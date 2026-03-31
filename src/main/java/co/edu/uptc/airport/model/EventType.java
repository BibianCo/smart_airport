package co.edu.uptc.airport.model;

/**
 * Tipos de eventos que pueden ocurrir en el aeropuerto.
 */
public enum EventType {

    INFO,
    RUNWAY_OCCUPIED,
    RUNWAY_CLEARED,
    GATE_OCCUPIED,
    GATE_CLEARED,
    PLANE_COMPLETED,
    RACE_CONDITION,
    DEADLOCK_DETECTED,
    DEADLOCK_PREVENTION,
    ERROR

}
