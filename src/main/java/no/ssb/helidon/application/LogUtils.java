package no.ssb.helidon.application;

import io.helidon.webserver.ServerRequest;
import no.ssb.helidon.media.protobuf.ProtobufJsonUtils;
import org.slf4j.Logger;

public class LogUtils {

    public static void trace(Logger log, String routingTarget, ServerRequest request) {
        if (log.isTraceEnabled()) {
            log.trace("{} {} {}", routingTarget, request.method().name(), request.uri());
        }
    }

    public static void trace(Logger log, String routingTarget, ServerRequest request, Object body) {
        if (log.isTraceEnabled()) {
            log.trace("{} {} {}\n{}", routingTarget, request.method().name(), request.uri(), ProtobufJsonUtils.toString(body));
        }
    }
}
