package no.ssb.helidon.application;

import com.google.protobuf.MessageOrBuilder;
import io.helidon.common.context.Contexts;
import io.helidon.webserver.ServerRequest;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

public class Tracing {

    public static <T extends MessageOrBuilder> T traceInputMessage(Span span, T message) {
        traceInputMessage(span, message.toString());
        return message;
    }

    public static <T extends MessageOrBuilder> T traceOutputMessage(Span span, T message) {
        traceOutputMessage(span, message.toString());
        return message;
    }

    public static void traceInputMessage(Span span, String message) {
        span.log(Map.of("event", "debug-input", "data", message));
    }

    public static void traceOutputMessage(Span span, String message) {
        span.log(Map.of("event", "debug-output", "data", message));
    }

    public static void logError(Span span, Throwable e) {
        logError(span, e, "error");
    }

    public static void logError(Span span, Throwable e, String event) {
        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        span.log(Map.of("event", event, "message", e.getMessage(), "stacktrace", stringWriter.toString()));
    }

    public static Tracer tracer() {
        Tracer tracer = GlobalTracer.get();
        if (tracer != null) {
            return tracer;
        }
        tracer = Contexts.context().get().get(Tracer.class).get();
        if (tracer == null) {
            throw new IllegalStateException("A Tracer has not been assigned to the Helidon Contexts context");
        }
        return tracer;
    }

    public static <T extends MessageOrBuilder> Span spanFromGrpc(T message, String operationName) {
        Tracer tracer = tracer();
        if (tracer.scopeManager().activeSpan() != null) {
            Span span = tracer
                    .buildSpan(operationName)
                    .asChildOf(tracer.scopeManager().activeSpan())
                    .start();
            tracer.scopeManager().activate(span);
            return span;
        }
        SpanContext spanContext = Contexts.context()
                .get()
                .get(SpanContext.class)
                .get();
        Span span = tracer
                .buildSpan(operationName)
                .asChildOf(spanContext)
                .start();
        tracer.scopeManager().activate(span);
        return span;
    }

    public static Span spanFromHttp(ServerRequest request, String operationName) {
        SpanContext spanContext = request.spanContext();
        Tracer tracer = request.tracer();
        Span span = tracer
                .buildSpan(operationName)
                .asChildOf(spanContext)
                .start();
        tracer.scopeManager().activate(span);
        return span;
    }
}
