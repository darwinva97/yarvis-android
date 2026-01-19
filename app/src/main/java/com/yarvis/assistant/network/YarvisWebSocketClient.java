package com.yarvis.assistant.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Cliente WebSocket para comunicación con el backend de Yarvis.
 * Maneja reconexión automática, sesiones de conversación, autenticación y ping/pong.
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
    private boolean isAuthenticated = false;
    private ConnectionListener listener;

    // Credenciales para autenticación
    private String password;
    private String agentName;

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
        void onAuthResult(boolean success, String message);
        void onPasswordChangeResult(boolean success, String message);
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
     * Establece las credenciales para autenticación.
     */
    public void setCredentials(String password, String agentName) {
        this.password = password;
        this.agentName = agentName;
    }

    /**
     * Conecta al servidor WebSocket.
     */
    public void connect() {
        shouldBeConnected = true;
        isAuthenticated = false;
        doConnect();
    }

    /**
     * Desconecta del servidor.
     */
    public void disconnect() {
        shouldBeConnected = false;
        isAuthenticated = false;
        stopPingTask();
        stopReconnectTask();
        activeSessionId = null;

        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    /**
     * Envía la autenticación al servidor.
     */
    private void sendAuthentication() {
        if (connection != null && connection.isConnected() && password != null) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "auth");
                json.put("password", password);
                if (agentName != null && !agentName.isEmpty()) {
                    json.put("agentName", agentName);
                }
                connection.send(json.toString());
                Log.d(TAG, "Sent authentication request");
            } catch (JSONException e) {
                Log.e(TAG, "Error creating auth message", e);
            }
        }
    }

    /**
     * Solicita cambio de contraseña.
     */
    public void changePassword(String currentPassword, String newPassword) {
        if (connection != null && connection.isConnected() && isAuthenticated) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "change_password");
                json.put("currentPassword", currentPassword);
                json.put("newPassword", newPassword);
                connection.send(json.toString());
                Log.d(TAG, "Sent change password request");
            } catch (JSONException e) {
                Log.e(TAG, "Error creating change password message", e);
                notifyPasswordChangeResult(false, "Error al crear solicitud");
            }
        } else {
            notifyPasswordChangeResult(false, "No conectado o no autenticado");
        }
    }

    /**
     * Envía un comando de voz al backend.
     * Incluye el sessionId si hay una conversación activa.
     */
    public void sendVoiceCommand(String text) {
        if (!isAuthenticated) {
            Log.w(TAG, "Cannot send voice command - not authenticated");
            notifyError("No autenticado con el servidor");
            return;
        }
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
        if (!isAuthenticated) {
            Log.w(TAG, "Cannot send chat message - not authenticated");
            notifyError("No autenticado con el servidor");
            return;
        }
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
        if (!isAuthenticated) {
            Log.w(TAG, "Cannot send notification - not authenticated");
            return;
        }
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
        if (activeSessionId != null && connection != null && connection.isConnected() && isAuthenticated) {
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
     * Verifica si está autenticado.
     */
    public boolean isAuthenticated() {
        return isAuthenticated;
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
                    // Enviar autenticación inmediatamente después de conectar
                    sendAuthentication();
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
                    isAuthenticated = false;
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
        // Verificar si es respuesta de autenticación
        try {
            JSONObject json = new JSONObject(jsonString);
            String type = json.optString("type", "");
            
            if ("auth_response".equals(type)) {
                boolean success = json.optBoolean("success", false);
                String message = json.optString("message", "");
                isAuthenticated = success;
                if (success) {
                    Log.i(TAG, "Authentication successful");
                    startPingTask();
                    notifyConnected();
                } else {
                    Log.w(TAG, "Authentication failed: " + message);
                }
                notifyAuthResult(success, message);
                return;
            }
            
            if ("change_password_response".equals(type)) {
                boolean success = json.optBoolean("success", false);
                String message = json.optString("message", "");
                notifyPasswordChangeResult(success, message);
                return;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing auth response", e);
        }

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

    private void notifyAuthResult(boolean success, String message) {
        if (listener != null) {
            mainHandler.post(() -> listener.onAuthResult(success, message));
        }
    }

    private void notifyPasswordChangeResult(boolean success, String message) {
        if (listener != null) {
            mainHandler.post(() -> listener.onPasswordChangeResult(success, message));
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
