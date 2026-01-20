package com.yarvis.assistant.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatHistoryManager {

    private static final String TAG = "ChatHistoryManager";
    private static final String PREFS_NAME = "yarvis_chat_history";
    private static final String KEY_MESSAGES = "messages";
    private static final String KEY_SESSIONS = "sessions";
    private static final int MAX_MESSAGES = 500;

    private static ChatHistoryManager instance;
    private final SharedPreferences prefs;
    private final List<ChatMessageModel> messages;
    private final Map<String, SessionInfo> sessions;
    private final List<ChatHistoryListener> listeners;

    public interface ChatHistoryListener {
        void onMessageAdded(ChatMessageModel message);
        void onMessageUpdated(ChatMessageModel message);
        void onHistoryCleared();
    }

    public static class SessionInfo {
        public final String id;
        public final long startedAt;
        public long lastActivityAt;
        public String title;
        public int messageCount;

        public SessionInfo(String id, long startedAt) {
            this.id = id;
            this.startedAt = startedAt;
            this.lastActivityAt = startedAt;
            this.messageCount = 0;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("startedAt", startedAt);
            json.put("lastActivityAt", lastActivityAt);
            json.put("title", title);
            json.put("messageCount", messageCount);
            return json;
        }

        public static SessionInfo fromJson(JSONObject json) throws JSONException {
            SessionInfo info = new SessionInfo(
                    json.getString("id"),
                    json.getLong("startedAt")
            );
            info.lastActivityAt = json.optLong("lastActivityAt", info.startedAt);
            info.title = json.optString("title", null);
            info.messageCount = json.optInt("messageCount", 0);
            return info;
        }
    }

    private ChatHistoryManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.messages = new ArrayList<>();
        this.sessions = new HashMap<>();
        this.listeners = new ArrayList<>();
        loadFromStorage();
    }

    public static synchronized ChatHistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new ChatHistoryManager(context);
        }
        return instance;
    }

    public void addListener(ChatHistoryListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(ChatHistoryListener listener) {
        listeners.remove(listener);
    }

    public void addMessage(ChatMessageModel message) {
        messages.add(message);

        if (message.getSessionId() != null) {
            SessionInfo session = sessions.get(message.getSessionId());
            if (session == null) {
                session = new SessionInfo(message.getSessionId(), message.getTimestamp());
                sessions.put(message.getSessionId(), session);
            }
            session.lastActivityAt = message.getTimestamp();
            session.messageCount++;

            if (session.title == null && message.isFromUser()) {
                String title = message.getText();
                if (title.length() > 50) {
                    title = title.substring(0, 47) + "...";
                }
                session.title = title;
            }
        }

        while (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }

        saveToStorage();
        notifyMessageAdded(message);
    }

    public void updateMessageStatus(String messageId, ChatMessageModel.MessageStatus status) {
        for (ChatMessageModel msg : messages) {
            if (msg.getId().equals(messageId)) {
                msg.setStatus(status);
                saveToStorage();
                notifyMessageUpdated(msg);
                break;
            }
        }
    }

    public List<ChatMessageModel> getAllMessages() {
        return new ArrayList<>(messages);
    }

    public List<ChatMessageModel> getMessagesForSession(String sessionId) {
        List<ChatMessageModel> result = new ArrayList<>();
        for (ChatMessageModel msg : messages) {
            if (sessionId.equals(msg.getSessionId())) {
                result.add(msg);
            }
        }
        return result;
    }

    public List<ChatMessageModel> getRecentMessages(int count) {
        int start = Math.max(0, messages.size() - count);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }

    public List<SessionInfo> getAllSessions() {
        List<SessionInfo> result = new ArrayList<>(sessions.values());
        Collections.sort(result, (a, b) -> Long.compare(b.lastActivityAt, a.lastActivityAt));
        return result;
    }

    public SessionInfo getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void clearHistory() {
        messages.clear();
        sessions.clear();
        saveToStorage();
        notifyHistoryCleared();
    }

    public void clearSession(String sessionId) {
        messages.removeIf(msg -> sessionId.equals(msg.getSessionId()));
        sessions.remove(sessionId);
        saveToStorage();
    }

    private void loadFromStorage() {
        try {
            String messagesJson = prefs.getString(KEY_MESSAGES, "[]");
            JSONArray messagesArray = new JSONArray(messagesJson);
            for (int i = 0; i < messagesArray.length(); i++) {
                try {
                    messages.add(ChatMessageModel.fromJson(messagesArray.getJSONObject(i)));
                } catch (JSONException e) {
                    Log.w(TAG, "Error loading message: " + e.getMessage());
                }
            }

            String sessionsJson = prefs.getString(KEY_SESSIONS, "{}");
            JSONObject sessionsObject = new JSONObject(sessionsJson);
            for (java.util.Iterator<String> it = sessionsObject.keys(); it.hasNext(); ) {
                String key = it.next();
                try {
                    sessions.put(key, SessionInfo.fromJson(sessionsObject.getJSONObject(key)));
                } catch (JSONException e) {
                    Log.w(TAG, "Error loading session: " + e.getMessage());
                }
            }

            Log.d(TAG, "Loaded " + messages.size() + " messages and " + sessions.size() + " sessions");
        } catch (JSONException e) {
            Log.e(TAG, "Error loading history: " + e.getMessage());
        }
    }

    private void saveToStorage() {
        try {
            JSONArray messagesArray = new JSONArray();
            for (ChatMessageModel msg : messages) {
                messagesArray.put(msg.toJson());
            }

            JSONObject sessionsObject = new JSONObject();
            for (Map.Entry<String, SessionInfo> entry : sessions.entrySet()) {
                sessionsObject.put(entry.getKey(), entry.getValue().toJson());
            }

            prefs.edit()
                    .putString(KEY_MESSAGES, messagesArray.toString())
                    .putString(KEY_SESSIONS, sessionsObject.toString())
                    .apply();

        } catch (JSONException e) {
            Log.e(TAG, "Error saving history: " + e.getMessage());
        }
    }

    private void notifyMessageAdded(ChatMessageModel message) {
        for (ChatHistoryListener listener : listeners) {
            listener.onMessageAdded(message);
        }
    }

    private void notifyMessageUpdated(ChatMessageModel message) {
        for (ChatHistoryListener listener : listeners) {
            listener.onMessageUpdated(message);
        }
    }

    private void notifyHistoryCleared() {
        for (ChatHistoryListener listener : listeners) {
            listener.onHistoryCleared();
        }
    }
}
