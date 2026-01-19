package com.yarvis.assistant.processing;

/**
 * Interfaz que define entidades identificables.
 * Utilizada como restricción de tipo (bound) para genéricos.
 *
 * Demuestra: INTERFACES como contratos y BOUNDED TYPE PARAMETERS
 */
public interface Identifiable {

    /**
     * Obtiene el identificador único de la entidad.
     * @return ID único como String
     */
    String getId();
}
