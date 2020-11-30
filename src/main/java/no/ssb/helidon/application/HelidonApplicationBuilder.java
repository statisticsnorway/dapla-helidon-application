package no.ssb.helidon.application;

public interface HelidonApplicationBuilder {

    HelidonApplicationBuilder override(Class<?> clazz, Object instance);

    HelidonApplication build();
}
