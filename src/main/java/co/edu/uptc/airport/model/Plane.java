package co.edu.uptc.airport.model;

/**
 * Modelo que representa un avión en la simulación del aeropuerto.
 * Cada avión tiene un identificador único, un estado y referencias a los
 * recursos que ocupa (pista y puerta de embarque).
 *
 * @author Bibian Corredor
 * @author Valentina Vega
 */
public class Plane {
    /** Identificador único del avión (ej: AV-001) */
    private final String idPlane;

    /** Nombre descriptivo o aerolínea del avión */
    private final String namePlane;

    /** Estado actual del avión en el sistema */
    private volatile AirplaneState statePlane;

    /** Número de puerta asignada (-1 si no tiene puerta) */
    private volatile int assignedDoor;

    /** Número de pista asignada (-1 si no tiene pista) */
    private volatile int assignedTrack;

    /** Marca de tiempo de creación del avión */
    private final long creationTime;

    /** Marca de tiempo del último cambio de estado */
    private volatile long lastStateChangeTime;

    public Plane(String idPlane, String namePlane) {
        this.idPlane = idPlane;
        this.namePlane = namePlane;
        this.statePlane = AirplaneState.WAITING_FYI; // Estado inicial
        this.assignedDoor = -1; // Sin puerta asignada inicialmente
        this.assignedTrack = -1; // Sin pista asignada inicialmente
        this.creationTime = System.currentTimeMillis();
        this.lastStateChangeTime = this.creationTime;
    }

    public String getIdPlane() {
        return idPlane;
    }

    public String getNamePlane() {
        return namePlane;
    }

    public AirplaneState getStatePlane() {
        return statePlane;
    }

    public synchronized void setStatePlane(AirplaneState statePlane) {
        this.statePlane = statePlane;
        this.lastStateChangeTime = System.currentTimeMillis();
    }

    public int getAssignedDoor() {
        return assignedDoor;
    }

    public synchronized void setAssignedDoor(int assignedDoor) {
        this.assignedDoor = assignedDoor;
    }

    public int getAssignedTrack() {
        return assignedTrack;
    }

    public synchronized void setAssignedTrack(int assignedTrack) {
        this.assignedTrack = assignedTrack;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getLastStateChangeTime() {
        return lastStateChangeTime;
    }

    public void setLastStateChangeTime(long lastStateChangeTime) {
        this.lastStateChangeTime = lastStateChangeTime;
    }

    /**
     * Retorna una representación en JSON del avión para enviarla al frontend.
     *
     * @return String JSON con los datos del avión
     */

    public String toJson() {
        return String.format(
                "{\"idPlane\":\"%s\",\"namePlane\":\"%s\",\"statePlane\":\"%s\",\"statePlaneDescription\":\"%s\",\"assignedDoor\":%d,\"assignedTrack\":%d,\"creationTime\":%d}",
                idPlane, namePlane, statePlane.name(), statePlane.getDescription(), assignedDoor, assignedTrack,
                creationTime);
    }

    @Override
    public String toString() {
        return "Plane [idPlane=" + idPlane + ", namePlane=" + namePlane + ", statePlane=" + statePlane
                + ", assignedDoor=" + assignedDoor + ", assignedTrack=" + assignedTrack + "]";
    }

}
