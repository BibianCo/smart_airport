package co.edu.uptc.airport.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import co.edu.uptc.airport.config.AirportProperties;
import co.edu.uptc.airport.model.*;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio principal del aeropuerto que gestiona todos los recursos
 * concurrentes
 * dentro de la simulación de un aeropuerto inteligente.
 *
 * <h3>Mecanismos de sincronización utilizados:</h3>
 * <ul>
 * <li><b>Semáforo binario (runwaySemaphores):</b> Implementado con
 * {@code Semaphore(1, true)} por cada pista. Garantiza exclusión mutua,
 * permitiendo que solo un avión use una pista a la vez.</li>
 *
 * <li><b>Semáforo de conteo (gateSemaphore):</b> Controla cuántos aviones
 * pueden
 * ocupar puertas simultáneamente mediante
 * {@code Semaphore(numGates, true)}.</li>
 *
 * <li><b>Sincronización compuesta:</b> Un avión sigue un orden global de
 * adquisición de recursos: primero pista y luego puerta, evitando
 * deadlocks.</li>
 *
 * <li><b>CopyOnWriteArrayList:</b> Estructura thread-safe utilizada para el
 * almacenamiento concurrente de aviones y eventos sin necesidad de
 * sincronización explícita en lectura.</li>
 * </ul>
 *
 * <h3>Prevención de deadlocks:</h3>
 * Se establece un orden global en la adquisición de recursos:
 * <ol>
 * <li>Primero pista</li>
 * <li>Luego puerta</li>
 * </ol>
 * Esto elimina la condición de espera circular.
 *
 * <h3>Adaptación a Spring Boot:</h3>
 * Esta clase está anotada con {@code @Service}, permitiendo su inyección en
 * controladores REST y facilitando la integración con una interfaz web.
 *
 * @author Bibian Corredor
 * @author Valentina Vega
 * @version 2.0
 */
@Slf4j
@Service
public class AirportService {

    private final AirportProperties properties;

    /** Lista de pistas */
    @Getter
    private List<Runway> runways;

    /** Lista de puertas */
    @Getter
    private List<Gate> gates;

    /**
     * Semáforo de conteo para puertas.
     * Permite que hasta {@code numGates} aviones ocupen puertas simultáneamente.
     */
    private Semaphore gateSemaphore;

    /**
     * Semáforos binarios para pistas.
     * Cada pista tiene un semáforo independiente que garantiza exclusión mutua.
     */
    private List<Semaphore> runwaySemaphores;

    /** Lista de aviones en el sistema (thread-safe) */
    @Getter
    private CopyOnWriteArrayList<Plane> planes;

    /** Log de eventos concurrentes */
    @Getter
    private CopyOnWriteArrayList<EventoLog> logEventos;

    /**
     * Contador atómico de aviones.
     * Garantiza IDs únicos en entorno concurrente.
     */
    private AtomicInteger planeCounter;

    /** Indica si la simulación está activa */
    @Getter
    private volatile boolean simulationActive;

    /** Recurso para demostrar condición de carrera (SIN protección) */
    private int unsafeResource;

    /** Recurso con exclusión mutua correcta */
    private int secureResource;
    private final Object lockSeguro = new Object();

    public AirportService(AirportProperties properties) {
        this.properties = properties;
    }

    /**
     * Inicialización post-construcción (Spring llama esto después de inyectar
     * dependencias).
     * Se usa {@code @PostConstruct} en lugar del constructor para poder acceder a
     * las propiedades ya inyectadas.
     */
    @PostConstruct
    public void init() {
        this.planes = new CopyOnWriteArrayList<>();
        this.logEventos = new CopyOnWriteArrayList<>();
        this.planeCounter = new AtomicInteger(0);
        this.simulationActive = false;

        // Inicializar pistas con semáforos binarios
        this.runways = new ArrayList<>();
        for (int i = 1; i <= properties.getNumRunways(); i++) {
            runways.add(new Runway(i));
        }

        // Inicializar puertas
        this.gates = new ArrayList<>();
        for (int i = 1; i <= properties.getNumGates(); i++) {
            gates.add(new Gate(i));
        }

        // Semáforo de conteo para puertas (fair=true → FIFO)
        this.gateSemaphore = new Semaphore(properties.getNumGates(), true);

        log.info("Aeropuerto inicializado con {} pistas y {} puertas",
                properties.getNumRunways(), properties.getNumGates());

        registerEvent(EventType.INFO, String.format("Sistema listo: %d pistas, %d puertas de embarque",
                properties.getNumRunways(), properties.getNumGates()), null);

    }

    // GESTIÓN DE AVIONES

    /**
     * Agrega un avión al sistema y lanza su ejecución como hilo independiente.
     *
     * @param nombre Nombre o aerolínea del avión
     * @return avión creado
     */
    public Plane addPlane(String nombre) {

        String planeId = String.format("AV-%03d", planeCounter.incrementAndGet());
        Plane plane = new Plane(planeId, nombre.trim().isEmpty() ? "Aerolínea Desconocida" : nombre.trim());

        planes.add(plane);
        registerEvent(EventType.INFO, "Avión ingresó al sistema", plane.getIdPlane());
        log.info("Nuevo avión: {} ({})", plane.getIdPlane(), nombre);

        Thread planeThread = new Thread(() -> airplaneCycle(plane), "Hilo-" + plane.getIdPlane());
        planeThread.setDaemon(true);
        planeThread.start();

        return plane;
    }

    /**
     * Ciclo de vida de un avión.
     *
     * Flujo:
     * <ol>
     * <li>Solicitar pista</li>
     * <li>Usar pista</li>
     * <li>Liberar pista</li>
     * <li>Solicitar puerta</li>
     * <li>Usar puerta</li>
     * <li>Liberar puerta</li>
     * </ol>
     *
     * @param plane avión en ejecución
     */
    public void airplaneCycle(Plane plane) {

        try {
            // Fase 1: Solicitar pista
            plane.setStatePlane(AirplaneState.WAITING_FYI);
            registerEvent(EventType.INFO, "Solicitando pista", plane.getIdPlane());

            Runway runway = findAndAcquireRunway(plane);
            plane.setAssignedRunway(runway.getRunwayNumber());

            // Fase 2: Uso de pista
            plane.setStatePlane(AirplaneState.ON_RUNWAY);
            registerEvent(EventType.RUNWAY_OCCUPIED,
                    "Usando pista " + runway.getRunwayNumber(),
                    plane.getIdPlane());

            int runwayTime = randomTime(properties.getTiempoMinRunwayMs(), properties.getTiempoMaxRunwayMs());
            Thread.sleep(runwayTime);

            // Fase 3: Liberar pista
            runway.release();
            plane.setAssignedRunway(-1);

            registerEvent(EventType.RUNWAY_CLEARED, "Pista liberada", plane.getIdPlane());

            // Fase 4: Solicitar puerta
            plane.setStatePlane(AirplaneState.TOWARDS_DOOR);
            registerEvent(EventType.INFO, "Solicitando puerta de embarque", plane.getIdPlane());

            /*
             * acquire() del semáforo de conteo:
             * - Si hay puertas libres (valor > 0): pasa inmediatamente
             * - Si todas ocupadas (valor = 0): bloquea hasta que algún
             * avión haga release()
             */
            gateSemaphore.acquire();

            Gate gate = findAvailableGate(plane);
            plane.setAssignedDoor(gate.getGateNumber());

            // Fase 5: Uso de puerta
            plane.setStatePlane(AirplaneState.AT_DOOR);
            registerEvent(EventType.GATE_OCCUPIED, "En puerta " + gate.getGateNumber(), plane.getIdPlane());

            int gateTime = randomTime(properties.getTiempoMinGateMs(), properties.getTiempoMaxGateMs());
            Thread.sleep(gateTime);

            // Fase 6: Liberar puerta
            gate.release();
            plane.setAssignedDoor(-1);
            gateSemaphore.release();

            registerEvent(EventType.GATE_CLEARED, "Puerta liberada", plane.getIdPlane());

            // Fase 7: Finalización
            plane.setStatePlane(AirplaneState.FILLED);
            registerEvent(EventType.PLANE_COMPLETED, "Ciclo completado", plane.getIdPlane());
            log.info("Avión {} completó su ciclo", plane.getIdPlane());

        } catch (InterruptedException e) {
            plane.setStatePlane(AirplaneState.LOCKED);
            registerEvent(EventType.ERROR, "Avión interrumpido", plane.getIdPlane());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Busca y adquiere una pista disponible.
     *
     * @return índice de la pista
     * @throws InterruptedException si el hilo es interrumpido
     */
    private Runway findAndAcquireRunway(Plane plane) throws InterruptedException {

        while (true) {
            for (Runway runway : runways) {
                if (runway.getSemaforo().tryAcquire()) {
                    // Semáforo binario adquirido → marcar pista como ocupada
                    runway.acquire(plane);

                    // Corregir doble-adquisición: tryAcquire ya adquirió,
                    // adquirir() hace acquire() interno — usar versión directa:
                    registerEvent(EventType.DEADLOCK_PREVENTION,
                            "Pista " + runway.getRunwayNumber() + " adquirida (orden global aplicado)",
                            plane.getIdPlane());

                    return runway;

                }
            }
            // Si no se pudo adquirir ninguna pista, esperar un poco antes de reintentar
            Thread.sleep(200);
        }
    }

    /**
     * Busca una puerta disponible.
     *
     * @param plane avión a asignar
     * @return puerta asignada
     */
    private synchronized Gate findAvailableGate(Plane plane) {
        for (Gate gate : gates) {
            if (gate.isAvailable()) {
                gate.assign(plane);
                return gate;
            }
        }
        throw new IllegalStateException("No hay puertas disponibles");
    }

    /**
     * Genera un tiempo aleatorio entre dos valores.
     */
    private int randomTime(int min, int max) {
        return min + (int) (Math.random() * (max - min));
    }

    // SIMULACIÓN

    /**
     * Inicia la simulación generando aviones periódicamente.
     */
    public void startSimulation() {

        simulationActive = true;
        registerEvent(EventType.INFO, "Simulación automática iniciada", null);
        log.info("Simulación iniciada");
    }

    /**
     * Detiene la simulación.
     */
    public void stopSimulation() {
        simulationActive = false;
        registerEvent(EventType.INFO, "Simulación automática detenida", null);
        log.info("Simulación detenida");
    }

    /**
     * Reinicia la simulación limpiando todos los recursos.
     */
    public synchronized void resetSimulation() {

        simulationActive = false;
        planes.clear();
        logEventos.clear();
        planeCounter.set(0);
        unsafeResource = 0;
        secureResource = 0;

        // Reiniciar RUNWAYS y GATES
        runways.clear();
        for (int i = 1; i <= properties.getNumRunways(); i++) {
            runways.add(new Runway(i));
        }

        gates.clear();
        for (int i = 1; i <= properties.getNumGates(); i++) {
            gates.add(new Gate(i));
        }

        gateSemaphore = new Semaphore(properties.getNumGates(), true);
        registerEvent(EventType.INFO, "Simulación reiniciada", null);
        log.info("Simulación reiniciada");
    }

    // Condición de carrera

    /**
     * Demuestra una condición de carrera lanzando 5 hilos sobre un recurso
     * sin protección, y otros 5 sobre el mismo recurso con exclusión mutua.
     *
     * <h4>Condición de carrera (read-modify-write no atómico):</h4>
     * 
     * <pre>
     * Hilo A: lee contador = 5
     *                          Hilo B: lee contador = 5  (mismo valor!)
     * Hilo A: escribe 5+1 = 6
     *                          Hilo B: escribe 5+1 = 6  (¡perdemos un incremento!)
     * Resultado: 6 en lugar de 7
     * </pre>
     */

    public void demonstrateRaceCondition() {

        registerEvent(EventType.RACE_CONDITION, "Demostrando condición de carrera", null);

        unsafeResource = 0;
        secureResource = 0;

        int numThreads = 5;
        int iterations = 1000;

        // Hilos sin protección (condición de carrera)
        List<Thread> fenceless = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            final int num = i;
            fenceless.add(new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    int read = unsafeResource; // lectura
                    // Pausa mínima: aumenta probabilidad de que otro hilo lea el mismo valor
                    try {
                        Thread.sleep(0, 1);
                    } catch (InterruptedException ignored) {
                    }
                    unsafeResource = read + 1; // escritura (NO atómica en conjunto)
                }
                registerEvent(EventType.RACE_CONDITION,
                        String.format("Hilo-Sin-%d terminó. Valor sin protección: %d (esperado: %d)",
                                num, unsafeResource, numThreads * iterations),
                        null);
            }, "Carrera-Sin-" + i));
        }

        // Hilos con protección (exclusión mutua)
        List<Thread> fenced = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            final int num = i;
            fenced.add(new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    synchronized (lockSeguro) {
                        secureResource++; // operación atómica gracias a la sincronización
                    }
                }
                registerEvent(EventType.INFO,
                        String.format("Hilo-Con-%d terminó. Valor con protección: %d (esperado: %d)",
                                num, secureResource, numThreads * iterations),
                        null);
            }, "Carrera-Con-" + i));
        }

        // Iniciar todos los hilos
        fenceless.forEach(Thread::start);
        fenced.forEach(Thread::start);

        // Monitor para reportar resultado final
        new Thread(() -> {
            try {
                // Esperar a que todos los hilos terminen
                for (Thread t : fenceless)
                    t.join();
                for (Thread t : fenced)
                    t.join();

                int esperado = numThreads * iterations;
                registerEvent(EventType.RACE_CONDITION,
                        String.format("RESULTADO — Sin protección: %d | Con protección: %d | Esperado: %d",
                                unsafeResource, secureResource, esperado),
                        null);

                if (unsafeResource < esperado) {
                    registerEvent(EventType.RACE_CONDITION,
                            "✗ CARRERA DETECTADA: se perdieron " + (esperado - unsafeResource)
                                    + " incrementos por falta de exclusión mutua",
                            null);
                }
                registerEvent(EventType.INFO,
                        "✓ EXCLUSIÓN MUTUA: recurso protegido = " + secureResource
                                + " (valor correcto garantizado)",
                        null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Monitor-Final").start();

    }

    // LOG

    /**
     * Registra un evento en el sistema.
     */
    private void registerEvent(EventType tipo, String mensaje, String avionId) {
        logEventos.add(new EventoLog(tipo, mensaje, avionId));

        if (logEventos.size() > 200) {
            logEventos.remove(0);
        }
    }

    /**
     * Retorna los aviones que no han completado su ciclo.
     *
     * @return Lista inmutable de aviones activos
     */
    public List<Plane> getActivePlane() {
        return planes.stream()
                .filter(a -> a.getStatePlane() != AirplaneState.FILLED)
                .toList();
    }

    /**
     * Retorna los aviones que completaron su ciclo.
     *
     * @return Lista inmutable de aviones completados
     */
    public List<Plane> getCompletedPlanes() {
        return planes.stream()
                .filter(a -> a.getStatePlane() == AirplaneState.FILLED)
                .toList();
    }

    /**
     * Número de pistas actualmente libres.
     */
    public long getRunwaysAvailable() {
        return runways.stream().filter(Runway::isAvailable).count();
    }

    /**
     * Número de puertas actualmente libres.
     */
    public long getGatesAvailable() {
        return gates.stream().filter(Gate::isAvailable).count();
    }

    /**
     * Número de aviones esperando pista.
     */
    public long getPlanesWaitingForRunway() {
        return planes.stream()
                .filter(a -> a.getStatePlane() == AirplaneState.WAITING_FYI)
                .count();
    }

    /**
     * Retorna la configuración de aerolíneas para la simulación.
     */
    public String[] getAirlines() {
        return new String[] {
                "AeroCol", "Latam", "Avianca", "Copa", "Wingo",
                "EasyFly", "JetBlue", "Delta", "American", "United"
        };
    }

    public AirportProperties getAirportProperties() {
        return properties;
    }
}