package com.yarvis.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Controla el ciclo de vida del VoiceService.
 */
public class VoiceServiceController {

    private final Context context;
    private boolean isRunning = false;

    public interface StateListener {
        void onServiceStateChanged(boolean isRunning);
    }

    private StateListener stateListener;

    public VoiceServiceController(Context context) {
        this.context = context;
    }

    public void setStateListener(StateListener listener) {
        this.stateListener = listener;
    }

    /**
     * Inicia el VoiceService como foreground service.
     */
    public void start() {
        Intent intent = new Intent(context, VoiceService.class);
        intent.setAction(VoiceService.ACTION_START);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        isRunning = true;
        notifyStateChanged();
    }

    /**
     * Detiene el VoiceService.
     */
    public void stop() {
        Intent intent = new Intent(context, VoiceService.class);
        intent.setAction(VoiceService.ACTION_STOP);
        context.startService(intent);

        isRunning = false;
        notifyStateChanged();
    }

    /**
     * Alterna el estado del servicio.
     */
    public void toggle() {
        if (isRunning) {
            stop();
        } else {
            start();
        }
    }

    /**
     * Sincroniza el estado con el servicio real.
     */
    public void syncState() {
        isRunning = VoiceService.isRunning();
        notifyStateChanged();
    }

    public boolean isRunning() {
        return isRunning;
    }

    private void notifyStateChanged() {
        if (stateListener != null) {
            stateListener.onServiceStateChanged(isRunning);
        }
    }
}
