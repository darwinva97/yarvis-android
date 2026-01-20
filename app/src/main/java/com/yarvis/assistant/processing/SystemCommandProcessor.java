package com.yarvis.assistant.processing;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.yarvis.assistant.processing.CommandType.SystemCommand;

public class SystemCommandProcessor extends CommandProcessor<SystemCommand> {

    private static final String TAG = "SystemCommandProcessor";

    public SystemCommandProcessor(Context context) {
        super(context);
    }

    @Override
    public String getProcessorName() {
        return "SystemProcessor";
    }

    @Override
    public boolean canHandle(CommandType command) {
        return command instanceof SystemCommand;
    }

    @Override
    protected CommandResult execute(SystemCommand command) throws Exception {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        Log.d(TAG, "Executing system command: " + command.getSetting() + " -> " + command.getOperation());

        switch (command.getSetting()) {
            case WIFI:
                return handleWifi(command);

            case BLUETOOTH:
                return handleBluetooth(command);

            case VOLUME:
                return handleVolume(command);

            case BRIGHTNESS:
                return handleBrightness(command);

            case FLASHLIGHT:
                return handleFlashlight(command);

            case AIRPLANE_MODE:
                return handleAirplaneMode(command);

            default:
                return CommandResult.failure(command.getId(),
                        "Configuración no soportada: " + command.getSetting());
        }
    }

    private CommandResult handleWifi(SystemCommand command) {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent intent = new Intent(Settings.Panel.ACTION_WIFI);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return CommandResult.success(command.getId(), "Abriendo configuración de WiFi");
        }

        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return CommandResult.failure(command.getId(), "WiFi no disponible");
        }

        boolean enable = command.getOperation() == SystemCommand.Operation.ENABLE ||
                (command.getOperation() == SystemCommand.Operation.TOGGLE && !wifiManager.isWifiEnabled());

        wifiManager.setWifiEnabled(enable);
        return CommandResult.success(command.getId(), "WiFi " + (enable ? "activado" : "desactivado"));
    }

    private CommandResult handleBluetooth(SystemCommand command) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return CommandResult.failure(command.getId(), "Bluetooth no disponible");
        }

        boolean enable = command.getOperation() == SystemCommand.Operation.ENABLE ||
                (command.getOperation() == SystemCommand.Operation.TOGGLE && !adapter.isEnabled());

        if (enable) {
            adapter.enable();
        } else {
            adapter.disable();
        }
        return CommandResult.success(command.getId(), "Bluetooth " + (enable ? "activado" : "desactivado"));
    }

    private CommandResult handleVolume(SystemCommand command) {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return CommandResult.failure(command.getId(), "Audio no disponible");
        }

        if (command.getOperation() == SystemCommand.Operation.SET && command.getValue() >= 0) {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int newVolume = (command.getValue() * maxVolume) / 100;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_SHOW_UI);
            return CommandResult.success(command.getId(), "Volumen establecido a " + command.getValue() + "%");
        }

        return CommandResult.failure(command.getId(), "Operación de volumen inválida");
    }

    private CommandResult handleBrightness(SystemCommand command) {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        if (command.getOperation() == SystemCommand.Operation.SET && command.getValue() >= 0) {
            try {
                int brightness = (command.getValue() * 255) / 100;
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, brightness);
                return CommandResult.success(command.getId(), "Brillo establecido a " + command.getValue() + "%");
            } catch (SecurityException e) {
                return CommandResult.failure(command.getId(), "Sin permiso para cambiar brillo", e);
            }
        }

        return CommandResult.failure(command.getId(), "Operación de brillo inválida");
    }

    private CommandResult handleFlashlight(SystemCommand command) {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) {
                return CommandResult.failure(command.getId(), "Cámara no disponible");
            }

            String cameraId = cameraManager.getCameraIdList()[0];
            boolean enable = command.getOperation() == SystemCommand.Operation.ENABLE;
            cameraManager.setTorchMode(cameraId, enable);

            return CommandResult.success(command.getId(), "Linterna " + (enable ? "encendida" : "apagada"));
        } catch (Exception e) {
            return CommandResult.failure(command.getId(), "Error con linterna", e);
        }
    }

    private CommandResult handleAirplaneMode(SystemCommand command) {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        Intent intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return CommandResult.success(command.getId(), "Abriendo configuración de modo avión");
    }
}
