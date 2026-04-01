package co.edu.uptc.airport.model;

import lombok.Getter;

/**
 * Modelo que representa una puerta de embarque.
 * Las puertas son recursos limitados gestionados mediante un semáforo
 * de conteo en el AeropuertoService. Cada puerta puede estar libre u ocupada
 * por exactamente un avión.
 *
 * @author Bibian Corredor
 * @author Valentina Vega
 * @version 1.0
 */

@Getter
public class Gate {
    /** Número identificador de la puerta (base 1) */
    private final int gateNumber;

    /** Avión que actualmente ocupa la puerta (null si libre) */
    private volatile Plane occupiedPlane;

    /** Contador de usos totales de esta puerta */
    private volatile int usageCount;

    /**
     * Constructor de la puerta.
     *
     * @param gateNumber Número identificador de la puerta
     */
    public Gate(int gateNumber) {
        this.gateNumber = gateNumber;
        this.occupiedPlane = null;
        this.usageCount = 0;
    }

    /**
     * Asigna un avión a esta puerta.
     * La sincronización real se gestiona con el semáforo de conteo en
     * AeropuertoService.
     *
     * @param plane El avión que ocupa la puerta
     */
    public synchronized void assign(Plane plane) {
        this.occupiedPlane = plane;
        this.usageCount++;
    }

    /**
     * Libera la puerta.
     */
    public synchronized void release() {
        this.occupiedPlane = null;
    }

    /**
     * Indica si la puerta está disponible.
     *
     * @return true si no hay avión en la puerta
     */
    public boolean isAvailable() {
        return this.occupiedPlane == null;
    }

    public int getGateNumber() {
        return gateNumber;
    }

    public Plane getOccupiedPlane() {
        return occupiedPlane;
    }

    public int getUsageCount() {
        return usageCount;
    }

}
