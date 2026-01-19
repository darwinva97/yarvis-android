package com.yarvis.assistant.chat;

import com.yarvis.assistant.network.WebSocketMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Modelo de mensaje para el historial de conversaciones.
 */
public class ChatMessageModel {

    public enum MessageType {
        USER_VOICE,     // Mensaje del usuario por voz
        USER_TEXT,      // Mensaje del usuario por texto
        ASSISTANT,      // Respuesta del asistente
        SYSTEM,         // Mensaje del sistema (inicio/fin conversación, errores)
        ACTION          // Acción ejecutada
    }

    public enum MessageStatus {
        SENDING,        // Enviando al servidor
        SENT,           // Enviado exitosamente
        ERROR           // Error al enviar
    }

    private final String id;
    private final String sessionId;
    private final MessageType type;
    private final String text;                      // Texto completo
    private final long timestamp;
    private MessageStatus status;
    private final WebSocketMessage.ShowContent showContent;  // Contenido enriquecido (para vista previa)

    private ChatMessageModel(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.sessionId = builder.sessionId;
        this.type = builder.type;
        this.text = builder.text;
        this.timestamp = builder.timestamp > 0 ? builder.timestamp : System.currentTimeMillis();
        this.status = builder.status;
        this.showContent = builder.showContent;
    }

    // Getters
    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public MessageType getType() { return type; }
    public String getText() { return text; }
    public long getTimestamp() { return timestamp; }
    public MessageStatus getStatus() { return status; }
    public WebSocketMessage.ShowContent getShowContent() { return showContent; }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public boolean isFromUser() {
        return type == MessageType.USER_VOICE || type == MessageType.USER_TEXT;
    }

    public boolean isFromAssistant() {
        return type == MessageType.ASSISTANT;
    }

    /**
     * Obtiene el texto de preview para mostrar en la UI.
     * Si hay ShowContent, usa ese. Si no, usa el texto completo truncado.
     */
    public String getPreviewText() {
        if (showContent != null && showContent.text != null) {
            return showContent.text;
        }
        if (showContent != null && showContent.title != null) {
            return showContent.title;
        }
        if (text != null && text.length() > 150) {
            return text.substring(0, 147) + "...";
        }
        return text;
    }

    /**
     * Verifica si el mensaje tiene contenido multimedia.
     */
    public boolean hasMedia() {
        if (showContent == null) return false;
        return showContent.imageUrl != null ||
               showContent.videoUrl != null ||
               (showContent.links != null && !showContent.links.isEmpty());
    }

    // ==================== Serialización JSON ====================

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("sessionId", sessionId);
        json.put("type", type.name());
        json.put("text", text);
        json.put("timestamp", timestamp);
        json.put("status", status.name());

        if (showContent != null) {
            json.put("showContent", showContentToJson(showContent));
        }

        return json;
    }

    public static ChatMessageModel fromJson(JSONObject json) throws JSONException {
        Builder builder = new Builder()
                .id(json.getString("id"))
                .sessionId(json.optString("sessionId", null))
                .type(MessageType.valueOf(json.getString("type")))
                .text(json.getString("text"))
                .timestamp(json.getLong("timestamp"))
                .status(MessageStatus.valueOf(json.optString("status", "SENT")));

        if (json.has("showContent")) {
            builder.showContent(showContentFromJson(json.getJSONObject("showContent")));
        }

        return builder.build();
    }

    private JSONObject showContentToJson(WebSocketMessage.ShowContent content) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", content.type.name());
        if (content.title != null) json.put("title", content.title);
        if (content.text != null) json.put("text", content.text);
        if (content.imageUrl != null) json.put("imageUrl", content.imageUrl);
        if (content.videoUrl != null) json.put("videoUrl", content.videoUrl);
        if (content.thumbnailUrl != null) json.put("thumbnailUrl", content.thumbnailUrl);

        if (content.links != null && !content.links.isEmpty()) {
            JSONArray linksArray = new JSONArray();
            for (WebSocketMessage.ShowLink link : content.links) {
                JSONObject linkJson = new JSONObject();
                linkJson.put("title", link.title);
                linkJson.put("url", link.url);
                if (link.description != null) linkJson.put("description", link.description);
                if (link.thumbnailUrl != null) linkJson.put("thumbnailUrl", link.thumbnailUrl);
                linksArray.put(linkJson);
            }
            json.put("links", linksArray);
        }

        if (content.items != null && !content.items.isEmpty()) {
            JSONArray itemsArray = new JSONArray();
            for (WebSocketMessage.ShowListItem item : content.items) {
                JSONObject itemJson = new JSONObject();
                itemJson.put("title", item.title);
                if (item.subtitle != null) itemJson.put("subtitle", item.subtitle);
                if (item.imageUrl != null) itemJson.put("imageUrl", item.imageUrl);
                itemsArray.put(itemJson);
            }
            json.put("items", itemsArray);
        }

        return json;
    }

    private static WebSocketMessage.ShowContent showContentFromJson(JSONObject json) {
        return WebSocketMessage.ShowContent.fromJson(json);
    }

    // ==================== Builder ====================

    public static class Builder {
        private String id;
        private String sessionId;
        private MessageType type;
        private String text;
        private long timestamp;
        private MessageStatus status = MessageStatus.SENT;
        private WebSocketMessage.ShowContent showContent;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder type(MessageType type) {
            this.type = type;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder status(MessageStatus status) {
            this.status = status;
            return this;
        }

        public Builder showContent(WebSocketMessage.ShowContent showContent) {
            this.showContent = showContent;
            return this;
        }

        public ChatMessageModel build() {
            if (type == null) {
                throw new IllegalStateException("MessageType is required");
            }
            if (text == null) {
                throw new IllegalStateException("Text is required");
            }
            return new ChatMessageModel(this);
        }
    }

    // ==================== Factory Methods ====================

    public static ChatMessageModel fromUserVoice(String text, String sessionId) {
        return new Builder()
                .type(MessageType.USER_VOICE)
                .text(text)
                .sessionId(sessionId)
                .status(MessageStatus.SENDING)
                .build();
    }

    public static ChatMessageModel fromUserText(String text, String sessionId) {
        return new Builder()
                .type(MessageType.USER_TEXT)
                .text(text)
                .sessionId(sessionId)
                .status(MessageStatus.SENDING)
                .build();
    }

    public static ChatMessageModel fromAssistantResponse(WebSocketMessage.Response response) {
        return new Builder()
                .id(response.messageId)
                .type(MessageType.ASSISTANT)
                .text(response.text)
                .sessionId(response.sessionId)
                .showContent(response.show)
                .build();
    }

    public static ChatMessageModel systemMessage(String text, String sessionId) {
        return new Builder()
                .type(MessageType.SYSTEM)
                .text(text)
                .sessionId(sessionId)
                .build();
    }
}
