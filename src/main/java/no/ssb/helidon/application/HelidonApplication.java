package no.ssb.helidon.application;

import java.util.concurrent.CompletionStage;

public interface HelidonApplication {

    <T> T put(Class<T> clazz, T instance);

    <T> T get(Class<T> clazz);

    CompletionStage<? extends HelidonApplication> start();

    CompletionStage<? extends HelidonApplication> stop();
}
