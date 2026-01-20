package com.yarvis.assistant.processing;

import android.content.Context;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class CommandProcessor<T extends CommandType> {

    private static final String TAG = "CommandProcessor";

    private final WeakReference<Context> contextRef;
    protected final Repository<CommandResult> resultRepository;
    protected final Cache<String, CommandResult> resultCache;
    private final ExecutorService executor;

    protected CommandProcessor(Context context) {
        this.contextRef = new WeakReference<>(context);
        this.resultRepository = new Repository<>();
        this.resultCache = new Cache<>(50, 5 * 60 * 1000);
        this.executor = Executors.newSingleThreadExecutor();
    }

    protected final Context getContext() {
        return contextRef.get();
    }

    public final void process(T command, ResultCallback<CommandResult> callback) {
        Log.d(TAG, "Processing command: " + command.getDescription());

        if (!validate(command)) {
            CommandResult result = CommandResult.failure(command.getId(), "Validation failed");
            callback.onResult(result);
            return;
        }

        resultCache.get(command.getId()).ifPresent(cached -> {
            Log.d(TAG, "Cache hit for command: " + command.getId());
            callback.onResult(cached);
            return;
        });

        preProcess(command);

        executor.execute(() -> {
            long startTime = System.currentTimeMillis();
            try {
                CommandResult result = execute(command);

                long executionTime = System.currentTimeMillis() - startTime;
                result = CommandResult.success(
                        command.getId(),
                        result.message(),
                        executionTime
                );

                postProcess(command, result);

                resultCache.put(command.getId(), result);
                resultRepository.add(result);

                callback.onResult(result);

            } catch (Exception e) {
                Log.e(TAG, "Error processing command", e);
                CommandResult errorResult = CommandResult.failure(command.getId(), "Processing error", e);
                onError(command, e);
                callback.onResult(errorResult);
            }
        });
    }

    protected abstract CommandResult execute(T command) throws Exception;

    public abstract String getProcessorName();

    public abstract boolean canHandle(CommandType command);

    protected boolean validate(T command) {
        return command != null && command.getId() != null;
    }

    protected void preProcess(T command) {
        Log.d(TAG, "[" + getProcessorName() + "] Pre-processing: " + command.getId());
    }

    protected void postProcess(T command, CommandResult result) {
        Log.d(TAG, "[" + getProcessorName() + "] Post-processing: " + result);
    }

    protected void onError(T command, Exception error) {
        Log.e(TAG, "[" + getProcessorName() + "] Error processing " + command.getId(), error);
    }

    public Repository<CommandResult> getResultRepository() {
        return resultRepository;
    }

    public void shutdown() {
        executor.shutdown();
        resultCache.clear();
    }
}
