package no.ssb.helidon.application;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.stub.AbstractStub;
import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.OperationNameConstructor;
import io.opentracing.contrib.grpc.TracingClientInterceptor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class HelidonGrpcWebTranscoding implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(HelidonGrpcWebTranscoding.class);

    static class StubCacheEntry {
        final AbstractStub<?> futureStub;
        final Method method;

        StubCacheEntry(AbstractStub<?> futureStub, Method method) {
            this.futureStub = futureStub;
            this.method = method;
        }
    }

    final Supplier<Channel> channelSupplier;
    final BindableService[] grpcServices;

    final AtomicReference<Channel> channelRef = new AtomicReference<>();
    final Map<String, StubCacheEntry> stubByPathPattern = new ConcurrentHashMap<>();

    public HelidonGrpcWebTranscoding(Supplier<Channel> channelSupplier, BindableService... grpcServices) {
        this.channelSupplier = channelSupplier;
        this.grpcServices = grpcServices;
    }

    @Override
    public void update(Routing.Rules rules) {
        for (BindableService grpcService : grpcServices) {
            grpcService.bindService().getMethods().stream().forEachOrdered(smd -> {
                MethodDescriptor<?, ?> grpcMethodDescriptor = smd.getMethodDescriptor();
                ProtoMethodDescriptorSupplier pmds = (ProtoMethodDescriptorSupplier) grpcMethodDescriptor.getSchemaDescriptor();
                Descriptors.MethodDescriptor protobufMethodDescriptor = pmds.getMethodDescriptor();
                String methodName = protobufMethodDescriptor.getName();
                if (MethodType.UNARY.equals(grpcMethodDescriptor.getType())) {
                    Descriptors.Descriptor inputType = protobufMethodDescriptor.getInputType();
                    String inputClassFullJavaName = inputType.getFile().getOptions().getJavaPackage() + "." + inputType.getName();
                    String clientClazzFullJavaName = inputType.getFile().getOptions().getJavaPackage() + "." + grpcMethodDescriptor.getServiceName() + "Grpc";
                    Class<?> inputClazz;
                    Class<?> clientClazz;
                    Method createFutureStubMethod;
                    try {
                        inputClazz = Class.forName(inputClassFullJavaName);
                        clientClazz = Class.forName(clientClazzFullJavaName);
                        createFutureStubMethod = clientClazz.getDeclaredMethod("newFutureStub", Channel.class);
                    } catch (NoSuchMethodException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    String pathPattern = String.format("/%s/%s", grpcMethodDescriptor.getServiceName(), methodName);
                    rules.post(pathPattern, Handler.create(inputClazz, ((req, res, entity) -> {
                        TracerAndSpan tracerAndSpan = Tracing.spanFromHttp(req, "transcoding-" + methodName);
                        Tracer tracer = tracerAndSpan.tracer();
                        Span span = tracerAndSpan.span();
                        try {
                            StubCacheEntry stubCacheEntry = stubByPathPattern.computeIfAbsent(pathPattern, k -> {
                                try {
                                    if (channelRef.get() == null) {
                                        TracingClientInterceptor tracingInterceptor = TracingClientInterceptor.newBuilder()
                                                .withTracer(tracer)
                                                .withStreaming()
                                                .withVerbosity()
                                                .withOperationName(new OperationNameConstructor() {
                                                    @Override
                                                    public <ReqT, RespT> String constructOperationName(MethodDescriptor<ReqT, RespT> method) {
                                                        return "Transcoding to local Grpc " + method.getFullMethodName();
                                                    }
                                                })
                                                .withActiveSpanSource(() -> tracer.scopeManager().activeSpan())
                                                .withTracedAttributes(TracingClientInterceptor.ClientRequestAttribute.ALL_CALL_OPTIONS, TracingClientInterceptor.ClientRequestAttribute.HEADERS)
                                                .build();
                                        channelRef.compareAndSet(null, tracingInterceptor.intercept(channelSupplier.get()));
                                    }
                                    AbstractStub<?> stub = ((AbstractStub<?>) createFutureStubMethod.invoke(null, channelRef.get()))
                                            .withDeadlineAfter(1, TimeUnit.SECONDS);
                                    Class<? extends AbstractStub> clientFutureStubClazz = stub.getClass();
                                    Method method = clientFutureStubClazz.getDeclaredMethod(methodName, inputClazz);
                                    return new StubCacheEntry(stub, method);
                                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            GrpcAuthorizationBearerCallCredentials authorizationBearer = GrpcAuthorizationBearerCallCredentials.from(req.headers());
                            ListenableFuture<?> listenableFuture = (ListenableFuture<?>) stubCacheEntry.method.invoke(stubCacheEntry.futureStub.withCallCredentials(authorizationBearer), entity);
                            Futures.addCallback(
                                    listenableFuture,
                                    new FutureCallback() {
                                        @Override
                                        public void onSuccess(@Nullable Object result) {
                                            Tracing.restoreTracingContext(tracerAndSpan);
                                            try {
                                                res.send(result);
                                            } finally {
                                                span.finish();
                                            }
                                        }

                                        @Override
                                        public void onFailure(Throwable t) {
                                            try {
                                                Tracing.restoreTracingContext(tracerAndSpan);
                                                LOG.error("while serving path " + pathPattern, t);
                                                res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(t.getMessage());
                                                Tracing.logError(span, t);
                                            } finally {
                                                span.finish();
                                            }
                                        }
                                    },
                                    MoreExecutors.directExecutor()
                            );
                        } catch (Throwable t) {
                            try {
                                LOG.error("while serving path " + pathPattern, t);
                                res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(t.getMessage());
                                Tracing.logError(span, t);
                            } finally {
                                span.finish();
                            }
                        }
                    })));
                    LOG.info("Mapped http-to-grpc transcoding method: {}", grpcMethodDescriptor.getFullMethodName());
                } else {
                    LOG.warn("Transcoding http-to-grpc does not support grpc method-type {} for method {}", grpcMethodDescriptor.getType(), methodName);
                }
            });
        }
    }
}
