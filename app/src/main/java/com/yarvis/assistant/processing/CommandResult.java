package com.yarvis.assistant.processing;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Clase inmutable que simula un Record de Java 16+.
 * En Java 16+ esto sería: record CommandResult(boolean success, String message, ...)
 *
 * Demuestra: RECORDS (patrón para Java < 16) e INMUTABILIDAD
 *
 * Características de un record:
 * - Todos los campos son final
 * - Constructor canónico
 * - Getters automáticos (aquí implementados manualmente)
 * - equals(), hashCode(), toString() implementados
 */
public final class CommandResult implements Identifiable {

    private final String id;
    private final boolean success;
    private final String message;
    private final String commandId;
    private final long executionTimeMs;
    private final Map<String, Object> metadata;

    /**
     * Constructor canónico (como en records).
     */
    public CommandResult(String id, boolean success, String message, String commandId,
                         long executionTimeMs, Map<String, Object> metadata) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.success = success;
        this.message = message != null ? message : "";
        this.commandId = commandId;
        this.executionTimeMs = executionTimeMs;
        this.metadata = metadata != null
                ? Collections.unmodifiableMap(metadata)
                : Collections.emptyMap();
    }

    // =========================================================================
    // ACCESSORS (equivalentes a los generados automáticamente en records)
    // =========================================================================

    @Override
    public String getId() { return id; }

    public boolean success() { return success; }

    public String message() { return message; }

    public String commandId() { return commandId; }

    public long executionTimeMs() { return executionTimeMs; }

    public Map<String, Object> metadata() { return metadata; }

    // =========================================================================
    // FACTORY METHODS (patrón común con records)
    // =========================================================================

    public static CommandResult success(String commandId, String message) {
        return new CommandResult(
                generateId(),
                true,
                message,
                commandId,
                0,
                null
        );
    }

    public static CommandResult success(String commandId, String message, long executionTimeMs) {
        return new CommandResult(
                generateId(),
                true,
                message,
                commandId,
                executionTimeMs,
                null
        );
    }

    public static CommandResult failure(String commandId, String errorMessage) {
        return new CommandResult(
                generateId(),
                false,
                errorMessage,
                commandId,
                0,
                null
        );
    }

    public static CommandResult failure(String commandId, String errorMessage, Throwable cause) {
        return new CommandResult(
                generateId(),
                false,
                errorMessage + (cause != null ? ": " + cause.getMessage() : ""),
                commandId,
                0,
                Map.of("errorType", cause != null ? cause.getClass().getSimpleName() : "Unknown")
        );
    }

    private static String generateId() {
        return "result_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    // =========================================================================
    // EQUALS, HASHCODE, TOSTRING (como genera un record)
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandResult)) return false;
        CommandResult that = (CommandResult) o;
        return success == that.success &&
                executionTimeMs == that.executionTimeMs &&
                Objects.equals(id, that.id) &&
                Objects.equals(message, that.message) &&
                Objects.equals(commandId, that.commandId) &&
                Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, success, message, commandId, executionTimeMs, metadata);
    }

    @Override
    public String toString() {
        return "CommandResult[" +
                "id=" + id +
                ", success=" + success +
                ", message=" + message +
                ", commandId=" + commandId +
                ", executionTimeMs=" + executionTimeMs +
                ", metadata=" + metadata +
                ']';
    }

    // =========================================================================
    // COPY METHOD (with pattern - común en records con Kotlin o Scala)
    // =========================================================================

    /**
     * Crea una copia con un mensaje diferente.
     * Simula el método copy() de data classes.
     */
    public CommandResult withMessage(String newMessage) {
        return new CommandResult(this.id, this.success, newMessage,
                this.commandId, this.executionTimeMs, this.metadata);
    }

    /**
     * Crea una copia con metadata adicional.
     */
    public CommandResult withMetadata(Map<String, Object> newMetadata) {
        return new CommandResult(this.id, this.success, this.message,
                this.commandId, this.executionTimeMs, newMetadata);
    }
}
