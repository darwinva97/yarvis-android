package com.yarvis.assistant.processing;

import android.content.Context;
import android.util.Log;

import com.yarvis.assistant.processing.CommandType.*;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandProcessorManager {

    private static final String TAG = "CommandProcessorManager";

    private static volatile CommandProcessorManager instance;

    private final WeakReference<Context> contextRef;
    private final List<CommandProcessor<?>> processors;
    private final Repository<CommandType> commandHistory;
    private final CommandParser parser;

    private static class CommandParser {

        private static final Pattern MEDIA_PATTERN = Pattern.compile(
                "(reproducir|play|pausar|pause|parar|stop|siguiente|next|anterior|previous|subir volumen|bajar volumen)",
                Pattern.CASE_INSENSITIVE);

        private static final Pattern SYSTEM_PATTERN = Pattern.compile(
                "(activar|desactivar|encender|apagar|toggle)\\s+(wifi|bluetooth|linterna|flashlight|brillo)",
                Pattern.CASE_INSENSITIVE);

        private static final Pattern CALL_PATTERN = Pattern.compile(
                "(llamar|call|marcar)\\s+(?:a\\s+)?(.+)",
                Pattern.CASE_INSENSITIVE);

        private static final Pattern SMS_PATTERN = Pattern.compile(
                "(enviar\\s+(?:sms|mensaje)|message|sms)\\s+(?:a\\s+)?(.+?)(?:\\s+diciendo\\s+(.+))?$",
                Pattern.CASE_INSENSITIVE);

        private static final Pattern TIME_PATTERN = Pattern.compile(
                "(qué hora|hora actual|what time|hora es)",
                Pattern.CASE_INSENSITIVE);

        private static final Pattern DATE_PATTERN = Pattern.compile(
                "(qué día|fecha|what day|qué fecha)",
                Pattern.CASE_INSENSITIVE);

        Optional<CommandType> parse(String text) {
            if (text == null || text.isEmpty()) {
                return Optional.empty();
            }

            String normalizedText = text.toLowerCase().trim();

            Matcher matcher;

            matcher = MEDIA_PATTERN.matcher(normalizedText);
            if (matcher.find()) {
                return Optional.of(parseMediaCommand(matcher.group(1)));
            }

            matcher = SYSTEM_PATTERN.matcher(normalizedText);
            if (matcher.find()) {
                return Optional.of(parseSystemCommand(matcher.group(1), matcher.group(2)));
            }

            matcher = CALL_PATTERN.matcher(normalizedText);
            if (matcher.find()) {
                return Optional.of(new CommunicationCommand(
                        generateId(),
                        CommunicationCommand.CommType.CALL,
                        matcher.group(2).trim(),
                        null
                ));
            }

            matcher = SMS_PATTERN.matcher(normalizedText);
            if (matcher.find()) {
                return Optional.of(new CommunicationCommand(
                        generateId(),
                        CommunicationCommand.CommType.SMS,
                        matcher.group(2).trim(),
                        matcher.groupCount() > 2 ? matcher.group(3) : null
                ));
            }

            matcher = TIME_PATTERN.matcher(normalizedText);
            if (matcher.find()) {
                return Optional.of(new QueryCommand(
                        generateId(),
                        QueryCommand.QueryType.TIME,
                        text
                ));
            }

            matcher = DATE_PATTERN.matcher(normalizedText);
            if (matcher.find()) {
                return Optional.of(new QueryCommand(
                        generateId(),
                        QueryCommand.QueryType.DATE,
                        text
                ));
            }

            return Optional.of(new QueryCommand(
                    generateId(),
                    QueryCommand.QueryType.GENERAL,
                    text
            ));
        }

        private MediaCommand parseMediaCommand(String action) {
            MediaCommand.Action mediaAction;
            switch (action.toLowerCase()) {
                case "reproducir":
                case "play":
                    mediaAction = MediaCommand.Action.PLAY;
                    break;
                case "pausar":
                case "pause":
                    mediaAction = MediaCommand.Action.PAUSE;
                    break;
                case "parar":
                case "stop":
                    mediaAction = MediaCommand.Action.STOP;
                    break;
                case "siguiente":
                case "next":
                    mediaAction = MediaCommand.Action.NEXT;
                    break;
                case "anterior":
                case "previous":
                    mediaAction = MediaCommand.Action.PREVIOUS;
                    break;
                case "subir volumen":
                    mediaAction = MediaCommand.Action.VOLUME_UP;
                    break;
                case "bajar volumen":
                    mediaAction = MediaCommand.Action.VOLUME_DOWN;
                    break;
                default:
                    mediaAction = MediaCommand.Action.PLAY;
            }
            return new MediaCommand(generateId(), mediaAction, null);
        }

        private SystemCommand parseSystemCommand(String operation, String setting) {
            SystemCommand.Setting sysSet;
            switch (setting.toLowerCase()) {
                case "wifi":
                    sysSet = SystemCommand.Setting.WIFI;
                    break;
                case "bluetooth":
                    sysSet = SystemCommand.Setting.BLUETOOTH;
                    break;
                case "linterna":
                case "flashlight":
                    sysSet = SystemCommand.Setting.FLASHLIGHT;
                    break;
                case "brillo":
                    sysSet = SystemCommand.Setting.BRIGHTNESS;
                    break;
                default:
                    sysSet = SystemCommand.Setting.WIFI;
            }

            SystemCommand.Operation sysOp;
            switch (operation.toLowerCase()) {
                case "activar":
                case "encender":
                    sysOp = SystemCommand.Operation.ENABLE;
                    break;
                case "desactivar":
                case "apagar":
                    sysOp = SystemCommand.Operation.DISABLE;
                    break;
                case "toggle":
                    sysOp = SystemCommand.Operation.TOGGLE;
                    break;
                default:
                    sysOp = SystemCommand.Operation.TOGGLE;
            }

            return new SystemCommand(generateId(), sysSet, sysOp);
        }

        private String generateId() {
            return "cmd_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        }
    }

    private CommandProcessorManager(Context context) {
        this.contextRef = new WeakReference<>(context.getApplicationContext());
        this.processors = new ArrayList<>();
        this.commandHistory = new Repository<>();
        this.parser = new CommandParser();

        initializeProcessors(context);
    }

    public static CommandProcessorManager getInstance(Context context) {
        if (instance == null) {
            synchronized (CommandProcessorManager.class) {
                if (instance == null) {
                    instance = new CommandProcessorManager(context);
                }
            }
        }
        return instance;
    }

    private void initializeProcessors(Context context) {
        processors.add(new MediaCommandProcessor(context));
        processors.add(new SystemCommandProcessor(context));
        processors.add(new QueryCommandProcessor(context));
        processors.add(new CommunicationCommandProcessor(context));
        Log.d(TAG, "Initialized " + processors.size() + " command processors");
    }

    @SuppressWarnings("unchecked")
    public void processText(String text, ResultCallback<CommandResult> callback) {
        Log.d(TAG, "Processing text: " + text);

        Optional<CommandType> commandOpt = parser.parse(text);

        if (!commandOpt.isPresent()) {
            callback.onResult(CommandResult.failure("unknown", "No se pudo parsear el comando"));
            return;
        }

        CommandType command = commandOpt.get();
        commandHistory.add(command);

        for (CommandProcessor processor : processors) {
            if (processor.canHandle(command)) {
                Log.d(TAG, "Using processor: " + processor.getProcessorName());
                processor.process(command, callback);
                return;
            }
        }

        callback.onResult(CommandResult.failure(command.getId(),
                "No hay procesador disponible para: " + command.getCategory()));
    }

    @SuppressWarnings("unchecked")
    public void processCommand(CommandType command, ResultCallback<CommandResult> callback) {
        commandHistory.add(command);

        for (CommandProcessor processor : processors) {
            if (processor.canHandle(command)) {
                processor.process(command, callback);
                return;
            }
        }

        callback.onResult(CommandResult.failure(command.getId(), "No processor found"));
    }

    public <R> R processWithVisitor(CommandType command, CommandType.CommandVisitor<R> visitor) {
        return command.accept(visitor);
    }

    public Repository<CommandType> getCommandHistory() {
        return commandHistory;
    }

    public void shutdown() {
        for (CommandProcessor<?> processor : processors) {
            processor.shutdown();
        }
        processors.clear();
        commandHistory.clear();
    }
}
