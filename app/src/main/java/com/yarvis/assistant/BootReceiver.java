package com.yarvis.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.yarvis.assistant.network.ServerConfig;
import com.yarvis.assistant.network.WebSocketService;

/**
 * Receiver que inicia VoiceService y WebSocketService cuando el dispositivo arranca.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            Log.d(TAG, "Boot completed or package replaced");

            // Iniciar WebSocketService si está habilitado
            ServerConfig serverConfig = new ServerConfig(context);
            if (serverConfig.isEnabled()) {
                Log.d(TAG, "Starting WebSocketService");
                Intent wsIntent = new Intent(context, WebSocketService.class);
                wsIntent.setAction(WebSocketService.ACTION_START);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(wsIntent);
                } else {
                    context.startService(wsIntent);
                }
            }

            // Iniciar VoiceService
            Log.d(TAG, "Starting VoiceService");
            Intent serviceIntent = new Intent(context, VoiceService.class);
            serviceIntent.setAction(VoiceService.ACTION_START);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
