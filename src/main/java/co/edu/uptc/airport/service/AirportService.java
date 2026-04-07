package co.edu.uptc.airport.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;

import co.edu.uptc.airport.config.AirportProperties;
import co.edu.uptc.airport.model.AirplaneState;
import co.edu.uptc.airport.model.EventType;
import co.edu.uptc.airport.model.EventoLog;
import co.edu.uptc.airport.model.Gate;
import co.edu.uptc.airport.model.Plane;
import co.edu.uptc.airport.model.Runway;
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
    private final ReentrantLock lock = new ReentrantLock();

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
        Runway runway = null;
        boolean gatePermitAcquired = false;

        try {
            long startTime = System.currentTimeMillis();
            plane.setStatePlane(AirplaneState.WAITING_FOR_LANDING);

            // --- FASE 0: ADQUISICIÓN DE RECURSOS (PREVENCIÓN DE DEADLOCK) ---
            while (runway == null || !gatePermitAcquired) {

                // Verificación de tiempo para alerta visual de Deadlock
                if (System.currentTimeMillis() - startTime > 10000) {
                    plane.setStatePlane(AirplaneState.LOCKED);
                    registerEvent(EventType.DEADLOCK_DETECTED, "Deadlock ", plane.getIdPlane());
                }

                if (!gatePermitAcquired) {
                    gatePermitAcquired = gateSemaphore.tryAcquire();
                }

                if (gatePermitAcquired) {
                    runway = findAndAcquireRunway(plane);
                    if (runway == null) {
                        // Si no hay pista, liberamos el permiso de puerta para evitar bloqueo circular
                        gateSemaphore.release();
                        gatePermitAcquired = false;
                    }
                }

                // Si no logramos ambos, esperamos un poco antes de reintentar
                if (runway == null) {
                    Thread.sleep(500);
                }
            }

            // --- FASE 1: ATERRIZAJE (Sincronización Compuesta) ---
            plane.setStatePlane(AirplaneState.WAITING_FOR_LANDING); // Nuevo Estado
            while (runway == null || !gatePermitAcquired) {
                if (!gatePermitAcquired)
                    gatePermitAcquired = gateSemaphore.tryAcquire();
                if (gatePermitAcquired) {
                    runway = findAndAcquireRunway(plane);
                    if (runway == null) {
                        gateSemaphore.release();
                        gatePermitAcquired = false;
                    }
                }
                if (runway == null)
                    Thread.sleep(500);
            }

            // Uso de pista para aterrizar
            plane.setAssignedRunway(runway.getRunwayNumber());
            plane.setStatePlane(AirplaneState.LANDING);
            registerEvent(EventType.INFO, "Aterrizando...", plane.getIdPlane());
            Thread.sleep(randomTime(2000, 4000));

            // Liberar pista tras aterrizar
            runway.release();
            runway = null;
            plane.setAssignedRunway(-1);

            // --- FASE 2: ESTACIONAMIENTO (Puerta) ---
            plane.setStatePlane(AirplaneState.TOWARDS_GATE);
            Gate gate = findAvailableGate(plane);
            plane.setAssignedDoor(gate.getGateNumber());
            plane.setStatePlane(AirplaneState.AT_GATE);
            registerEvent(EventType.GATE_OCCUPIED, "En puerta " + gate.getGateNumber(), plane.getIdPlane());

            Thread.sleep(randomTime(5000, 8000)); // Tiempo en puerta

            // --- FASE 3: DESPEGUE (Requiere Pista otra vez) ---
            plane.setStatePlane(AirplaneState.WAITING_FOR_TAKEOFF);
            registerEvent(EventType.INFO, "Solicitando pista para despegue", plane.getIdPlane());

            // Liberamos la puerta ANTES de pedir pista de despegue para no bloquear el
            // aeropuerto
            gate.release();
            gateSemaphore.release();
            plane.setAssignedDoor(-1);

            // Pedir pista para despegar
            while (runway == null) {
                runway = findAndAcquireRunway(plane);
                if (runway == null)
                    Thread.sleep(500);
            }

            plane.setAssignedRunway(runway.getRunwayNumber());
            plane.setStatePlane(AirplaneState.TAKEOFF);
            registerEvent(EventType.INFO, "Despegando...", plane.getIdPlane());
            Thread.sleep(randomTime(2000, 4000));

            runway.release();
            plane.setAssignedRunway(-1);

            // --- FINALIZACIÓN ---
            plane.setStatePlane(AirplaneState.COMPLETED);
            registerEvent(EventType.PLANE_COMPLETED, "Vuelo finalizado con éxito", plane.getIdPlane());

        } catch (InterruptedException e) {
            if (runway != null)
                runway.release();
            if (gatePermitAcquired)
                gateSemaphore.release();
            plane.setStatePlane(AirplaneState.LOCKED);
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

        for (Runway runway : runways) {
            if (runway.getSemaforo().tryAcquire()) {
                runway.acquire(plane);

                registerEvent(EventType.DEADLOCK_PREVENTION,
                        "Pista " + runway.getRunwayNumber() + " adquirida (orden global aplicado)",
                        plane.getIdPlane());

                return runway;
            }
        }
        return null;
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

        Runway runway = runways.get(0); // Usamos la primera pista como recurso compartido

        runway.isAvailable();

        // ── PARTE 1: SIN exclusión mutua ─────────────────────────────
        // Dos hilos leen que la pista está libre y los dos intentan ocuparla.
        // El sleep(50) entre lectura y escritura aumenta la ventana de carrera,
        // simulando que el SO cambia de contexto justo en ese instante.

        Runnable indefensible = () -> {
            try {
                String namePlane = Thread.currentThread().getName();

                registerEvent(EventType.RACE_CONDITION,
                        "SISTEMA: " + namePlane + " detecta Pista 1 LIBRE. Iniciando aproximación...", namePlane);

                // VENTANA DE CARRERA: pausa entre leer y escribir.
                // Aquí el SO puede cambiar de hilo → ambos habrán leído "libre"
                Thread.sleep(100);

                // ESCRITURA: ambos hilos llegan aquí creyendo que la pista está libre
                // y los dos sobreescriben pista1.avionActual sin ninguna protección
                runway.assignPlane(new Plane(namePlane, namePlane));

                registerEvent(EventType.RACE_CONDITION,
                        "CONFLICTO: " + namePlane + " ha tomado la Pista 1 sobreescribiendo cualquier dato previo!",
                        namePlane);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread planeAvianca = new Thread(indefensible, "Avianca");
        Thread planeLatam = new Thread(indefensible, "Latam");

        planeAvianca.start();
        planeLatam.start();

        // Esperar que ambos terminen y mostrar el resultado de la carrera

        new Thread(() -> {
            try {
                planeAvianca.join();
                planeLatam.join();

                Plane finalPlane = runway.getPlaneActual();
                registerEvent(EventType.RACE_CONDITION, "RESULTADO SIN PROTECCIÓN: Pista 1 quedó con: "
                        + (finalPlane != null ? finalPlane.getIdPlane() : "NULL")
                        + " Uno de los dos aviones fue SOBREESCRITO y perdido", null);

                // ── PARTE 2: CON exclusión mutua ─────────────────────────
                // La misma operación, pero protegida con synchronized.
                // Solo un hilo entra a la sección crítica a la vez →
                // el segundo espera → no hay sobreescritura.

                runway.release(); // Liberamos la pista para la siguiente prueba

                registerEvent(EventType.RACE_CONDITION, "Ahora con EXCLUSIÓN MUTUA (synchronized)", null);

                Runnable defensible = () -> {
                    String namePlane = Thread.currentThread().getName();

                    synchronized (runway) {
                        try {
                            if (runway.getPlaneActual() == null) {
                                Thread.sleep(50); // Simula ventana de carrera, pero ahora el otro hilo no puede entrar
                                runway.assignPlane(new Plane(namePlane, namePlane));
                                registerEvent(EventType.RACE_CONDITION,
                                        namePlane + " ocupó pista1 correctamente (protegido)",
                                        namePlane);
                            } else {
                                registerEvent(EventType.RACE_CONDITION,
                                        namePlane + " intentó ocupar pista1 pero ya estaba ocupada por "
                                                + runway.getPlaneActual().getIdPlane(),
                                        namePlane);
                            }

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                    }
                };

                Thread plane1 = new Thread(defensible, "Avi-P1");
                Thread plane2 = new Thread(defensible, "Avi-P2");
                plane1.start();
                plane2.start();
                plane1.join();
                plane2.join();

                registerEvent(EventType.RACE_CONDITION, "RESULTADO CON PROTECCIÓN : Pista 1 quedó con: "
                        + runway.getPlaneActual().getIdPlane() + "Segundo avión detectó pista ocupada correctamente",
                        null);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Monitor-Demo-Carrera").start();

    }

    // LOG

    /**
     * Registra un evento en el sistema.
     */
    private void registerEvent(EventType tipo, String mensaje, String avionId) {
        lock.lock(); // 1. Entrada a la sección crítica: El hilo adquiere el permiso exclusivo
        try {
            // 2. Sección Crítica: Solo UN hilo a la vez puede ejecutar estas líneas
            logEventos.add(new EventoLog(tipo, mensaje, avionId));

            // Mantenimiento de la estructura de datos compartida
            if (logEventos.size() > 200) {
                logEventos.remove(0);
            }
        } finally {
            // 3. Salida: Se libera el cerrojo pase lo que pase (incluso si hay error)
            lock.unlock();
        }
    }

    /**
     * Retorna los aviones que no han completado su ciclo.
     *
     * @return Lista inmutable de aviones activos
     */
    public List<Plane> getActivePlane() {
        return planes.stream()
                .filter(a -> a.getStatePlane() != AirplaneState.COMPLETED)
                .toList();
    }

    /**
     * Retorna los aviones que completaron su ciclo.
     *
     * @return Lista inmutable de aviones completados
     */
    public List<Plane> getCompletedPlanes() {
        return planes.stream()
                .filter(a -> a.getStatePlane() == AirplaneState.COMPLETED)
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
                .filter(a -> a.getStatePlane() == AirplaneState.WAITING_FOR_LANDING)
                .count();
    }

    public AirportProperties getAirportProperties() {
        return properties;
    }
}