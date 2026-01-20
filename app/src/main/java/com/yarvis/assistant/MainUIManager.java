package com.yarvis.assistant;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainUIManager {

    private final TextView statusText;
    private final TextView recognizedText;
    private final TextView commandText;
    private final Button toggleButton;
    private final Button notificationPermissionButton;
    private final Button batteryOptimizationButton;

    public MainUIManager(
            TextView statusText,
            TextView recognizedText,
            TextView commandText,
            Button toggleButton,
            Button notificationPermissionButton,
            Button batteryOptimizationButton
    ) {
        this.statusText = statusText;
        this.recognizedText = recognizedText;
        this.commandText = commandText;
        this.toggleButton = toggleButton;
        this.notificationPermissionButton = notificationPermissionButton;
        this.batteryOptimizationButton = batteryOptimizationButton;
    }

    public void updateServiceState(boolean isRunning) {
        if (isRunning) {
            statusText.setText("Escuchando...");
            toggleButton.setText("Detener");
        } else {
            statusText.setText("Servicio detenido");
            toggleButton.setText("Iniciar");
        }
    }

    public void updatePermissionButtons(boolean notificationAccessEnabled, boolean batteryOptimizationIgnored) {
        notificationPermissionButton.setVisibility(
                notificationAccessEnabled ? View.GONE : View.VISIBLE
        );
        batteryOptimizationButton.setVisibility(
                batteryOptimizationIgnored ? View.GONE : View.VISIBLE
        );
    }

    public void showSpeechResult(String text) {
        recognizedText.setText("\"" + text + "\"");
    }

    public void showSpeechPartial(String text) {
        recognizedText.setText(text + "...");
    }

    public void showCommand(String command) {
        commandText.setText(command);
    }

    public void clearRecognitionTexts() {
        recognizedText.setText("");
        commandText.setText("");
    }

    public void showStatus(String message) {
        statusText.setText(message);
    }
}
