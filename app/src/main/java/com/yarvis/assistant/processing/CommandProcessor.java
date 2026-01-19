package com.yarvis.assistant.processing;

import android.content.Context;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Clase abstracta base para procesadores de comandos.
 *
 * Demuestra: CLASES ABSTRACTAS, MÉTODOS ABSTRACTOS, MÉTODOS TEMPLATE
 *
 * Esta clase define el esqueleto del algoritmo de procesamiento (Template Method Pattern)
 * y deja que las subclases implementen los pasos específicos.
 */
public abstract class CommandProcessor<T extends CommandType> {

    private static final String TAG = "CommandProcessor";

    // WeakReference para evitar memory leaks con Context
    private final WeakReference<Context> contextRef;
    protected final Repository<CommandResult> resultRepository;
    protected final Cache<String, CommandResult> resultCache;
    private final ExecutorService executor;

    /**
     * Constructor protegido - solo subclases pueden instanciar.
     * Demuestra: ENCAPSULAMIENTO con constructor protegido
     */
    protected CommandProcessor(Context context) {
        this.contextRef = new WeakReference<>(context);
        this.resultRepository = new Repository<>();
        this.resultCache = new Cache<>(50, 5 * 60 * 1000); // 5 min TTL
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Obtiene el contexto de forma segura.
     */
    protected final Context getContext() {
        return contextRef.get();
    }

    // =========================================================================
    // TEMPLATE METHOD PATTERN
    // =========================================================================

    /**
     * Método template que define el algoritmo de procesamiento.
     * Demuestra: TEMPLATE METHOD PATTERN - el algoritmo está definido aquí,
     * pero los pasos específicos son implementados por las subclases.
     */
    public final void process(T command, ResultCallback<CommandResult> callback) {
        Log.d(TAG, "Processing command: " + command.getDescription());

        // Paso 1: Validar (puede ser sobrescrito)
        if (!validate(command)) {
            CommandResult result = CommandResult.failure(command.getId(), "Validation failed");
            callback.onResult(result);
            return;
        }

        // Paso 2: Verificar caché
        resultCache.get(command.getId()).ifPresent(cached -> {
            Log.d(TAG, "Cache hit for command: " + command.getId());
            callback.onResult(cached);
            return;
        });

        // Paso 3: Pre-procesamiento (hook - puede ser sobrescrito)
        preProcess(command);

        // Paso 4: Ejecutar de forma asíncrona
        executor.execute(() -> {
            long startTime = System.currentTimeMillis();
            try {
                // Paso 5: Ejecución específica (ABSTRACTO - debe ser implementado)
                CommandResult result = execute(command);

                // Agregar tiempo de ejecución
                long executionTime = System.currentTimeMillis() - startTime;
                result = CommandResult.success(
                        command.getId(),
                        result.message(),
                        executionTime
                );

                // Paso 6: Post-procesamiento (hook)
                postProcess(command, result);

                // Guardar en caché y repositorio
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

    // =========================================================================
    // MÉTODOS ABSTRACTOS - deben ser implementados por subclases
    // =========================================================================

    /**
     * Ejecuta el comando específico.
     * Demuestra: MÉTODO ABSTRACTO
     */
    protected abstract CommandResult execute(T command) throws Exception;

    /**
     * Retorna el nombre del procesador.
     */
    public abstract String getProcessorName();

    /**
     * Verifica si este procesador puede manejar el comando dado.
     */
    public abstract boolean canHandle(CommandType command);

    // =========================================================================
    // HOOKS - métodos que pueden ser sobrescritos (pero no son obligatorios)
    // =========================================================================

    /**
     * Valida el comando antes de procesarlo.
     * Demuestra: HOOK METHOD - implementación por defecto que puede sobrescribirse
     */
    protected boolean validate(T command) {
        return command != null && command.getId() != null;
    }

    /**
     * Hook llamado antes del procesamiento.
     */
    protected void preProcess(T command) {
        Log.d(TAG, "[" + getProcessorName() + "] Pre-processing: " + command.getId());
    }

    /**
     * Hook llamado después del procesamiento exitoso.
     */
    protected void postProcess(T command, CommandResult result) {
        Log.d(TAG, "[" + getProcessorName() + "] Post-processing: " + result);
    }

    /**
     * Hook llamado cuando ocurre un error.
     */
    protected void onError(T command, Exception error) {
        Log.e(TAG, "[" + getProcessorName() + "] Error processing " + command.getId(), error);
    }

    // =========================================================================
    // MÉTODOS UTILITARIOS
    // =========================================================================

    /**
     * Obtiene el historial de resultados.
     */
    public Repository<CommandResult> getResultRepository() {
        return resultRepository;
    }

    /**
     * Limpia recursos.
     */
    public void shutdown() {
        executor.shutdown();
        resultCache.clear();
    }
}
