package com.yarvis.assistant.network;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WebSocketMessage {

    public static class BaseMessage {
        public final String type;

        public BaseMessage(String type) {
            this.type = type;
        }

        public String toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("type", type);
                return json.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }
    }

    public enum ShowContentType {
        TEXT, IMAGE, IMAGE_TEXT, LINK, LINKS, VIDEO, CARD, LIST
    }

    public static class ShowLink {
        public final String title;
        public final String url;
        public final String description;
        public final String thumbnailUrl;

        public ShowLink(String title, String url, String description, String thumbnailUrl) {
            this.title = title;
            this.url = url;
            this.description = description;
            this.thumbnailUrl = thumbnailUrl;
        }

        public static ShowLink fromJson(JSONObject json) {
            return new ShowLink(
                    json.optString("title", ""),
                    json.optString("url", ""),
                    json.optString("description", null),
                    json.optString("thumbnailUrl", null)
            );
        }
    }

    public static class ShowListItem {
        public final String title;
        public final String subtitle;
        public final String imageUrl;

        public ShowListItem(String title, String subtitle, String imageUrl) {
            this.title = title;
            this.subtitle = subtitle;
            this.imageUrl = imageUrl;
        }

        public static ShowListItem fromJson(JSONObject json) {
            return new ShowListItem(
                    json.optString("title", ""),
                    json.optString("subtitle", null),
                    json.optString("imageUrl", null)
            );
        }
    }

    public static class ShowContent {
        public final ShowContentType type;
        public final String title;
        public final String text;
        public final String imageUrl;
        public final String videoUrl;
        public final String thumbnailUrl;
        public final List<ShowLink> links;
        public final List<ShowListItem> items;

        public ShowContent(ShowContentType type, String title, String text,
                           String imageUrl, String videoUrl, String thumbnailUrl,
                           List<ShowLink> links, List<ShowListItem> items) {
            this.type = type;
            this.title = title;
            this.text = text;
            this.imageUrl = imageUrl;
            this.videoUrl = videoUrl;
            this.thumbnailUrl = thumbnailUrl;
            this.links = links;
            this.items = items;
        }

        public static ShowContent fromJson(JSONObject json) {
            if (json == null) return null;

            ShowContentType type = parseContentType(json.optString("type", "text"));
            String title = json.optString("title", null);
            String text = json.optString("text", null);
            String imageUrl = json.optString("imageUrl", null);
            String videoUrl = json.optString("videoUrl", null);
            String thumbnailUrl = json.optString("thumbnailUrl", null);

            List<ShowLink> links = new ArrayList<>();
            JSONArray linksArray = json.optJSONArray("links");
            if (linksArray != null) {
                for (int i = 0; i < linksArray.length(); i++) {
                    JSONObject linkJson = linksArray.optJSONObject(i);
                    if (linkJson != null) {
                        links.add(ShowLink.fromJson(linkJson));
                    }
                }
            }

            List<ShowListItem> items = new ArrayList<>();
            JSONArray itemsArray = json.optJSONArray("items");
            if (itemsArray != null) {
                for (int i = 0; i < itemsArray.length(); i++) {
                    JSONObject itemJson = itemsArray.optJSONObject(i);
                    if (itemJson != null) {
                        items.add(ShowListItem.fromJson(itemJson));
                    }
                }
            }

            return new ShowContent(type, title, text, imageUrl, videoUrl, thumbnailUrl, links, items);
        }

        private static ShowContentType parseContentType(String type) {
            switch (type.toLowerCase()) {
                case "image": return ShowContentType.IMAGE;
                case "image_text": return ShowContentType.IMAGE_TEXT;
                case "link": return ShowContentType.LINK;
                case "links": return ShowContentType.LINKS;
                case "video": return ShowContentType.VIDEO;
                case "card": return ShowContentType.CARD;
                case "list": return ShowContentType.LIST;
                default: return ShowContentType.TEXT;
            }
        }
    }

    public static class VoiceCommand extends BaseMessage {
        public final String text;
        public final long timestamp;
        public final String sessionId;

        public VoiceCommand(String text, String sessionId) {
            super("voice_command");
            this.text = text;
            this.timestamp = System.currentTimeMillis();
            this.sessionId = sessionId;
        }

        public VoiceCommand(String text) {
            this(text, null);
        }

        @Override
        public String toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("type", type);
                json.put("text", text);
                json.put("timestamp", timestamp);
                if (sessionId != null) {
                    json.put("sessionId", sessionId);
                }
                return json.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }
    }

    public static class ChatMessage extends BaseMessage {
        public final String text;
        public final long timestamp;
        public final String sessionId;

        public ChatMessage(String text, String sessionId) {
            super("chat_message");
            this.text = text;
            this.timestamp = System.currentTimeMillis();
            this.sessionId = sessionId;
        }

        public ChatMessage(String text) {
            this(text, null);
        }

        @Override
        public String toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("type", type);
                json.put("text", text);
                json.put("timestamp", timestamp);
                if (sessionId != null) {
                    json.put("sessionId", sessionId);
                }
                return json.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }
    }

    public static class NotificationMessage extends BaseMessage {
        public final String app;
        public final String title;
        public final String text;

        public NotificationMessage(String app, String title, String text) {
            super("notification");
            this.app = app;
            this.title = title;
            this.text = text;
        }

        @Override
        public String toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("type", type);
                json.put("app", app);
                json.put("title", title);
                json.put("text", text);
                return json.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }
    }

    public static class EndConversation extends BaseMessage {
        public final String sessionId;
        public final String reason;

        public EndConversation(String sessionId, String reason) {
            super("end_conversation");
            this.sessionId = sessionId;
            this.reason = reason;
        }

        @Override
        public String toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("type", type);
                json.put("sessionId", sessionId);
                if (reason != null) {
                    json.put("reason", reason);
                }
                return json.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }
    }

    public static class Ping extends BaseMessage {
        public Ping() {
            super("ping");
        }
    }

    public static class Response {
        public final String text;
        public final boolean speak;
        public final String sessionId;
        public final String messageId;
        public final ShowContent show;

        public Response(String text, boolean speak, String sessionId, String messageId, ShowContent show) {
            this.text = text;
            this.speak = speak;
            this.sessionId = sessionId;
            this.messageId = messageId;
            this.show = show;
        }
    }

    public static class StartConversation {
        public final String sessionId;
        public final String greeting;
        public final JSONObject context;
        public final ShowContent show;

        public StartConversation(String sessionId, String greeting, JSONObject context, ShowContent show) {
            this.sessionId = sessionId;
            this.greeting = greeting;
            this.context = context;
            this.show = show;
        }
    }

    public static class EndConversationResponse {
        public final String sessionId;
        public final String farewell;
        public final String reason;

        public EndConversationResponse(String sessionId, String farewell, String reason) {
            this.sessionId = sessionId;
            this.farewell = farewell;
            this.reason = reason;
        }
    }

    public static class Action {
        public final String action;
        public final JSONObject params;

        public Action(String action, JSONObject params) {
            this.action = action;
            this.params = params;
        }
    }

    public static class Error {
        public final String message;

        public Error(String message) {
            this.message = message;
        }
    }

    public static Object parseServerMessage(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            String type = json.optString("type", "");

            switch (type) {
                case "response":
                    return new Response(
                            json.optString("text", ""),
                            json.optBoolean("speak", false),
                            json.optString("sessionId", null),
                            json.optString("messageId", null),
                            ShowContent.fromJson(json.optJSONObject("show"))
                    );

                case "start_conversation":
                    return new StartConversation(
                            json.optString("sessionId", ""),
                            json.optString("greeting", ""),
                            json.optJSONObject("context"),
                            ShowContent.fromJson(json.optJSONObject("show"))
                    );

                case "end_conversation":
                    return new EndConversationResponse(
                            json.optString("sessionId", ""),
                            json.optString("farewell", ""),
                            json.optString("reason", "system")
                    );

                case "action":
                    return new Action(
                            json.optString("action", ""),
                            json.optJSONObject("params")
                    );

                case "error":
                    return new Error(json.optString("message", "Error desconocido"));

                case "pong":
                    return "pong";

                default:
                    return null;
            }
        } catch (JSONException e) {
            return new Error("Error parsing message: " + e.getMessage());
        }
    }
}
