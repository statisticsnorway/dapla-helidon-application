package no.ssb.helidon.application;

import ch.qos.logback.classic.util.ContextInitializer;
import io.helidon.common.reactive.Single;
import io.helidon.webserver.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.LogManager;

import static java.util.Optional.ofNullable;

public abstract class DefaultHelidonApplication implements HelidonApplication {

    private static final Logger LOG;

    static {
        String logbackConfigurationFile = ofNullable(System.getProperty("logback.configuration.file"))
                .orElseGet(() -> System.getenv("LOGBACK_CONFIGURATION_FILE"));
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

    private final Map<Class<?>, Object> instanceByType = new ConcurrentHashMap<>();

    @Override
    public <T> T put(Class<T> clazz, T instance) {
        return (T) instanceByType.put(clazz, instance);
    }

    @Override
    public <T> T get(Class<T> clazz) {
        return (T) instanceByType.get(clazz);
    }

    public Single<DefaultHelidonApplication> start() {
        return ofNullable(get(WebServer.class))
                .map(webServer -> webServer.start().map(ws -> this))
                .orElse(Single.just(this));
    }

    public Single<DefaultHelidonApplication> stop() {
        return ofNullable(get(WebServer.class))
                .map(webServer -> webServer.shutdown().map(ws -> this))
                .orElse(Single.just(this));
    }
}
