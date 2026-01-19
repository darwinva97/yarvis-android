package com.yarvis.assistant.settings;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.yarvis.assistant.R;
import com.yarvis.assistant.network.ServerConfig;
import com.yarvis.assistant.network.WebSocketMessage;
import com.yarvis.assistant.network.YarvisWebSocketClient;

/**
 * Activity para configurar la conexión al backend.
 */
public class SettingsActivity extends AppCompatActivity implements YarvisWebSocketClient.ConnectionListener {

    private ServerConfig serverConfig;
    private YarvisWebSocketClient webSocketClient;

    // Views de configuración
    private TextInputEditText agentNameInput;
    private TextInputEditText backendUrlInput;
    private TextInputEditText backendPasswordInput;
    private SwitchCompat connectSwitch;
    private View connectionIndicator;
    private TextView connectionStatus;
    private Button saveConfigButton;

    // Views de cambio de contraseña
    private TextInputEditText currentPasswordInput;
    private TextInputEditText newPasswordInput;
    private TextInputEditText confirmPasswordInput;
    private Button changePasswordButton;

    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        serverConfig = new ServerConfig(this);
        initViews();
        loadCurrentConfig();
        setupListeners();
    }

    private void initViews() {
        // Header
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        // Configuración de conexión
        agentNameInput = findViewById(R.id.agent_name_input);
        backendUrlInput = findViewById(R.id.backend_url_input);
        backendPasswordInput = findViewById(R.id.backend_password_input);
        connectSwitch = findViewById(R.id.connect_switch);
        connectionIndicator = findViewById(R.id.connection_indicator);
        connectionStatus = findViewById(R.id.connection_status);
        saveConfigButton = findViewById(R.id.save_config_button);

        // Cambio de contraseña
        currentPasswordInput = findViewById(R.id.current_password_input);
        newPasswordInput = findViewById(R.id.new_password_input);
        confirmPasswordInput = findViewById(R.id.confirm_password_input);
        changePasswordButton = findViewById(R.id.change_password_button);
    }

    private void loadCurrentConfig() {
        agentNameInput.setText(serverConfig.getAgentName());
        backendUrlInput.setText(serverConfig.getServerUrl());
        backendPasswordInput.setText(serverConfig.getPassword());
        connectSwitch.setChecked(serverConfig.isEnabled());

        updateConnectionUI(false, getString(R.string.status_disconnected));
    }

    private void setupListeners() {
        saveConfigButton.setOnClickListener(v -> saveConfiguration());
        changePasswordButton.setOnClickListener(v -> changePassword());

        connectSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Intentar conectar
                connectToBackend();
            } else {
                // Desconectar
                disconnectFromBackend();
            }
        });
    }

    private void saveConfiguration() {
        String agentName = getText(agentNameInput);
        String backendUrl = getText(backendUrlInput);
        String password = getText(backendPasswordInput);

        serverConfig.setAgentName(agentName);
        serverConfig.setServerUrl(backendUrl);
        serverConfig.setPassword(password);
        serverConfig.setEnabled(connectSwitch.isChecked());

        Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show();

        // Si está habilitado, reconectar con la nueva configuración
        if (connectSwitch.isChecked()) {
            disconnectFromBackend();
            connectToBackend();
        }
    }

    private void connectToBackend() {
        String url = getText(backendUrlInput);
        String password = getText(backendPasswordInput);
        String agentName = getText(agentNameInput);

        if (url.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "URL y contraseña son requeridos", Toast.LENGTH_SHORT).show();
            connectSwitch.setChecked(false);
            return;
        }

        updateConnectionUI(false, getString(R.string.status_connecting));

        // Crear cliente WebSocket
        if (webSocketClient != null) {
            webSocketClient.destroy();
        }

        webSocketClient = new YarvisWebSocketClient(url);
        webSocketClient.setCredentials(password, agentName);
        webSocketClient.setListener(this);
        webSocketClient.connect();
    }

    private void disconnectFromBackend() {
        if (webSocketClient != null) {
            webSocketClient.disconnect();
            webSocketClient.destroy();
            webSocketClient = null;
        }
        isConnected = false;
        updateConnectionUI(false, getString(R.string.status_disconnected));
        changePasswordButton.setEnabled(false);
    }

    private void changePassword() {
        if (!isConnected || webSocketClient == null) {
            Toast.makeText(this, R.string.must_be_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        String currentPassword = getText(currentPasswordInput);
        String newPassword = getText(newPasswordInput);
        String confirmPassword = getText(confirmPasswordInput);

        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, R.string.password_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            Toast.makeText(this, R.string.password_mismatch, Toast.LENGTH_SHORT).show();
            return;
        }

        webSocketClient.changePassword(currentPassword, newPassword);
    }

    private void updateConnectionUI(boolean connected, String statusText) {
        isConnected = connected;
        connectionStatus.setText(statusText);

        int color = connected
                ? ContextCompat.getColor(this, R.color.success)
                : ContextCompat.getColor(this, R.color.error);

        GradientDrawable drawable = (GradientDrawable) connectionIndicator.getBackground();
        drawable.setColor(color);

        changePasswordButton.setEnabled(connected);
    }

    private String getText(TextInputEditText input) {
        return input.getText() != null ? input.getText().toString().trim() : "";
    }

    // YarvisWebSocketClient.ConnectionListener implementation

    @Override
    public void onConnected() {
        // La conexión se establece pero aún no autenticado
        updateConnectionUI(false, getString(R.string.status_connecting));
    }

    @Override
    public void onDisconnected() {
        updateConnectionUI(false, getString(R.string.status_disconnected));
        if (connectSwitch.isChecked()) {
            // Si debería estar conectado pero se desconectó, mostrar estado de reconexión
            connectionStatus.setText(getString(R.string.status_connecting));
        }
    }

    @Override
    public void onResponse(WebSocketMessage.Response response) {
        // No usado en settings
    }

    @Override
    public void onAction(String action, String params) {
        // No usado en settings
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConversationStarted(String sessionId, String greeting, WebSocketMessage.ShowContent show) {
        // No usado en settings
    }

    @Override
    public void onConversationEnded(String sessionId, String farewell, String reason) {
        // No usado en settings
    }

    @Override
    public void onAuthResult(boolean success, String message) {
        if (success) {
            updateConnectionUI(true, getString(R.string.status_connected));
            // Guardar que la conexión está habilitada
            serverConfig.setEnabled(true);
        } else {
            updateConnectionUI(false, getString(R.string.status_auth_failed));
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            connectSwitch.setChecked(false);
        }
    }

    @Override
    public void onPasswordChangeResult(boolean success, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        if (success) {
            // Actualizar la contraseña guardada localmente
            String newPassword = getText(newPasswordInput);
            serverConfig.setPassword(newPassword);
            backendPasswordInput.setText(newPassword);

            // Limpiar campos
            currentPasswordInput.setText("");
            newPasswordInput.setText("");
            confirmPasswordInput.setText("");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocketClient != null) {
            webSocketClient.destroy();
        }
    }
}
