package com.yarvis.assistant.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.yarvis.assistant.MainActivity;
import com.yarvis.assistant.R;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Foreground Service dedicado para mantener la conexión WebSocket persistente.
 * Este servicio se mantiene activo independientemente del ciclo de vida de las Activities.
 */
public class WebSocketService extends Service implements YarvisWebSocketClient.ConnectionListener {

    private static final String TAG = "WebSocketService";

    public static final String ACTION_START = "com.yarvis.assistant.websocket.START";
    public static final String ACTION_STOP = "com.yarvis.assistant.websocket.STOP";
    public static final String ACTION_RECONNECT = "com.yarvis.assistant.websocket.RECONNECT";

    private static final String CHANNEL_ID = "yarvis_websocket_channel";
    private static final int NOTIFICATION_ID = 2;

    private static volatile boolean isRunning = false;

    private final IBinder binder = new LocalBinder();
    private YarvisWebSocketClient webSocketClient;
    private ServerConfig serverConfig;

    private final CopyOnWriteArrayList<ConnectionStateListener> stateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<YarvisWebSocketClient.ConnectionListener> messageListeners = new CopyOnWriteArrayList<>();

    private boolean isConnected = false;
    private boolean isAuthenticated = false;

    /**
     * Listener para cambios de estado de conexión.
     */
    public interface ConnectionStateListener {
        void onConnectionStateChanged(boolean connected, boolean authenticated);
    }

    public class LocalBinder extends Binder {
        public WebSocketService getService() {
            return WebSocketService.this;
        }
    }

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WebSocketService created");
        serverConfig = new ServerConfig(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "onStartCommand: " + action);

        if (ACTION_START.equals(action)) {
            startForegroundWithNotification();
            connectIfEnabled();
        } else if (ACTION_STOP.equals(action)) {
            disconnect();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        } else if (ACTION_RECONNECT.equals(action)) {
            reconnect();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "WebSocketService destroyed");
        disconnect();
        isRunning = false;
    }

    // ==================== Public API ====================

    /**
     * Obtiene el cliente WebSocket.
     */
    public YarvisWebSocketClient getWebSocketClient() {
        return webSocketClient;
    }

    /**
     * Verifica si está conectado.
     */
    public boolean isConnected() {
        return isConnected && webSocketClient != null && webSocketClient.isConnected();
    }

    /**
     * Verifica si está autenticado.
     */
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    /**
     * Conecta al backend si está habilitado en la configuración.
     */
    public void connectIfEnabled() {
        if (serverConfig.isEnabled()) {
            connect();
        }
    }

    /**
     * Conecta al backend con la configuración actual.
     */
    public void connect() {
        String serverUrl = serverConfig.getServerUrl();
        String password = serverConfig.getPassword();
        String agentName = serverConfig.getAgentName();

        if (serverUrl == null || serverUrl.isEmpty() || password == null || password.isEmpty()) {
            Log.w(TAG, "Cannot connect: missing URL or password");
            return;
        }

        // Desconectar si ya existe una conexión
        if (webSocketClient != null) {
            webSocketClient.destroy();
        }

        webSocketClient = new YarvisWebSocketClient(serverUrl);
        webSocketClient.setCredentials(password, agentName);
        webSocketClient.setProductionMode(serverConfig.isProductionMode());
        webSocketClient.setListener(this);
        webSocketClient.connect();
        String envLabel = serverConfig.isProductionMode() ? "PROD" : "DEV";
        Log.d(TAG, "Connecting to: " + serverUrl + " as " + agentName + " [" + envLabel + "]");
    }

    /**
     * Desconecta del backend.
     */
    public void disconnect() {
        isConnected = false;
        isAuthenticated = false;
        if (webSocketClient != null) {
            webSocketClient.destroy();
            webSocketClient = null;
        }
        updateNotification();
        notifyConnectionStateChanged();
        Log.d(TAG, "Disconnected");
    }

    /**
     * Reconecta al backend (desconecta y vuelve a conectar).
     */
    public void reconnect() {
        disconnect();
        if (serverConfig.isEnabled()) {
            connect();
        }
    }

    /**
     * Actualiza las credenciales y reconecta si es necesario.
     */
    public void updateCredentials(String serverUrl, String password, String agentName, boolean enabled) {
        serverConfig.setServerUrl(serverUrl);
        serverConfig.setPassword(password);
        serverConfig.setAgentName(agentName);
        serverConfig.setEnabled(enabled);

        if (enabled) {
            reconnect();
        } else {
            disconnect();
        }
    }

    /**
     * Actualiza el modo de producción en tiempo real.
     * No requiere reconexión, se aplica a los siguientes mensajes.
     */
    public void updateProductionMode(boolean production) {
        serverConfig.setProductionMode(production);
        if (webSocketClient != null) {
            webSocketClient.setProductionMode(production);
            String envLabel = production ? "PROD" : "DEV";
            Log.d(TAG, "Production mode updated to: " + envLabel);
        }
    }

    // ==================== Listeners Management ====================

    /**
     * Agrega un listener para cambios de estado de conexión.
     */
    public void addConnectionStateListener(ConnectionStateListener listener) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener);
            // Notificar estado actual
            listener.onConnectionStateChanged(isConnected, isAuthenticated);
        }
    }

    /**
     * Elimina un listener de estado de conexión.
     */
    public void removeConnectionStateListener(ConnectionStateListener listener) {
        stateListeners.remove(listener);
    }

    /**
     * Agrega un listener para mensajes del WebSocket.
     */
    public void addMessageListener(YarvisWebSocketClient.ConnectionListener listener) {
        if (!messageListeners.contains(listener)) {
            messageListeners.add(listener);
        }
    }

    /**
     * Elimina un listener de mensajes.
     */
    public void removeMessageListener(YarvisWebSocketClient.ConnectionListener listener) {
        messageListeners.remove(listener);
    }

    private void notifyConnectionStateChanged() {
        for (ConnectionStateListener listener : stateListeners) {
            listener.onConnectionStateChanged(isConnected, isAuthenticated);
        }
    }

    // ==================== Notification ====================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Conexión WebSocket",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Estado de conexión con el backend");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundWithNotification() {
        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        isRunning = true;
        Log.d(TAG, "Foreground service started");
    }

    private void updateNotification() {
        if (!isRunning) return;

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String statusText;
        int iconRes;
        if (isAuthenticated) {
            statusText = "Conectado al backend";
            iconRes = R.drawable.ic_cloud_done;
        } else if (isConnected) {
            statusText = "Conectando...";
            iconRes = R.drawable.ic_cloud_sync;
        } else {
            statusText = serverConfig.isEnabled() ? "Desconectado (reconectando...)" : "Backend deshabilitado";
            iconRes = R.drawable.ic_cloud_off;
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Yarvis Backend")
                .setContentText(statusText)
                .setSmallIcon(iconRes)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    // ==================== WebSocket Listener Implementation ====================

    @Override
    public void onConnected() {
        Log.i(TAG, "WebSocket connected");
        isConnected = true;
        updateNotification();
        notifyConnectionStateChanged();

        // Propagar a listeners
        for (YarvisWebSocketClient.ConnectionListener listener : messageListeners) {
            listener.onConnected();
        }
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "WebSocket disconnected");
        isConnected = false;
        isAuthenticated = false;
        updateNotification();
        notifyConnectionStateChanged();

        // Propagar a listeners
        for (YarvisWebSocketClient.ConnectionListener listener : messageListeners) {
            listener.onDisconnected();
        }
    }

    @Override
    public void onResponse(WebSocketMessage.Response response) {
        Log.d(TAG, "Response received: \"" + response.text + "\" speak=" + response.speak + " (listeners: " + messageListeners.size() + ")");
        for (YarvisWebSocketClient.ConnectionListener listener : messageListeners) {
            Log.d(TAG, "Notifying listener: " + listener.getClass().getSimpleName());
            listener.onResponse(response);
        }
    }

    @Override
    public void onAction(String action, String params) {
        Log.d(TAG, "Action received: " + action + " (listeners: " + messageListeners.size() + ")");
        for (YarvisWebSocketClient.ConnectionListener listener : messageListeners) {
            listener.onAction(action, params);
        }
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "Error from backend: " + message + " (listeners: " + messageListeners.size() + ")");
        for (YarvisWebSocketClient.ConnectionListener listener : messageListeners) {
            listener.onError(message);
        }
    }

    @Override
    public void onConversationStarted(String sessionId, String greeting, WebSocketMessage.ShowContent show) {
        Log.i(TAG, "Conversation started: " + sessionId);
        for (YarvisWebSocketClient.ConnectionListener listener : messageListeners) {
            listener.onConversationStarted(sessionId, greeting, show);
        }
    }

    @Override
    public void onConversationEnded(String sessionId, String farewell, String reason) {
        Log.i(TAG, "Conversation ended: " + sessionId);
        for (YarvisWebSocketClient.ConnectionListener listener : messageListeners) {
            listener.onConversationEnded(sessionId, farewell, reason);
        }
    }

    @Override
    public void onAuthResult(boolean success, String message) {
        Log.i(TAG, "Auth result: " + success + " - " + message);
        isAuthenticated = success;
        updateNotification();
        notifyConnectionStateChanged();

        for (YarvisWebSocketClient.ConnectionListener listener : messageListeners) {
            listener.onAuthResult(success, message);
        }
    }

    @Override
    public void onPasswordChangeResult(boolean success, String message) {
        Log.i(TAG, "Password change result: " + success);
        for (YarvisWebSocketClient.ConnectionListener listener : messageListeners) {
            listener.onPasswordChangeResult(success, message);
        }
    }
}
