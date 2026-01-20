package com.yarvis.assistant;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.yarvis.assistant.chat.ChatActivity;
import com.yarvis.assistant.network.ServerConfig;
import com.yarvis.assistant.network.WebSocketService;
import com.yarvis.assistant.settings.SettingsActivity;

import java.util.Map;

public class MainActivity extends AppCompatActivity implements
        SpeechBroadcastReceiver.SpeechListener,
        VoiceServiceController.StateListener {

    private PermissionManager permissionManager;
    private VoiceServiceController serviceController;
    private SpeechBroadcastReceiver speechReceiver;
    private MainUIManager uiManager;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeComponents();
        setupClickListeners();
        updateUI();
    }

    private void initializeComponents() {
        permissionManager = new PermissionManager(this);
        serviceController = new VoiceServiceController(this);
        serviceController.setStateListener(this);

        speechReceiver = new SpeechBroadcastReceiver();
        speechReceiver.setListener(this);

        uiManager = new MainUIManager(
                findViewById(R.id.status_text),
                findViewById(R.id.recognized_text),
                findViewById(R.id.command_text),
                findViewById(R.id.toggle_button),
                findViewById(R.id.notification_permission_button),
                findViewById(R.id.battery_optimization_button)
        );

        startWebSocketServiceIfEnabled();
    }

    private void startWebSocketServiceIfEnabled() {
        ServerConfig serverConfig = new ServerConfig(this);
        if (serverConfig.isEnabled()) {
            Intent wsIntent = new Intent(this, WebSocketService.class);
            wsIntent.setAction(WebSocketService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(wsIntent);
            } else {
                startService(wsIntent);
            }
        }
    }

    private void setupClickListeners() {
        Button toggleButton = findViewById(R.id.toggle_button);
        Button notificationButton = findViewById(R.id.notification_permission_button);
        Button batteryButton = findViewById(R.id.battery_optimization_button);
        Button chatButton = findViewById(R.id.chat_button);
        Button settingsButton = findViewById(R.id.settings_button);

        toggleButton.setOnClickListener(v -> onToggleClick());
        notificationButton.setOnClickListener(v ->
                startActivity(permissionManager.getNotificationAccessSettingsIntent()));
        batteryButton.setOnClickListener(v ->
                startActivity(permissionManager.getBatteryOptimizationExclusionIntent()));
        chatButton.setOnClickListener(v -> openChat());
        settingsButton.setOnClickListener(v -> openSettings());
    }

    private void openChat() {
        Intent intent = new Intent(this, ChatActivity.class);
        startActivity(intent);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        serviceController.syncState();
        speechReceiver.register(this);
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        speechReceiver.unregister(this);
    }

    private void onToggleClick() {
        if (serviceController.isRunning()) {
            serviceController.stop();
        } else {
            checkPermissionsAndStart();
        }
    }

    private void checkPermissionsAndStart() {
        if (permissionManager.hasAllRequiredPermissions()) {
            startService();
        } else {
            permissionManager.requestMissingPermissions(permissionLauncher);
        }
    }

    private void onPermissionsResult(Map<String, Boolean> results) {
        if (permissionManager.hasAudioPermission()) {
            startService();
        } else {
            uiManager.showStatus("Permiso de micrófono denegado");
        }
    }

    private void startService() {
        serviceController.start();
        uiManager.clearRecognitionTexts();
    }

    private void updateUI() {
        uiManager.updateServiceState(serviceController.isRunning());
        uiManager.updatePermissionButtons(
                permissionManager.isNotificationAccessEnabled(),
                permissionManager.isBatteryOptimizationIgnored()
        );
    }

    @Override
    public void onSpeechResult(String text) {
        uiManager.showSpeechResult(text);
    }

    @Override
    public void onSpeechPartial(String text) {
        uiManager.showSpeechPartial(text);
    }

    @Override
    public void onCommandDetected(String command) {
        uiManager.showCommand(command);
    }

    @Override
    public void onServiceStateChanged(boolean isRunning) {
        updateUI();
    }
}
