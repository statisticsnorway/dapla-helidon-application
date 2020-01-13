package no.ssb.helidon.application;

public interface HelidonApplicationBuilder {

    <T> HelidonApplicationBuilder override(Class<T> clazz, T instance);

    HelidonApplication build();
}
