package no.ssb.helidon.application;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.helidon.webserver.RequestHeaders;

import java.util.concurrent.Executor;

public class GrpcAuthorizationBearerCallCredentials extends CallCredentials {

    private String token;

    public GrpcAuthorizationBearerCallCredentials(String token) {
        this.token = token;
    }

    public static GrpcAuthorizationBearerCallCredentials from(RequestHeaders headers) {
        String token = headers.first("Authorization").map(s -> {
            if (s.isBlank() || !s.startsWith("Bearer ")) {
                return "";
            }
            return s.substring("Bearer ".length());
        }).orElse("no-token");
        return new GrpcAuthorizationBearerCallCredentials(token);
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), String.format("Bearer %s", token));
        appExecutor.execute(() -> applier.apply(metadata));
    }

    @Override
    public void thisUsesUnstableApi() {
    }
}
