package no.ssb.helidon.application;

import ch.qos.logback.classic.util.ContextInitializer;
import io.grpc.ManagedChannel;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.webserver.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;

import static java.util.Optional.ofNullable;

public abstract class DefaultHelidonApplication implements HelidonApplication {

    private static final Logger LOG;

    static {
        String logbackConfigurationFile = System.getenv("LOGBACK_CONFIGURATION_FILE");
        if (logbackConfigurationFile != null) {
            System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, logbackConfigurationFile);
        }
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        LOG = LoggerFactory.getLogger(DefaultHelidonApplication.class);
    }

    public static void installSlf4jJulBridge() {
        // placeholder used to trigger static initializer only
    }

    public static void shutdownAndAwaitTermination(ManagedChannel managedChannel) {
        managedChannel.shutdown();
        try {
            if (!managedChannel.awaitTermination(5, TimeUnit.SECONDS)) {
                managedChannel.shutdownNow(); // Cancel currently executing tasks
                if (!managedChannel.awaitTermination(5, TimeUnit.SECONDS))
                    LOG.error("ManagedChannel did not terminate");
            }
        } catch (InterruptedException ie) {
            managedChannel.shutdownNow(); // (Re-)Cancel if current thread also interrupted
            Thread.currentThread().interrupt();
        }
    }

    private final Map<Class<?>, Object> instanceByType = new ConcurrentHashMap<>();

    @Override
    public <T> T put(Class<T> clazz, T instance) {
        return (T) instanceByType.put(clazz, instance);
    }

    @Override
    public <T> T get(Class<T> clazz) {
        return (T) instanceByType.get(clazz);
    }

    public CompletionStage<HelidonApplication> start() {
        return ofNullable(get(GrpcServer.class)).map(GrpcServer::start).orElse(CompletableFuture.completedFuture(null))
                .thenCombine(ofNullable(get(WebServer.class)).map(WebServer::start).orElse(CompletableFuture.completedFuture(null)), (grpcServer, webServer) -> this);
    }

    public CompletionStage<HelidonApplication> stop() {
        return ofNullable(get(WebServer.class)).map(WebServer::shutdown).orElse(CompletableFuture.completedFuture(null))
                .thenCombine(ofNullable(get(GrpcServer.class)).map(GrpcServer::shutdown).orElse(CompletableFuture.completedFuture(null)), ((webServer, grpcServer) -> this))
                ;
    }
}
