package no.ssb.helidon.application;

import io.grpc.LoadBalancerRegistry;
import io.grpc.NameResolverRegistry;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import io.grpc.services.internal.HealthCheckingRoundRobinLoadBalancerProvider;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigSource;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

public abstract class DefaultHelidonApplicationBuilder implements HelidonApplicationBuilder {

    public static void applyGrpcProvidersWorkaround() {
        // The shaded version of grpc from helidon does not include the service definition for
        // PickFirstLoadBalancerProvider. This result in LoadBalancerRegistry not being able to
        // find it. We register them manually here.
        LoadBalancerRegistry.getDefaultRegistry().register(new PickFirstLoadBalancerProvider());
        LoadBalancerRegistry.getDefaultRegistry().register(new HealthCheckingRoundRobinLoadBalancerProvider());

        // The same thing happens with the name resolvers.
        NameResolverRegistry.getDefaultRegistry().register(new DnsNameResolverProvider());
    }

    public static Config createDefaultConfig() {
        List<Supplier<ConfigSource>> configSourceSupplierList = new LinkedList<>();
        String overrideFile = System.getenv("HELIDON_CONFIG_FILE");
        if (overrideFile != null) {
            configSourceSupplierList.add(file(overrideFile).optional());
        }
        configSourceSupplierList.add(file("conf/application.yaml").optional());
        configSourceSupplierList.add(classpath("application.yaml"));
        return Config.builder().sources(configSourceSupplierList).build();
    }

    protected Config config;

    protected DefaultHelidonApplicationBuilder() {
        applyGrpcProvidersWorkaround();
    }


    @Override
    public <T> HelidonApplicationBuilder override(Class<T> clazz, T instance) {
        if (Config.class.isAssignableFrom(clazz)) {
            config = (Config) instance;
        }
        return this;
    }
}
