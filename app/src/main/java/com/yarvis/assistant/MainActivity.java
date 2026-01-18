package com.yarvis.assistant;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MainActivity - muestra texto reconocido y comandos detectados.
 */
public class MainActivity extends AppCompatActivity {

    private TextView statusText;
    private TextView recognizedText;
    private TextView commandText;
    private Button toggleButton;
    private Button notificationPermissionButton;
    private Button batteryOptimizationButton;
    private boolean isServiceRunning = false;

    // Receiver para broadcasts del servicio
    private final BroadcastReceiver speechReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case VoiceService.ACTION_SPEECH_RESULT:
                    String result = intent.getStringExtra(VoiceService.EXTRA_TEXT);
                    if (result != null) {
                        recognizedText.setText("\"" + result + "\"");
                    }
                    break;

                case VoiceService.ACTION_SPEECH_PARTIAL:
                    String partial = intent.getStringExtra(VoiceService.EXTRA_TEXT);
                    if (partial != null) {
                        recognizedText.setText(partial + "...");
                    }
                    break;

                case VoiceService.ACTION_COMMAND_DETECTED:
                    String command = intent.getStringExtra(VoiceService.EXTRA_COMMAND);
                    if (command != null) {
                        commandText.setText(command);
                    }
                    break;
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.status_text);
        recognizedText = findViewById(R.id.recognized_text);
        commandText = findViewById(R.id.command_text);
        toggleButton = findViewById(R.id.toggle_button);
        notificationPermissionButton = findViewById(R.id.notification_permission_button);
        batteryOptimizationButton = findViewById(R.id.battery_optimization_button);

        toggleButton.setOnClickListener(this::onToggleClick);
        notificationPermissionButton.setOnClickListener(v -> openNotificationAccessSettings());
        batteryOptimizationButton.setOnClickListener(v -> requestBatteryOptimizationExclusion());

        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isServiceRunning = VoiceService.isRunning();
        updateUI();
        registerReceivers();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceivers();
    }

    /**
     * Registra los receivers para recibir broadcasts del servicio.
     */
    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(VoiceService.ACTION_SPEECH_RESULT);
        filter.addAction(VoiceService.ACTION_SPEECH_PARTIAL);
        filter.addAction(VoiceService.ACTION_COMMAND_DETECTED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(speechReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(speechReceiver, filter);
        }
    }

    /**
     * Des-registra los receivers.
     */
    private void unregisterReceivers() {
        try {
            unregisterReceiver(speechReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }
    }

    private void onToggleClick(View v) {
        if (isServiceRunning) {
            stopVoiceService();
        } else {
            checkPermissionsAndStart();
        }
    }

    private void checkPermissionsAndStart() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (permissionsNeeded.isEmpty()) {
            startVoiceService();
        } else {
            permissionLauncher.launch(permissionsNeeded.toArray(new String[0]));
        }
    }

    private void onPermissionsResult(Map<String, Boolean> results) {
        boolean audioGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        if (audioGranted) {
            startVoiceService();
        } else {
            statusText.setText("Permiso de micrófono denegado");
        }
    }

    private void startVoiceService() {
        Intent intent = new Intent(this, VoiceService.class);
        intent.setAction(VoiceService.ACTION_START);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        isServiceRunning = true;
        recognizedText.setText("");
        commandText.setText("");
        updateUI();
    }

    private void stopVoiceService() {
        Intent intent = new Intent(this, VoiceService.class);
        intent.setAction(VoiceService.ACTION_STOP);
        startService(intent);

        isServiceRunning = false;
        updateUI();
    }

    private void updateUI() {
        if (isServiceRunning) {
            statusText.setText("Escuchando...");
            toggleButton.setText("Detener");
        } else {
            statusText.setText("Servicio detenido");
            toggleButton.setText("Iniciar");
        }

        // Mostrar/ocultar botón de permiso de notificaciones
        if (isNotificationAccessEnabled()) {
            notificationPermissionButton.setVisibility(View.GONE);
        } else {
            notificationPermissionButton.setVisibility(View.VISIBLE);
        }

        // Mostrar/ocultar botón de optimización de batería
        if (isBatteryOptimizationIgnored()) {
            batteryOptimizationButton.setVisibility(View.GONE);
        } else {
            batteryOptimizationButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Verifica si la app tiene permiso para leer notificaciones.
     */
    private boolean isNotificationAccessEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Abre los ajustes de acceso a notificaciones.
     */
    private void openNotificationAccessSettings() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
    }

    /**
     * Verifica si la app está excluida de la optimización de batería.
     */
    private boolean isBatteryOptimizationIgnored() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return false;
    }

    /**
     * Solicita exclusión de la optimización de batería.
     */
    private void requestBatteryOptimizationExclusion() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }
}
