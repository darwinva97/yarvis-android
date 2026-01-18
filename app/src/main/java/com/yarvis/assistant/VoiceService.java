package com.yarvis.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.Locale;

/**
 * Foreground Service para reconocimiento de voz continuo.
 * Usa SpeechRecognizer de Android para convertir voz a texto.
 */
public class VoiceService extends Service {

    private static final String TAG = "VoiceService";

    // Acciones para controlar el servicio
    public static final String ACTION_START = "com.yarvis.assistant.START";
    public static final String ACTION_STOP = "com.yarvis.assistant.STOP";

    // Broadcasts para comunicar con MainActivity
    public static final String ACTION_SPEECH_RESULT = "com.yarvis.assistant.SPEECH_RESULT";
    public static final String ACTION_SPEECH_PARTIAL = "com.yarvis.assistant.SPEECH_PARTIAL";
    public static final String ACTION_COMMAND_DETECTED = "com.yarvis.assistant.COMMAND_DETECTED";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_COMMAND = "command";

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

            // Construir mensaje para leer
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

            // Enviar a UI y hablar
            sendCommandBroadcast("NOTIF: " + fullMessage);
            speak(fullMessage);
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
        createNotificationChannel();
        initializeTTS();
        registerNotificationReceiver();
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
                        // Reiniciar escucha después de hablar
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
            // Pausar escucha mientras habla para evitar feedback
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
            // startListening() se llama al final de initializeSpeechRecognizer()
        } else if (ACTION_STOP.equals(action)) {
            stopListening();
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        stopListening();
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

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Yarvis")
                .setContentText("Escuchando... Di \"Hey Yarvis\"")
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

            // Iniciar escucha después de que el reconocedor esté listo
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
     * Verifica si el texto contiene comandos conocidos.
     */
    private void checkForCommands(String text) {
        String lowerText = text.toLowerCase(Locale.getDefault());

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

        if (lowerText.contains("qué hora es") || lowerText.contains("dime la hora")) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", Locale.getDefault());
            String hora = sdf.format(new java.util.Date());
            String response = "Son las " + hora;
            sendCommandBroadcast("COMMAND: " + response);
            speak(response);
            return;
        }
    }

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
            // Nivel de audio - se puede usar para visualización
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

            // Reiniciar escucha automáticamente
            restartListening();
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                Log.d(TAG, "Final result: " + text);
                sendBroadcast(ACTION_SPEECH_RESULT, text);
                checkForCommands(text);
            }

            // Reiniciar escucha para reconocimiento continuo
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
