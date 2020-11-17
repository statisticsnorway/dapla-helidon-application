package no.ssb.helidon.application;

import io.helidon.config.Config;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

public abstract class DefaultHelidonApplicationBuilder implements HelidonApplicationBuilder {

    public static Config createDefaultConfig() {
        String overrideFile = System.getenv("HELIDON_CONFIG_FILE");
        Config.Builder builder = Config.builder();
        if (overrideFile != null) {
            builder.addSource(file(overrideFile).optional());
        }
        return builder
                .addSource(file("conf/application.yaml").optional())
                .addSource(classpath("application.yaml"))
                .build();
    }

    protected Config config;

    protected DefaultHelidonApplicationBuilder() {
    }

    @Override
    public <T> HelidonApplicationBuilder override(Class<T> clazz, T instance) {
        if (Config.class.isAssignableFrom(clazz)) {
            config = (Config) instance;
        }
        return this;
    }
}
