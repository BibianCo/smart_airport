package co.edu.uptc.airport.model;

import java.time.Instant;
import lombok.Getter;
import lombok.ToString;

/**
 * Modelo que representa un avión en la simulación del aeropuerto.
 * Cada avión actúa como un hilo independiente en el sistema.
 */
@Getter
@ToString
public class Plane {

    private final String idPlane;
    private final String namePlane;

    /**
     * Estado actual del avión.
     * Se usa volatile para garantizar que cuando el hilo del avión cambie el
     * estado,
     * la interfaz web lo vea inmediatamente sin retrasos de caché de CPU.
     */
    private volatile AirplaneState statePlane;

    /** Número de puerta asignada (-1 si ninguna) */
    private volatile int assignedDoor;

    /** Número de pista asignada (-1 si ninguna) */
    private volatile int assignedRunway;

    /** Instante de creación para calcular tiempos de espera si es necesario */
    private final Instant creationPlane;

    public Plane(String idPlane, String namePlane) {
        this.idPlane = idPlane;
        this.namePlane = namePlane;
        // Estado inicial coherente con el nuevo flujo
        this.statePlane = AirplaneState.WAITING_FOR_LANDING;
        this.assignedDoor = -1;
        this.assignedRunway = -1;
        this.creationPlane = Instant.now();
    }

    // --- SETTERS SINCRONIZADOS ---
    // Aunque usamos volatile, los setters se sincronizan para evitar
    // escrituras inconsistentes en momentos de alta carga.

    public synchronized void setStatePlane(AirplaneState statePlane) {
        this.statePlane = statePlane;
    }

    public synchronized void setAssignedDoor(int assignedDoor) {
        this.assignedDoor = assignedDoor;
    }

    public synchronized void setAssignedRunway(int assignedRunway) {
        this.assignedRunway = assignedRunway;
    }

    // --- GETTERS ---

    public String getIdPlane() {
        return idPlane;
    }

    public String getNamePlane() {
        return namePlane;
    }

    public AirplaneState getStatePlane() {
        return statePlane;
    }

    public int getAssignedDoor() {
        return assignedDoor;
    }

    public int getAssignedRunway() {
        return assignedRunway;
    }

    public Instant getCreationPlane() {
        return creationPlane;
    }
}