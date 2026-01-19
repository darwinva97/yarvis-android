package com.yarvis.assistant.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Cliente WebSocket para comunicación con el backend de Yarvis.
 * Maneja reconexión automática, sesiones de conversación y ping/pong.
 */
public class YarvisWebSocketClient {

    private static final String TAG = "YarvisWebSocket";
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int PING_INTERVAL_MS = 30000;

    private final String serverUrl;
    private final Handler mainHandler;
    private final ScheduledExecutorService scheduler;
    private WebSocketConnection connection;
    private ScheduledFuture<?> pingTask;
    private ScheduledFuture<?> reconnectTask;
    private boolean shouldBeConnected = false;
    private ConnectionListener listener;

    // Sesión de conversación activa
    private String activeSessionId = null;

    /**
     * Listener para eventos del WebSocket.
     */
    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onResponse(WebSocketMessage.Response response);
        void onAction(String action, String params);
        void onError(String message);
        void onConversationStarted(String sessionId, String greeting, WebSocketMessage.ShowContent show);
        void onConversationEnded(String sessionId, String farewell, String reason);
    }

    public YarvisWebSocketClient(String serverUrl) {
        this.serverUrl = serverUrl;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void setListener(ConnectionListener listener) {
        this.listener = listener;
    }

    /**
     * Conecta al servidor WebSocket.
     */
    public void connect() {
        shouldBeConnected = true;
        doConnect();
    }

    /**
     * Desconecta del servidor.
     */
    public void disconnect() {
        shouldBeConnected = false;
        stopPingTask();
        stopReconnectTask();
        activeSessionId = null;

        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    /**
     * Envía un comando de voz al backend.
     * Incluye el sessionId si hay una conversación activa.
     */
    public void sendVoiceCommand(String text) {
        if (connection != null && connection.isConnected()) {
            WebSocketMessage.VoiceCommand message = new WebSocketMessage.VoiceCommand(text, activeSessionId);
            connection.send(message.toJson());
            Log.d(TAG, "Sent voice command: " + text + (activeSessionId != null ? " [session: " + activeSessionId + "]" : ""));
        } else {
            Log.w(TAG, "Cannot send voice command - not connected");
            notifyError("No hay conexión con el servidor");
        }
    }

    /**
     * Envía un mensaje de chat (texto escrito) al backend.
     * Incluye el sessionId si hay una conversación activa.
     */
    public void sendChatMessage(String text) {
        if (connection != null && connection.isConnected()) {
            WebSocketMessage.ChatMessage message = new WebSocketMessage.ChatMessage(text, activeSessionId);
            connection.send(message.toJson());
            Log.d(TAG, "Sent chat message: " + text + (activeSessionId != null ? " [session: " + activeSessionId + "]" : ""));
        } else {
            Log.w(TAG, "Cannot send chat message - not connected");
            notifyError("No hay conexión con el servidor");
        }
    }

    /**
     * Envía una notificación al backend.
     */
    public void sendNotification(String app, String title, String text) {
        if (connection != null && connection.isConnected()) {
            WebSocketMessage.NotificationMessage message =
                    new WebSocketMessage.NotificationMessage(app, title, text);
            connection.send(message.toJson());
            Log.d(TAG, "Sent notification: " + app + " - " + title);
        }
    }

    /**
     * Termina la conversación activa.
     */
    public void endConversation(String reason) {
        if (activeSessionId != null && connection != null && connection.isConnected()) {
            WebSocketMessage.EndConversation message =
                    new WebSocketMessage.EndConversation(activeSessionId, reason);
            connection.send(message.toJson());
            Log.d(TAG, "Ending conversation: " + activeSessionId + " reason: " + reason);
            activeSessionId = null;
        }
    }

    /**
     * Verifica si está conectado.
     */
    public boolean isConnected() {
        return connection != null && connection.isConnected();
    }

    /**
     * Verifica si hay una conversación activa.
     */
    public boolean hasActiveConversation() {
        return activeSessionId != null;
    }

    /**
     * Obtiene el ID de la sesión activa.
     */
    public String getActiveSessionId() {
        return activeSessionId;
    }

    private void doConnect() {
        if (connection != null && connection.isConnected()) {
            return;
        }

        try {
            URI uri = new URI(serverUrl);
            connection = new WebSocketConnection(uri, new WebSocketConnection.Callback() {
                @Override
                public void onOpen() {
                    Log.i(TAG, "Connected to " + serverUrl);
                    startPingTask();
                    notifyConnected();
                }

                @Override
                public void onMessage(String message) {
                    handleServerMessage(message);
                }

                @Override
                public void onClose(int code, String reason) {
                    Log.i(TAG, "Disconnected: " + reason);
                    stopPingTask();
                    activeSessionId = null;
                    notifyDisconnected();
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error: " + ex.getMessage());
                    notifyError("Error de conexión: " + ex.getMessage());
                    scheduleReconnect();
                }
            });

            connection.connect();
            Log.d(TAG, "Connecting to " + serverUrl);

        } catch (Exception e) {
            Log.e(TAG, "Failed to create connection: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void handleServerMessage(String jsonString) {
        Object message = WebSocketMessage.parseServerMessage(jsonString);

        if (message instanceof WebSocketMessage.Response) {
            WebSocketMessage.Response response = (WebSocketMessage.Response) message;
            notifyResponse(response);

        } else if (message instanceof WebSocketMessage.StartConversation) {
            WebSocketMessage.StartConversation start = (WebSocketMessage.StartConversation) message;
            activeSessionId = start.sessionId;
            Log.i(TAG, "Conversation started: " + start.sessionId);
            notifyConversationStarted(start.sessionId, start.greeting, start.show);

        } else if (message instanceof WebSocketMessage.EndConversationResponse) {
            WebSocketMessage.EndConversationResponse end = (WebSocketMessage.EndConversationResponse) message;
            Log.i(TAG, "Conversation ended: " + end.sessionId + " reason: " + end.reason);
            if (end.sessionId.equals(activeSessionId)) {
                activeSessionId = null;
            }
            notifyConversationEnded(end.sessionId, end.farewell, end.reason);

        } else if (message instanceof WebSocketMessage.Action) {
            WebSocketMessage.Action action = (WebSocketMessage.Action) message;
            String params = action.params != null ? action.params.toString() : null;
            notifyAction(action.action, params);

        } else if (message instanceof WebSocketMessage.Error) {
            WebSocketMessage.Error error = (WebSocketMessage.Error) message;
            notifyError(error.message);

        } else if ("pong".equals(message)) {
            Log.d(TAG, "Received pong");
        }
    }

    private void startPingTask() {
        stopPingTask();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            if (connection != null && connection.isConnected()) {
                WebSocketMessage.Ping ping = new WebSocketMessage.Ping();
                connection.send(ping.toJson());
                Log.d(TAG, "Sent ping");
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopPingTask() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
    }

    private void scheduleReconnect() {
        if (!shouldBeConnected) return;

        stopReconnectTask();
        reconnectTask = scheduler.schedule(() -> {
            if (shouldBeConnected) {
                Log.d(TAG, "Attempting reconnect...");
                doConnect();
            }
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void stopReconnectTask() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    // Notificaciones al listener en el hilo principal

    private void notifyConnected() {
        if (listener != null) {
            mainHandler.post(() -> listener.onConnected());
        }
    }

    private void notifyDisconnected() {
        if (listener != null) {
            mainHandler.post(() -> listener.onDisconnected());
        }
    }

    private void notifyResponse(WebSocketMessage.Response response) {
        if (listener != null) {
            mainHandler.post(() -> listener.onResponse(response));
        }
    }

    private void notifyAction(String action, String params) {
        if (listener != null) {
            mainHandler.post(() -> listener.onAction(action, params));
        }
    }

    private void notifyError(String message) {
        if (listener != null) {
            mainHandler.post(() -> listener.onError(message));
        }
    }

    private void notifyConversationStarted(String sessionId, String greeting, WebSocketMessage.ShowContent show) {
        if (listener != null) {
            mainHandler.post(() -> listener.onConversationStarted(sessionId, greeting, show));
        }
    }

    private void notifyConversationEnded(String sessionId, String farewell, String reason) {
        if (listener != null) {
            mainHandler.post(() -> listener.onConversationEnded(sessionId, farewell, reason));
        }
    }

    /**
     * Libera recursos.
     */
    public void destroy() {
        disconnect();
        scheduler.shutdown();
    }
}
