package co.edu.uptc.airport.model;

/**
 * Enumeración de los posibles estados de un avión.
 */
public enum AirplaneState {

    /** Avión esperando para aterrizar */
    WAITING_FYI("Esperando pista"),

    /** Avión actualmente aterrizando o despegando en una pista */
    ON_TRACK("En pista"),

    /** Avión trasladándose hacia la puerta */
    TOWARDS_DOOR("Hacia la puerta"),

    /** Avión estacionado en una puerta de embarque */
    AT_DOOR("En la puerta"),

    /** Avión que completó su ciclo y fue liberado */
    FILLED("Completado"),

    /** Avión bloqueado esperando múltiples recursos (posible deadlock) */
    LOCKED("Bloqueado");

    private final String description;

    AirplaneState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

}
