package co.edu.uptc.airport.model;

import java.time.Instant;

import lombok.Getter;
import lombok.ToString;

/**
 * Modelo que representa un avión en la simulación del aeropuerto.
 * Cada avión tiene un identificador único, un estado y referencias a los
 * recursos que ocupa (pista y puerta de embarque).
 *
 * @author Bibian Corredor
 * @author Valentina Vega
 */

@Getter
@ToString
public class Plane {
    /** Identificador único del avión (ej: AV-001) */
    private final String idPlane;

    /** Nombre descriptivo o aerolínea del avión */
    private final String namePlane;

    /**
     * Estado actual del avión en el sistema
     * se utiliza volatile para asegurar visibilidad entre hilos sin necesidad de
     * sincronización adicional en getters
     */
    private volatile AirplaneState statePlane;

    /** Número de puerta asignada (-1 si no tiene puerta) */
    private volatile int assignedDoor;

    /** Número de pista asignada (-1 si no tiene pista) */
    private volatile int assignedRunway;

    /** instante de creación del avión */
    private final Instant creationPlane;

    public Plane(String idPlane, String namePlane) {
        this.idPlane = idPlane;
        this.namePlane = namePlane;
        this.statePlane = AirplaneState.WAITING_FYI; // Estado inicial
        this.assignedDoor = -1; // Sin puerta asignada inicialmente
        this.assignedRunway = -1; // Sin pista asignada inicialmente
        this.creationPlane = Instant.now();
    }

    public String getIdPlane() {
        return idPlane;
    }

    public String getNamePlane() {
        return namePlane;
    }

    public Instant getCreationPlane() {
        return creationPlane;
    }

    public AirplaneState getStatePlane() {
        return statePlane;
    }

    public synchronized void setStatePlane(AirplaneState statePlane) {
        this.statePlane = statePlane;
    }

    public int getAssignedDoor() {
        return assignedDoor;
    }

    public synchronized void setAssignedDoor(int assignedDoor) {
        this.assignedDoor = assignedDoor;
    }

    public int getAssignedRunway() {
        return assignedRunway;
    }

    public synchronized void setAssignedRunway(int assignedTrack) {
        this.assignedRunway = assignedTrack;
    }

}
