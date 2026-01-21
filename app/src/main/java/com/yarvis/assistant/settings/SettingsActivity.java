package com.yarvis.assistant.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
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
import com.yarvis.assistant.network.WebSocketService;
import com.yarvis.assistant.network.YarvisWebSocketClient;

/**
 * Activity para configurar la conexión al backend.
 * Usa WebSocketService para mantener la conexión persistente.
 */
public class SettingsActivity extends AppCompatActivity implements
        WebSocketService.ConnectionStateListener,
        YarvisWebSocketClient.ConnectionListener {

    private ServerConfig serverConfig;
    private WebSocketService webSocketService;
    private boolean serviceBound = false;

    // Views de configuración
    private TextInputEditText agentNameInput;
    private TextInputEditText backendUrlInput;
    private TextInputEditText backendPasswordInput;
    private SwitchCompat connectSwitch;
    private android.view.View connectionIndicator;
    private TextView connectionStatus;
    private Button saveConfigButton;

    // Views de entorno
    private SwitchCompat productionModeSwitch;
    private TextView currentEnvLabel;

    // Views de cambio de contraseña
    private TextInputEditText currentPasswordInput;
    private TextInputEditText newPasswordInput;
    private TextInputEditText confirmPasswordInput;
    private Button changePasswordButton;

    private boolean isConnected = false;
    private boolean isAuthenticated = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            WebSocketService.LocalBinder localBinder = (WebSocketService.LocalBinder) binder;
            webSocketService = localBinder.getService();
            serviceBound = true;

            // Registrar listeners
            webSocketService.addConnectionStateListener(SettingsActivity.this);
            webSocketService.addMessageListener(SettingsActivity.this);

            // Actualizar UI con estado actual
            updateConnectionUI(webSocketService.isAuthenticated(),
                    webSocketService.isAuthenticated() ? getString(R.string.status_connected) :
                            (webSocketService.isConnected() ? getString(R.string.status_connecting) :
                                    getString(R.string.status_disconnected)));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            webSocketService = null;
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        serverConfig = new ServerConfig(this);
        initViews();
        loadCurrentConfig();
        setupListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind al WebSocketService
        Intent intent = new Intent(this, WebSocketService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serviceBound && webSocketService != null) {
            webSocketService.removeConnectionStateListener(this);
            webSocketService.removeMessageListener(this);
            unbindService(serviceConnection);
            serviceBound = false;
        }
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

        // Entorno
        productionModeSwitch = findViewById(R.id.production_mode_switch);
        currentEnvLabel = findViewById(R.id.current_env_label);

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
        productionModeSwitch.setChecked(serverConfig.isProductionMode());
        updateEnvLabel(serverConfig.isProductionMode());

        updateConnectionUI(false, getString(R.string.status_disconnected));
    }

    private void setupListeners() {
        saveConfigButton.setOnClickListener(v -> saveConfiguration());
        changePasswordButton.setOnClickListener(v -> changePassword());

        connectSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                connectToBackend();
            } else {
                disconnectFromBackend();
            }
        });

        productionModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            serverConfig.setProductionMode(isChecked);
            updateEnvLabel(isChecked);
            // Actualizar el servicio en tiempo real
            if (serviceBound && webSocketService != null) {
                webSocketService.updateProductionMode(isChecked);
            }
        });
    }

    private void updateEnvLabel(boolean production) {
        currentEnvLabel.setText(production ? R.string.settings_env_prod : R.string.settings_env_dev);
    }

    private void saveConfiguration() {
        String agentName = getText(agentNameInput);
        String backendUrl = getText(backendUrlInput);
        String password = getText(backendPasswordInput);
        boolean enabled = connectSwitch.isChecked();

        // Guardar configuración
        serverConfig.setAgentName(agentName);
        serverConfig.setServerUrl(backendUrl);
        serverConfig.setPassword(password);
        serverConfig.setEnabled(enabled);

        Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show();

        // Actualizar el servicio con la nueva configuración
        if (serviceBound && webSocketService != null) {
            webSocketService.updateCredentials(backendUrl, password, agentName, enabled);
        }
    }

    private void connectToBackend() {
        String url = getText(backendUrlInput);
        String password = getText(backendPasswordInput);

        if (url.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "URL y contraseña son requeridos", Toast.LENGTH_SHORT).show();
            connectSwitch.setChecked(false);
            return;
        }

        updateConnectionUI(false, getString(R.string.status_connecting));

        // Guardar configuración y reconectar
        saveConfiguration();
    }

    private void disconnectFromBackend() {
        serverConfig.setEnabled(false);

        if (serviceBound && webSocketService != null) {
            webSocketService.disconnect();
        }

        isConnected = false;
        isAuthenticated = false;
        updateConnectionUI(false, getString(R.string.status_disconnected));
        changePasswordButton.setEnabled(false);
    }

    private void changePassword() {
        if (!isAuthenticated || !serviceBound || webSocketService == null) {
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

        YarvisWebSocketClient client = webSocketService.getWebSocketClient();
        if (client != null) {
            client.changePassword(currentPassword, newPassword);
        }
    }

    private void updateConnectionUI(boolean authenticated, String statusText) {
        runOnUiThread(() -> {
            isAuthenticated = authenticated;
            connectionStatus.setText(statusText);

            int color = authenticated
                    ? ContextCompat.getColor(this, R.color.success)
                    : ContextCompat.getColor(this, R.color.error);

            GradientDrawable drawable = (GradientDrawable) connectionIndicator.getBackground();
            drawable.setColor(color);

            changePasswordButton.setEnabled(authenticated);
        });
    }

    private String getText(TextInputEditText input) {
        return input.getText() != null ? input.getText().toString().trim() : "";
    }

    // ==================== WebSocketService.ConnectionStateListener ====================

    @Override
    public void onConnectionStateChanged(boolean connected, boolean authenticated) {
        runOnUiThread(() -> {
            isConnected = connected;
            isAuthenticated = authenticated;

            String status;
            if (authenticated) {
                status = getString(R.string.status_connected);
            } else if (connected) {
                status = getString(R.string.status_connecting);
            } else {
                status = getString(R.string.status_disconnected);
            }

            updateConnectionUI(authenticated, status);
        });
    }

    // ==================== YarvisWebSocketClient.ConnectionListener ====================

    @Override
    public void onConnected() {
        // Manejado por onConnectionStateChanged
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            if (connectSwitch.isChecked()) {
                connectionStatus.setText(getString(R.string.status_connecting));
            }
        });
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
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
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
        runOnUiThread(() -> {
            if (success) {
                updateConnectionUI(true, getString(R.string.status_connected));
                serverConfig.setEnabled(true);
            } else {
                updateConnectionUI(false, getString(R.string.status_auth_failed));
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                connectSwitch.setChecked(false);
            }
        });
    }

    @Override
    public void onPasswordChangeResult(boolean success, String message) {
        runOnUiThread(() -> {
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
        });
    }
}
