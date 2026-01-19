package com.yarvis.assistant.network;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Configuración del servidor backend.
 */
public class ServerConfig {

    private static final String PREFS_NAME = "yarvis_server_config";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_ENABLED = "backend_enabled";

    // Valores por defecto
    private static final String DEFAULT_SERVER_URL = "ws://192.168.1.100:3000/ws";
    private static final boolean DEFAULT_ENABLED = false;

    private final SharedPreferences prefs;

    public ServerConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Obtiene la URL del servidor WebSocket.
     */
    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    /**
     * Establece la URL del servidor WebSocket.
     */
    public void setServerUrl(String url) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }

    /**
     * Verifica si el backend está habilitado.
     */
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
    }

    /**
     * Habilita o deshabilita el backend.
     */
    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }
}
