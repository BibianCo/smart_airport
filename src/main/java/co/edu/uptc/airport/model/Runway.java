package co.edu.uptc.airport.model;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;

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

@Getter
public class Runway {

    /** Número identificador de la pista (base 1) */
    private final int runwayNumber;

    /**
     * Semáforo binario de la pista.
     * {@code new Semaphore(1, true)} = semáforo binario con política FIFO.
     * Implementa exclusión mutua: solo un avión a la vez.
     */
    private final Semaphore semaforo;

    /** Avión que actualmente ocupa la pista (null si libre) */
    private volatile Plane planeActual;

    /** Contador de usos totales de esta pista */
    private volatile int useTotal;

    /**
     * Constructor de la pista.
     *
     * @param numero Número identificador de la pista
     */
    public Runway(int runwayNumber) {
        this.runwayNumber = runwayNumber;
        // fair=true garantiza orden FIFO para evitar inanición
        this.semaforo = new Semaphore(1, true);
        this.planeActual = null;
        this.useTotal = 0;
    }

    /**
     * Asigna un avión a esta pista (llamado después de adquirir el semáforo binario
     * externo).
     *
     * @param plane El avión que ocupa la pista
     */
    public synchronized void assignPlane(Plane plane) {
        this.planeActual = plane;
        this.useTotal++;
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
        semaforo.acquire(); // P(semaforo) — bloquea si valor = 0
        planeActual = plane; // Registra el avión que ocupa la pista
        useTotal++; // Incrementa el contador de usos
    }

    /**
     * Libera la pista (equivale a signal() en semáforo binario).
     * Solo puede liberar quien la adquirió.
     */
    public void release() {
        this.planeActual = null;
        semaforo.release(); // V(semaforo) — despierta siguiente hilo
    }

    /**
     * Verifica si la pista está disponible sin bloquear.
     *
     * @return true si la pista está libre
     */
    public boolean isAvailable() {
        return planeActual == null;
    }

    public int getRunwayNumber() {
        return runwayNumber;
    }

    public Plane getPlaneActual() {
        return planeActual;
    }

    public int getUseTotal() {
        return useTotal;
    }

}
