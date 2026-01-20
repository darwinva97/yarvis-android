package com.yarvis.assistant.processing;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;

import com.yarvis.assistant.processing.CommandType.MediaCommand;

public class MediaCommandProcessor extends CommandProcessor<MediaCommand> {

    private static final String TAG = "MediaCommandProcessor";
    private final AudioManager audioManager;

    public MediaCommandProcessor(Context context) {
        super(context);
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public String getProcessorName() {
        return "MediaProcessor";
    }

    @Override
    public boolean canHandle(CommandType command) {
        return command instanceof MediaCommand;
    }

    @Override
    protected boolean validate(MediaCommand command) {
        if (!super.validate(command)) {
            return false;
        }
        return command.getAction() != null;
    }

    @Override
    protected CommandResult execute(MediaCommand command) throws Exception {
        Context context = getContext();
        if (context == null) {
            return CommandResult.failure(command.getId(), "Context not available");
        }

        Log.d(TAG, "Executing media command: " + command.getAction());

        switch (command.getAction()) {
            case PLAY:
                sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY);
                return CommandResult.success(command.getId(), "Reproduciendo");

            case PAUSE:
                sendMediaButton(KeyEvent.KEYCODE_MEDIA_PAUSE);
                return CommandResult.success(command.getId(), "Pausado");

            case STOP:
                sendMediaButton(KeyEvent.KEYCODE_MEDIA_STOP);
                return CommandResult.success(command.getId(), "Detenido");

            case NEXT:
                sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT);
                return CommandResult.success(command.getId(), "Siguiente pista");

            case PREVIOUS:
                sendMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                return CommandResult.success(command.getId(), "Pista anterior");

            case VOLUME_UP:
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                return CommandResult.success(command.getId(), "Volumen aumentado");

            case VOLUME_DOWN:
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return CommandResult.success(command.getId(), "Volumen reducido");

            default:
                return CommandResult.failure(command.getId(), "Acción no soportada: " + command.getAction());
        }
    }

    private void sendMediaButton(int keyCode) {
        Context context = getContext();
        if (context == null) return;

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        context.sendBroadcast(downIntent);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        context.sendBroadcast(upIntent);
    }

    @Override
    protected void preProcess(MediaCommand command) {
        super.preProcess(command);
        Log.d(TAG, "Preparing media action: " + command.getAction());
    }
}
