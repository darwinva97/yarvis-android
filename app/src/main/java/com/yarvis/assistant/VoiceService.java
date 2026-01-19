package com.yarvis.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.yarvis.assistant.chat.ChatHistoryManager;
import com.yarvis.assistant.chat.ChatMessageModel;
import com.yarvis.assistant.network.ServerConfig;
import com.yarvis.assistant.network.WebSocketService;
import com.yarvis.assistant.network.YarvisWebSocketClient;
import com.yarvis.assistant.processing.CommandProcessorManager;
import com.yarvis.assistant.processing.CommandResult;
import com.yarvis.assistant.processing.ResultCallback;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Foreground Service para reconocimiento de voz continuo.
 * Usa SpeechRecognizer de Android para convertir voz a texto.
 * Usa WebSocketService para conexión persistente con el backend.
 */
public class VoiceService extends Service implements
        YarvisWebSocketClient.ConnectionListener,
        WebSocketService.ConnectionStateListener {

    // Estado de conversación
    private boolean inConversation = false;
    private String currentSessionId = null;

    private static final String TAG = "VoiceService";

    // Acciones para controlar el servicio
    public static final String ACTION_START = "com.yarvis.assistant.START";
    public static final String ACTION_STOP = "com.yarvis.assistant.STOP";

    // Broadcasts para comunicar con MainActivity
    public static final String ACTION_SPEECH_RESULT = "com.yarvis.assistant.SPEECH_RESULT";
    public static final String ACTION_SPEECH_PARTIAL = "com.yarvis.assistant.SPEECH_PARTIAL";
    public static final String ACTION_COMMAND_DETECTED = "com.yarvis.assistant.COMMAND_DETECTED";
    public static final String ACTION_CONNECTION_STATUS = "com.yarvis.assistant.CONNECTION_STATUS";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_CONNECTED = "connected";

    // Configuración del canal de notificación
    private static final String CHANNEL_ID = "yarvis_voice_channel";
    private static final int NOTIFICATION_ID = 1;

    // Estado del servicio
    private static volatile boolean isRunning = false;

    // Componentes de reconocimiento de voz
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private Handler mainHandler;
    private boolean isListening = false;

    // Text-to-Speech
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean isSpeaking = false;

    // Control de lectura de notificaciones
    private boolean readNotifications = true;

    // Binder para comunicación con Activities
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public VoiceService getService() {
            return VoiceService.this;
        }
    }

    // WebSocket y configuración
    private WebSocketService webSocketService;
    private boolean webSocketBound = false;
    private ServerConfig serverConfig;
    private boolean backendEnabled = false;
    private boolean backendConnected = false;

    private final ServiceConnection webSocketConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            WebSocketService.LocalBinder localBinder = (WebSocketService.LocalBinder) binder;
            webSocketService = localBinder.getService();
            webSocketBound = true;

            // Registrar listeners
            webSocketService.addConnectionStateListener(VoiceService.this);
            webSocketService.addMessageListener(VoiceService.this);

            backendConnected = webSocketService.isAuthenticated();
            updateNotification(backendConnected);
            Log.d(TAG, "Bound to WebSocketService, connected: " + backendConnected);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            webSocketService = null;
            webSocketBound = false;
            backendConnected = false;
        }
    };

    // Sistema de procesamiento de comandos (POO avanzado)
    private CommandProcessorManager commandProcessorManager;

    // Historial de chat
    private ChatHistoryManager chatHistoryManager;

    // Receiver para notificaciones del sistema
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!readNotifications || !isRunning) return;

            String app = intent.getStringExtra(NotificationService.EXTRA_NOTIFICATION_APP);
            String title = intent.getStringExtra(NotificationService.EXTRA_NOTIFICATION_TITLE);
            String text = intent.getStringExtra(NotificationService.EXTRA_NOTIFICATION_TEXT);

            if (app == null) app = "Una aplicación";
            if (title == null) title = "";
            if (text == null) text = "";

            // Si el backend está habilitado, enviar la notificación
            if (backendEnabled && backendConnected && webSocketBound && webSocketService != null) {
                YarvisWebSocketClient client = webSocketService.getWebSocketClient();
                if (client != null) {
                    client.sendNotification(app, title, text);
                }
            } else {
                // Comportamiento local: leer la notificación
                StringBuilder message = new StringBuilder();
                message.append("Notificación de ").append(app).append(". ");
                if (!title.isEmpty()) {
                    message.append(title).append(". ");
                }
                if (!text.isEmpty()) {
                    message.append(text);
                }

                String fullMessage = message.toString();
                Log.d(TAG, "Reading notification: " + fullMessage);

                sendCommandBroadcast("NOTIF: " + fullMessage);
                speak(fullMessage);
            }
        }
    };

    /**
     * Verifica si el servicio está corriendo.
     */
    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        mainHandler = new Handler(Looper.getMainLooper());
        serverConfig = new ServerConfig(this);
        chatHistoryManager = ChatHistoryManager.getInstance(this);
        createNotificationChannel();
        initializeTTS();
        registerNotificationReceiver();
        bindToWebSocketService();
        initializeCommandProcessor();
    }

    /**
     * Inicializa el sistema de procesamiento de comandos.
     * Demuestra el uso de: Clases abstractas, Genéricos, Polimorfismo, etc.
     */
    private void initializeCommandProcessor() {
        commandProcessorManager = CommandProcessorManager.getInstance(this);
        Log.d(TAG, "Command processor manager initialized");
    }

    /**
     * Se conecta al WebSocketService para usar la conexión persistente.
     */
    private void bindToWebSocketService() {
        backendEnabled = serverConfig.isEnabled();

        if (backendEnabled) {
            // Iniciar y bindear al WebSocketService
            Intent wsIntent = new Intent(this, WebSocketService.class);
            wsIntent.setAction(WebSocketService.ACTION_START);
            startService(wsIntent);
            bindService(wsIntent, webSocketConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "Binding to WebSocketService");
        } else {
            Log.d(TAG, "Backend disabled, running in local mode");
        }
    }

    /**
     * Registra el receiver para notificaciones.
     */
    private void registerNotificationReceiver() {
        IntentFilter filter = new IntentFilter(NotificationService.ACTION_NOTIFICATION_RECEIVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notificationReceiver, filter);
        }
        Log.d(TAG, "Notification receiver registered");
    }

    /**
     * Inicializa Text-to-Speech.
     */
    private void initializeTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("es", "ES"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.getDefault());
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        isSpeaking = true;
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        isSpeaking = false;
                        restartListening();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        isSpeaking = false;
                        restartListening();
                    }
                });
                ttsReady = true;
                Log.d(TAG, "TTS initialized");
            } else {
                Log.e(TAG, "TTS initialization failed");
            }
        });
    }

    /**
     * Habla el texto dado.
     */
    private void speak(String text) {
        if (ttsReady && tts != null) {
            stopListening();
            isSpeaking = true;
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "yarvis_response");
            Log.d(TAG, "Speaking: " + text);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_START.equals(action)) {
            startForegroundWithNotification();
            initializeSpeechRecognizer();
        } else if (ACTION_STOP.equals(action)) {
            stopListening();
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Obtiene el cliente WebSocket para acceso externo.
     */
    public YarvisWebSocketClient getWebSocketClient() {
        if (webSocketBound && webSocketService != null) {
            return webSocketService.getWebSocketClient();
        }
        return null;
    }

    /**
     * Obtiene el WebSocketService.
     */
    public WebSocketService getWebSocketService() {
        return webSocketService;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        stopListening();

        // Desconectar del WebSocketService (pero no destruirlo)
        if (webSocketBound && webSocketService != null) {
            webSocketService.removeConnectionStateListener(this);
            webSocketService.removeMessageListener(this);
            unbindService(webSocketConnection);
            webSocketBound = false;
        }

        // Limpiar procesador de comandos
        if (commandProcessorManager != null) {
            commandProcessorManager.shutdown();
        }

        try {
            unregisterReceiver(notificationReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        isRunning = false;
    }

    /**
     * Crea el canal de notificación (requerido Android 8.0+).
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Yarvis Voice Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Servicio de escucha de voz activo");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Inicia el servicio en primer plano con notificación.
     */
    private void startForegroundWithNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, VoiceService.class);
        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String contentText = backendEnabled
                ? "Escuchando... (Backend conectando)"
                : "Escuchando... Di \"Hey Yarvis\"";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Yarvis")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_stop, "Detener", stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        isRunning = true;
        Log.d(TAG, "Foreground service started");
    }

    /**
     * Actualiza la notificación con el estado de conexión.
     */
    private void updateNotification(boolean connected) {
        if (!isRunning) return;

        String contentText = backendEnabled
                ? (connected ? "Escuchando... (Backend conectado)" : "Escuchando... (Backend desconectado)")
                : "Escuchando... Di \"Hey Yarvis\"";

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, VoiceService.class);
        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Yarvis")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_stop, "Detener", stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * Inicializa el SpeechRecognizer.
     */
    private void initializeSpeechRecognizer() {
        mainHandler.post(() -> {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }

            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.e(TAG, "Speech recognition not available");
                sendBroadcast(ACTION_SPEECH_RESULT, "Error: Reconocimiento de voz no disponible");
                return;
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new SpeechListener());

            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            Log.d(TAG, "SpeechRecognizer initialized");

            isListening = true;
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "Started listening");
        });
    }

    /**
     * Inicia el reconocimiento de voz.
     */
    private void startListening() {
        mainHandler.post(() -> {
            if (speechRecognizer != null && !isListening) {
                isListening = true;
                speechRecognizer.startListening(recognizerIntent);
                Log.d(TAG, "Started listening");
            }
        });
    }

    /**
     * Detiene el reconocimiento de voz.
     */
    private void stopListening() {
        mainHandler.post(() -> {
            isListening = false;
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                Log.d(TAG, "Stopped listening");
            }
        });
    }

    /**
     * Reinicia el reconocimiento después de un resultado o error.
     */
    private void restartListening() {
        mainHandler.postDelayed(() -> {
            if (isRunning && speechRecognizer != null) {
                isListening = true;
                speechRecognizer.startListening(recognizerIntent);
                Log.d(TAG, "Restarted listening");
            }
        }, 500);
    }

    /**
     * Envía un broadcast con el texto reconocido.
     */
    private void sendBroadcast(String action, String text) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_TEXT, text);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    /**
     * Envía un broadcast cuando se detecta un comando.
     */
    private void sendCommandBroadcast(String command) {
        Intent intent = new Intent(ACTION_COMMAND_DETECTED);
        intent.putExtra(EXTRA_COMMAND, command);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    /**
     * Envía broadcast de estado de conexión.
     */
    private void sendConnectionBroadcast(boolean connected) {
        Intent intent = new Intent(ACTION_CONNECTION_STATUS);
        intent.putExtra(EXTRA_CONNECTED, connected);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    /**
     * Verifica si el texto comienza con el wake word "yarvis" o "jarvis".
     */
    private boolean startsWithWakeWord(String text) {
        String lowerText = text.toLowerCase(Locale.getDefault()).trim();
        return lowerText.startsWith("yarvis") || lowerText.startsWith("jarvis");
    }

    /**
     * Elimina el wake word del inicio del texto.
     */
    private String removeWakeWord(String text) {
        String lowerText = text.toLowerCase(Locale.getDefault()).trim();
        if (lowerText.startsWith("yarvis")) {
            return text.trim().substring(6).trim();
        } else if (lowerText.startsWith("jarvis")) {
            return text.trim().substring(6).trim();
        }
        return text;
    }

    /**
     * Procesa el texto reconocido.
     * Solo envía al backend si comienza con "yarvis" o "jarvis".
     * Si no, procesa localmente.
     */
    private void processVoiceCommand(String text) {
        YarvisWebSocketClient client = getWebSocketClient();
        if (backendEnabled && backendConnected && client != null && startsWithWakeWord(text)) {
            // Eliminar wake word y enviar al backend
            String command = removeWakeWord(text);
            if (!command.isEmpty()) {
                client.sendVoiceCommand(command);
                Log.d(TAG, "Sent to backend: " + command);
            } else {
                // Solo dijo "yarvis" sin comando
                String response = "¿Sí? ¿En qué puedo ayudarte?";
                speak(response);
                Log.d(TAG, "Wake word only, no command");
            }
        } else {
            // Procesar localmente si no tiene wake word o backend no disponible
            checkForLocalCommands(text);
        }
    }

    /**
     * Verifica si el texto contiene comandos locales conocidos.
     * Usa el CommandProcessorManager para comandos avanzados (POO).
     */
    private void checkForLocalCommands(String text) {
        String lowerText = text.toLowerCase(Locale.getDefault());

        // Comandos especiales del asistente (wake word, saludos, despedidas)
        if (lowerText.contains("hey yarvis") || lowerText.contains("hola yarvis") ||
            lowerText.contains("oye yarvis")) {
            String response = "¿Sí? ¿En qué puedo ayudarte?";
            sendCommandBroadcast("WAKE: " + response);
            speak(response);
            Log.d(TAG, "Wake word detected!");
            return;
        }

        if (lowerText.contains("say hello") || lowerText.contains("di hola") ||
            lowerText.contains("saluda")) {
            String response = "¡Hola! Soy Yarvis, tu asistente de voz.";
            sendCommandBroadcast("COMMAND: " + response);
            speak(response);
            Log.d(TAG, "Hello command detected!");
            return;
        }

        if (lowerText.contains("cómo te llamas") || lowerText.contains("cuál es tu nombre")) {
            String response = "Me llamo Yarvis, mucho gusto.";
            sendCommandBroadcast("COMMAND: " + response);
            speak(response);
            return;
        }

        // Comandos para terminar conversación (modo local)
        if (lowerText.contains("adiós") || lowerText.contains("adios") ||
            lowerText.contains("hasta luego") || lowerText.contains("chao") ||
            lowerText.contains("termina") || lowerText.contains("eso es todo")) {
            String response = "Hasta luego, que tengas un buen día.";
            sendCommandBroadcast("COMMAND: " + response);
            speak(response);
            return;
        }

        // Usar el sistema de procesamiento avanzado para otros comandos
        // Demuestra: POLIMORFISMO, GENÉRICOS, CLASES ABSTRACTAS, INTERFACES FUNCIONALES
        processWithCommandProcessor(text);
    }

    /**
     * Procesa comandos usando el sistema POO avanzado.
     * Utiliza: CommandProcessorManager, Repository<T>, Cache<K,V>,
     * clases abstractas, interfaces funcionales (ResultCallback).
     */
    private void processWithCommandProcessor(String text) {
        if (commandProcessorManager == null) {
            Log.w(TAG, "Command processor not initialized");
            return;
        }

        // ResultCallback es una INTERFAZ FUNCIONAL - se usa con lambda
        ResultCallback<CommandResult> callback = result -> {
            mainHandler.post(() -> {
                if (result.success()) {
                    Log.d(TAG, "Command processed successfully: " + result.message());
                    sendCommandBroadcast("PROCESSED: " + result.message());
                    speak(result.message());
                } else {
                    Log.w(TAG, "Command failed: " + result.message());
                    // No hablar errores, solo loguear
                }
            });
        };

        // processText usa POLIMORFISMO para seleccionar el procesador correcto
        commandProcessorManager.processText(text, callback);
    }

    // ==================== WebSocketService.ConnectionStateListener ====================

    @Override
    public void onConnectionStateChanged(boolean connected, boolean authenticated) {
        Log.i(TAG, "Connection state changed: connected=" + connected + ", authenticated=" + authenticated);
        backendConnected = authenticated;
        updateNotification(authenticated);
        sendConnectionBroadcast(authenticated);

        if (!connected) {
            inConversation = false;
            currentSessionId = null;
        }
    }

    // ==================== WebSocket Listener ====================

    @Override
    public void onConnected() {
        Log.i(TAG, "Backend connected");
        // Estado manejado por onConnectionStateChanged
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "Backend disconnected");
        inConversation = false;
        currentSessionId = null;
        // Estado manejado por onConnectionStateChanged
    }

    @Override
    public void onResponse(com.yarvis.assistant.network.WebSocketMessage.Response response) {
        Log.d(TAG, "Backend response: " + response.text);
        sendCommandBroadcast("BACKEND: " + response.text);

        // Agregar respuesta al historial de chat
        ChatMessageModel message = ChatMessageModel.fromAssistantResponse(response);
        chatHistoryManager.addMessage(message);

        if (response.speak) {
            speak(response.text);
        }
    }

    @Override
    public void onAction(String action, String params) {
        Log.d(TAG, "Backend action: " + action + " params: " + params);
        sendCommandBroadcast("ACTION: " + action);
        // Aquí se pueden manejar acciones específicas como:
        // - TURN_ON_LIGHT
        // - PLAY_MUSIC
        // - SET_ALARM
        // etc.
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "Backend error: " + message);
        sendCommandBroadcast("ERROR: " + message);
    }

    @Override
    public void onConversationStarted(String sessionId, String greeting, com.yarvis.assistant.network.WebSocketMessage.ShowContent show) {
        Log.i(TAG, "Conversation started: " + sessionId);
        inConversation = true;
        currentSessionId = sessionId;
        sendCommandBroadcast("CONVERSATION_START: " + sessionId);

        if (greeting != null && !greeting.isEmpty()) {
            speak(greeting);
        }
    }

    @Override
    public void onConversationEnded(String sessionId, String farewell, String reason) {
        Log.i(TAG, "Conversation ended: " + sessionId + " reason: " + reason);
        inConversation = false;
        currentSessionId = null;
        sendCommandBroadcast("CONVERSATION_END: " + reason);

        if (farewell != null && !farewell.isEmpty()) {
            speak(farewell);
        }
    }

    @Override
    public void onAuthResult(boolean success, String message) {
        Log.i(TAG, "Auth result: " + success + " - " + message);
        if (success) {
            sendCommandBroadcast("AUTH_SUCCESS");
            updateNotification(true);
        } else {
            sendCommandBroadcast("AUTH_FAILED: " + message);
            // La desconexión es manejada por WebSocketService
        }
    }

    @Override
    public void onPasswordChangeResult(boolean success, String message) {
        Log.i(TAG, "Password change result: " + success + " - " + message);
        // En el servicio no manejamos cambio de contraseña, solo en Settings
    }

    /**
     * Verifica si hay una conversación activa.
     */
    public boolean isInConversation() {
        return inConversation;
    }

    // ==================== Speech Listener ====================

    /**
     * Listener para eventos del SpeechRecognizer.
     */
    private class SpeechListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Ready for speech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "Speech started");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "Speech ended");
        }

        @Override
        public void onError(int error) {
            String errorMessage;
            switch (error) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                    errorMessage = "No se entendió";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    errorMessage = "Tiempo de espera";
                    break;
                case SpeechRecognizer.ERROR_AUDIO:
                    errorMessage = "Error de audio";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    errorMessage = "Error del cliente";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    errorMessage = "Error de red";
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    errorMessage = "Timeout de red";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    errorMessage = "Error del servidor";
                    break;
                default:
                    errorMessage = "Error: " + error;
            }
            Log.w(TAG, "Recognition error: " + errorMessage);
            restartListening();
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                Log.d(TAG, "Final result: " + text);
                sendBroadcast(ACTION_SPEECH_RESULT, text);
                processVoiceCommand(text);
            }
            restartListening();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                Log.d(TAG, "Partial result: " + text);
                sendBroadcast(ACTION_SPEECH_PARTIAL, text);
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }
}
