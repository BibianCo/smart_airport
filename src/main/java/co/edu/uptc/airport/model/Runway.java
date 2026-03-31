package co.edu.uptc.airport.model;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Modelo que representa una pista de aterrizaje/despegue.
 * Cada pista es un recurso crítico controlado por un semáforo binario
 * (implementado aquí como ReentrantLock) para garantizar exclusión mutua.
 *
 * Solo UN avión puede usar una pista a la vez.
 *
 * @author Bibian Corredor
 * @author Valentina Vega
 * @version 1.0
 */
public class Runway {

    /** Número identificador de la pista (base 1) */
    private final int runwayNumber;

    /**
     * Lock que implementa el semáforo binario de la pista.
     * ReentrantLock permite verificar si está ocupada y registrar quién la ocupa.
     */
    private final ReentrantLock lock;

    /** Avión que actualmente ocupa la pista (null si libre) */
    private volatile Plane occupiedPlane;

    /** Contador de usos totales de esta pista */
    private volatile int usageCount;

    /**
     * Constructor de la pista.
     *
     * @param numero Número identificador de la pista
     */
    public Runway(int runwayNumber) {
        this.runwayNumber = runwayNumber;
        // fair=true garantiza orden FIFO para evitar inanición
        this.lock = new ReentrantLock(true);
        this.occupiedPlane = null;
        this.usageCount = 0;
    }

    /**
     * Asigna un avión a esta pista (llamado después de adquirir el semáforo binario
     * externo).
     *
     * @param plane El avión que ocupa la pista
     */
    public synchronized void assignPlane(Plane plane) {
        this.occupiedPlane = plane;
        this.usageCount++;
    }

    /**
     * Intenta adquirir la pista para un avión (equivale a wait() en semáforo
     * binario).
     * Bloquea el hilo hasta que la pista esté libre.
     *
     * @param plane El avión que solicita la pista
     * @throws InterruptedException si el hilo es interrumpido mientras espera
     */
    public void acquire(Plane plane) throws InterruptedException {
        lock.lockInterruptibly(); // Bloquea hasta que la pista esté libre o el hilo sea interrumpido
        occupiedPlane = plane; // Registra el avión que ocupa la pista
        usageCount++; // Incrementa el contador de usos
    }

    /**
     * Libera la pista (equivale a signal() en semáforo binario).
     * Solo puede liberar quien la adquirió.
     */
    public void release() {
        occupiedPlane = null; // Marca la pista como libre
        if (lock.isHeldByCurrentThread()) {
            lock.unlock(); // Desbloquea para que otros aviones puedan adquirirla
        }
    }

    /**
     * Verifica si la pista está disponible sin bloquear.
     *
     * @return true si la pista está libre
     */
    public boolean isAvailable() {
        return !lock.isLocked();
    }

    public int getRunwayNumber() {
        return runwayNumber;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public Plane getOccupiedPlane() {
        return occupiedPlane;
    }

    public int getUsageCount() {
        return usageCount;
    }

    /**
     * Retorna representación JSON de la pista.
     *
     * @return String JSON
     */
    public String toJson() {
        String palneJson = occupiedPlane != null
                ? String.format("{\"id\":\"%s\",\"name\":\"%s\"}", occupiedPlane.getIdPlane(),
                        occupiedPlane.getNamePlane())
                : "null";
        return String.format(
                "{\"runwayNumber\":%d,\"isAvailable\":%b,\"occupiedPlane\":%s,\"usageCount\":%d}",
                runwayNumber, isAvailable(), palneJson, usageCount);
    }

    @Override
    public String toString() {
        return String.format("Runway{runwayNumber=%d, isAvailable=%b, occupiedPlane=%s}",
                runwayNumber, isAvailable(), occupiedPlane != null ? occupiedPlane.getIdPlane() : "none",
                usageCount);
    }

}
