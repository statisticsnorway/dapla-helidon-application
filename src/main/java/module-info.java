module no.ssb.helidon.application {
    requires java.logging;
    requires jul.to.slf4j;
    requires org.slf4j;
    requires logback.classic;
    requires io.helidon.webserver;
    requires io.helidon.grpc.server;
    requires no.ssb.helidon.media.protobuf.json.server;

    exports no.ssb.helidon.application;
}