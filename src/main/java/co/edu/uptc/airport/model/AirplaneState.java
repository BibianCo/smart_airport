package co.edu.uptc.airport.model;

/**
 * Enumeración de los posibles estados de un avión en su ciclo de vida
 * concurrente.
 */
public enum AirplaneState {

    /** Fase 1: Sincronización compuesta (esperando pista y puerta) */
    WAITING_FOR_LANDING("Esperando aterrizaje"),

    /** Fase 2: Sección crítica - Uso de pista para aterrizar */
    LANDING("Aterrizando"),

    /** Fase 3: Traslado tras aterrizar */
    TOWARDS_GATE("Hacia la puerta"),

    /** Fase 4: Recurso limitado - Estacionado en puerta de embarque */
    AT_GATE("En la puerta"),

    /** Fase 5: Esperando pista nuevamente para salir */
    WAITING_FOR_TAKEOFF("Esperando despegue"),

    /** Fase 6: Sección crítica - Uso de pista para despegar */
    TAKEOFF("Despegando"),

    /** Fin: El hilo ha terminado su ejecución exitosamente */
    COMPLETED("Vuelo completado"),

    /** Estado de error o interrupción */
    LOCKED("Bloqueado / Error");

    private final String description;

    AirplaneState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}