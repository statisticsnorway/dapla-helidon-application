package no.ssb.helidon.application;

import io.helidon.config.Config;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;
import static java.util.Optional.ofNullable;

public abstract class DefaultHelidonApplicationBuilder implements HelidonApplicationBuilder {

    public static Config createDefaultConfig() {
        Config.Builder builder = Config.builder();
        String overrideFile = ofNullable(System.getProperty("helidon.config.file"))
                .orElseGet(() -> System.getenv("HELIDON_CONFIG_FILE"));
        if (overrideFile != null) {
            builder.addSource(file(overrideFile).optional());
        }
        String profile = ofNullable(System.getProperty("helidon.config.profile"))
                .orElseGet(() -> System.getenv("HELIDON_CONFIG_PROFILE"));
        if (profile != null) {
            String profileFilename = String.format("application-%s.yaml", profile);
            builder.addSource(file(profileFilename).optional());
            builder.addSource(classpath(profileFilename).optional());
        }
        builder.addSource(file("conf/application.yaml").optional());
        builder.addSource(classpath("application.yaml"));
        return builder.build();
    }

    protected Config config;

    protected DefaultHelidonApplicationBuilder() {
        config = createDefaultConfig();
    }

    @Override
    public HelidonApplicationBuilder override(Class<?> clazz, Object instance) {
        if (Config.class.isAssignableFrom(clazz)) {
            config = (Config) instance;
        }
        return this;
    }
}
