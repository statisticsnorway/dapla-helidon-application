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
        span.log(Map.of("event", "debug-input", "data", message.toString()));
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

    public static <T extends MessageOrBuilder> Span spanFromGrpc(T message, String operationName) {
        SpanContext spanContext = Contexts.context()
                .get()
                .get(SpanContext.class)
                .get();
        Tracer tracer = GlobalTracer.get();
        Span span = tracer
                .buildSpan(operationName)
                .asChildOf(spanContext)
                .start();
        tracer.scopeManager().activate(span);
        traceInputMessage(span, message);
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
