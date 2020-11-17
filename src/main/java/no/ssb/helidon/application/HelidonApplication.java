package no.ssb.helidon.application;

import io.helidon.common.reactive.Single;

public interface HelidonApplication {

    <T> T put(Class<T> clazz, T instance);

    <T> T get(Class<T> clazz);

    Single<? extends HelidonApplication> start();

    Single<? extends HelidonApplication> stop();
}
