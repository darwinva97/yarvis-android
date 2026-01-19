package com.yarvis.assistant.processing;

/**
 * Clase sellada (sealed) simulada para Java 11.
 * En Java 17+ esto sería: sealed class CommandType permits MediaCommand, SystemCommand, QueryCommand
 *
 * Demuestra: SEALED CLASSES (patrón para Java < 17)
 *
 * La clase base tiene constructor package-private para restringir herencia
 * solo a las clases permitidas dentro de este paquete.
 */
public abstract class CommandType implements Identifiable {

    private final String id;
    private final String category;
    private final long timestamp;

    // Constructor package-private: solo clases en este paquete pueden heredar
    CommandType(String id, String category) {
        this.id = id;
        this.category = category;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public final String getId() {
        return id;
    }

    public final String getCategory() {
        return category;
    }

    public final long getTimestamp() {
        return timestamp;
    }

    /**
     * Método abstracto que cada subclase debe implementar.
     * Demuestra: MÉTODOS ABSTRACTOS
     */
    public abstract String getDescription();

    /**
     * Visitor pattern para procesar comandos de forma type-safe.
     * Demuestra: POLIMORFISMO avanzado con visitor
     */
    public abstract <R> R accept(CommandVisitor<R> visitor);

    // =========================================================================
    // SUBCLASES PERMITIDAS (sealed pattern)
    // =========================================================================

    /**
     * Comando de medios (música, video, fotos).
     */
    public static final class MediaCommand extends CommandType {
        public enum Action { PLAY, PAUSE, STOP, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN }

        private final Action action;
        private final String target;

        public MediaCommand(String id, Action action, String target) {
            super(id, "MEDIA");
            this.action = action;
            this.target = target;
        }

        public Action getAction() { return action; }
        public String getTarget() { return target; }

        @Override
        public String getDescription() {
            return String.format("Media: %s on '%s'", action, target != null ? target : "current");
        }

        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visitMedia(this);
        }
    }

    /**
     * Comando del sistema (WiFi, Bluetooth, brillo, etc.).
     */
    public static final class SystemCommand extends CommandType {
        public enum Setting { WIFI, BLUETOOTH, BRIGHTNESS, VOLUME, AIRPLANE_MODE, FLASHLIGHT }
        public enum Operation { ENABLE, DISABLE, TOGGLE, SET }

        private final Setting setting;
        private final Operation operation;
        private final int value;

        public SystemCommand(String id, Setting setting, Operation operation) {
            this(id, setting, operation, -1);
        }

        public SystemCommand(String id, Setting setting, Operation operation, int value) {
            super(id, "SYSTEM");
            this.setting = setting;
            this.operation = operation;
            this.value = value;
        }

        public Setting getSetting() { return setting; }
        public Operation getOperation() { return operation; }
        public int getValue() { return value; }

        @Override
        public String getDescription() {
            return String.format("System: %s %s%s", operation, setting,
                    value >= 0 ? " to " + value : "");
        }

        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visitSystem(this);
        }
    }

    /**
     * Comando de consulta (clima, hora, información).
     */
    public static final class QueryCommand extends CommandType {
        public enum QueryType { WEATHER, TIME, DATE, CALENDAR, REMINDER, GENERAL }

        private final QueryType queryType;
        private final String query;

        public QueryCommand(String id, QueryType queryType, String query) {
            super(id, "QUERY");
            this.queryType = queryType;
            this.query = query;
        }

        public QueryType getQueryType() { return queryType; }
        public String getQuery() { return query; }

        @Override
        public String getDescription() {
            return String.format("Query [%s]: %s", queryType, query);
        }

        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visitQuery(this);
        }
    }

    /**
     * Comando de comunicación (llamadas, mensajes).
     */
    public static final class CommunicationCommand extends CommandType {
        public enum CommType { CALL, SMS, EMAIL, WHATSAPP }

        private final CommType commType;
        private final String recipient;
        private final String message;

        public CommunicationCommand(String id, CommType commType, String recipient, String message) {
            super(id, "COMMUNICATION");
            this.commType = commType;
            this.recipient = recipient;
            this.message = message;
        }

        public CommType getCommType() { return commType; }
        public String getRecipient() { return recipient; }
        public String getMessage() { return message; }

        @Override
        public String getDescription() {
            return String.format("Communication: %s to %s", commType, recipient);
        }

        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visitCommunication(this);
        }
    }

    // =========================================================================
    // VISITOR INTERFACE
    // =========================================================================

    /**
     * Visitor interface para procesamiento type-safe de comandos.
     * Demuestra: GENÉRICOS EN INTERFACES
     */
    public interface CommandVisitor<R> {
        R visitMedia(MediaCommand command);
        R visitSystem(SystemCommand command);
        R visitQuery(QueryCommand command);
        R visitCommunication(CommunicationCommand command);
    }
}
