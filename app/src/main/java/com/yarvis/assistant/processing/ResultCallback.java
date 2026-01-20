package com.yarvis.assistant.processing;

@FunctionalInterface
public interface ResultCallback<T> {

    void onResult(T result);

    default ResultCallback<T> andThen(ResultCallback<? super T> after) {
        return (T result) -> {
            this.onResult(result);
            after.onResult(result);
        };
    }

    static <T> ResultCallback<T> noOp() {
        return result -> { };
    }

    static <T> ResultCallback<T> logging(String tag) {
        return result -> System.out.println("[" + tag + "] Result: " + result);
    }
}
