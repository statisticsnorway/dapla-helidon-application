package no.ssb.helidon.application;

import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.stub.StreamObserver;
import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.opentracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class HelidonGrpcWebTranscoding implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(HelidonGrpcWebTranscoding.class);

    final BindableService[] grpcServices;

    public HelidonGrpcWebTranscoding(BindableService... grpcServices) {
        this.grpcServices = grpcServices;
    }

    @Override
    public void update(Routing.Rules rules) {
        for (BindableService grpcService : grpcServices) {
            grpcService.bindService().getMethods().stream().forEachOrdered(smd -> {
                MethodDescriptor<?, ?> grpcMethodDescriptor = smd.getMethodDescriptor();
                ProtoMethodDescriptorSupplier pmds = (ProtoMethodDescriptorSupplier) grpcMethodDescriptor.getSchemaDescriptor();
                Descriptors.MethodDescriptor protobufMethodDescriptor = pmds.getMethodDescriptor();
                if (MethodType.UNARY.equals(grpcMethodDescriptor.getType())) {
                    Descriptors.Descriptor inputType = protobufMethodDescriptor.getInputType();
                    String inputClassFullJavaName = inputType.getFile().getOptions().getJavaPackage() + "." + inputType.getName();
                    Class<?> inputClazz;
                    try {
                        inputClazz = Class.forName(inputClassFullJavaName);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    rules.post(Handler.create(inputClazz, ((req, res, entity) -> {
                        try {
                            Class<? extends BindableService> aClass = grpcService.getClass();
                            String methodName = protobufMethodDescriptor.getName();
                            Method[] declaredMethods = aClass.getDeclaredMethods();
                            Method method = null;
                            for (Method declaredMethod : declaredMethods) {
                                if (!Modifier.isStatic(declaredMethod.getModifiers()) && Modifier.isPublic(declaredMethod.getModifiers())) {
                                    // TODO add check that method returns void
                                    method = declaredMethod;
                                }
                            }
                            Span span = Tracing.spanFromHttp(req, "transcoding-" + methodName);
                            method.invoke(grpcService, entity, new StreamObserver<>() {
                                final Deque<Object> result = new ConcurrentLinkedDeque<>();

                                @Override
                                public void onNext(Object value) {
                                    result.add(value);
                                }

                                @Override
                                public void onError(Throwable t) {
                                    try {
                                        res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(t.getMessage());
                                        Tracing.logError(span, t);
                                    } finally {
                                        span.finish();
                                    }
                                }

                                @Override
                                public void onCompleted() {
                                    try {
                                        if (result.isEmpty()) {
                                            res.send(null);
                                        } else {
                                            Object first = result.getFirst();
                                            res.send(first);
                                        }
                                    } finally {
                                        span.finish();
                                    }
                                }
                            });
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    })));
                    LOG.debug("Mapped http-to-grpc transcoding method: {}", grpcMethodDescriptor.getFullMethodName());
                }
            });
        }
    }
}
