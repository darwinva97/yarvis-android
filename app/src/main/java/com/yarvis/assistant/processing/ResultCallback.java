package com.yarvis.assistant.processing;

/**
 * Interfaz funcional para callbacks de resultados.
 *
 * Demuestra: INTERFAZ FUNCIONAL (como Consumer, Function, etc.)
 *
 * En Java 8+ las interfaces con un solo método abstracto pueden usarse
 * con expresiones lambda.
 *
 * @param <T> Tipo del resultado
 */
@FunctionalInterface
public interface ResultCallback<T> {

    /**
     * Llamado cuando el resultado está disponible.
     * @param result El resultado de la operación
     */
    void onResult(T result);

    /**
     * Método por defecto para encadenar callbacks.
     * Demuestra: MÉTODOS DEFAULT EN INTERFACES
     */
    default ResultCallback<T> andThen(ResultCallback<? super T> after) {
        return (T result) -> {
            this.onResult(result);
            after.onResult(result);
        };
    }

    /**
     * Crea un callback que no hace nada.
     * Demuestra: MÉTODOS ESTÁTICOS EN INTERFACES
     */
    static <T> ResultCallback<T> noOp() {
        return result -> { };
    }

    /**
     * Crea un callback que imprime el resultado.
     */
    static <T> ResultCallback<T> logging(String tag) {
        return result -> System.out.println("[" + tag + "] Result: " + result);
    }
}
