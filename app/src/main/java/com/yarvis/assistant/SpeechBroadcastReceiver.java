package com.yarvis.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/**
 * Receiver para broadcasts del VoiceService.
 */
public class SpeechBroadcastReceiver extends BroadcastReceiver {

    public interface SpeechListener {
        void onSpeechResult(String text);
        void onSpeechPartial(String text);
        void onCommandDetected(String command);
    }

    private SpeechListener listener;

    public void setListener(SpeechListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (listener == null) return;

        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case VoiceService.ACTION_SPEECH_RESULT:
                String result = intent.getStringExtra(VoiceService.EXTRA_TEXT);
                if (result != null) {
                    listener.onSpeechResult(result);
                }
                break;

            case VoiceService.ACTION_SPEECH_PARTIAL:
                String partial = intent.getStringExtra(VoiceService.EXTRA_TEXT);
                if (partial != null) {
                    listener.onSpeechPartial(partial);
                }
                break;

            case VoiceService.ACTION_COMMAND_DETECTED:
                String command = intent.getStringExtra(VoiceService.EXTRA_COMMAND);
                if (command != null) {
                    listener.onCommandDetected(command);
                }
                break;
        }
    }

    /**
     * Crea el IntentFilter para los broadcasts del VoiceService.
     */
    public static IntentFilter createIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(VoiceService.ACTION_SPEECH_RESULT);
        filter.addAction(VoiceService.ACTION_SPEECH_PARTIAL);
        filter.addAction(VoiceService.ACTION_COMMAND_DETECTED);
        return filter;
    }

    /**
     * Registra el receiver en el contexto dado.
     */
    public void register(Context context) {
        IntentFilter filter = createIntentFilter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(this, filter);
        }
    }

    /**
     * Des-registra el receiver del contexto.
     */
    public void unregister(Context context) {
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }
    }
}
