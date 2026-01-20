package com.yarvis.assistant.network;

import android.content.Context;
import android.content.SharedPreferences;

public class ServerConfig {

    private static final String PREFS_NAME = "yarvis_server_config";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_ENABLED = "backend_enabled";
    private static final String KEY_AGENT_NAME = "agent_name";
    private static final String KEY_PASSWORD = "backend_password";

    private static final String DEFAULT_SERVER_URL = "ws://192.168.18.21:3000/ws";
    private static final boolean DEFAULT_ENABLED = false;
    private static final String DEFAULT_AGENT_NAME = "Yarvis";
    private static final String DEFAULT_PASSWORD = "PasswordJarvis2026!";

    private final SharedPreferences prefs;

    public ServerConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    public void setServerUrl(String url) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public String getAgentName() {
        return prefs.getString(KEY_AGENT_NAME, DEFAULT_AGENT_NAME);
    }

    public void setAgentName(String name) {
        prefs.edit().putString(KEY_AGENT_NAME, name).apply();
    }

    public String getPassword() {
        return prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD);
    }

    public void setPassword(String password) {
        prefs.edit().putString(KEY_PASSWORD, password).apply();
    }

    public boolean isConfigured() {
        String url = getServerUrl();
        String password = getPassword();
        return url != null && !url.isEmpty() && password != null && !password.isEmpty();
    }
}
