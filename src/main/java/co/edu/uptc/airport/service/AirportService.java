package co.edu.uptc.airport.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import co.edu.uptc.airport.model.AirplaneState;
import co.edu.uptc.airport.model.EventType;
import co.edu.uptc.airport.model.EventoLog;
import co.edu.uptc.airport.model.Gate;
import co.edu.uptc.airport.model.Plane;
import co.edu.uptc.airport.model.Runway;

/**
 * Servicio principal del aeropuerto que gestiona todos los recursos
 * concurrentes.
 *
 * <h3>Mecanismos de sincronización utilizados:</h3>
 * <ul>
 * <li><b>Semáforo binario (pistaSemaphore):</b> Implementado con Semaphore(1)
 * por pista.
 * Garantiza exclusión mutua — solo un avión usa una pista a la vez.</li>
 * <li><b>Semáforo de conteo (puertaSemaphore):</b> Semaphore(N_PUERTAS)
 * controla
 * cuántos aviones pueden ocupar puertas simultáneamente.</li>
 * <li><b>Sincronización compuesta:</b> Un avión solo avanza si obtiene AMBOS
 * recursos
 * (pista Y puerta) siguiendo orden global para prevenir deadlocks.</li>
 * <li><b>CopyOnWriteArrayList:</b> Lista de eventos thread-safe para el log
 * concurrente.</li>
 * </ul>
 *
 * <h3>Prevención de deadlocks:</h3>
 * Se impone un orden global de adquisición: primero pista, luego puerta.
 * Ningún avión puede invertir este orden, eliminando la condición de "espera
 * circular".
 *
 * @author Bibian Corredor
 * @author Valentina Vega
 * @version 1.0
 */
public class AirportService {

    /** Número de pistas de aterrizaje/despegue disponibles */
    private final int nunRunway;

    /** Número de puertas de embarque disponibles */
    private final int numGates;

    /** Tiempo mínimo de uso de una pista (ms) */
    private static final int MIN_RUNWAY_TIME_MS = 2000;

    /** Tiempo máximo de uso de una pista (ms) */
    private static final int MAX_RUNWAY_TIME_MS = 5000;

    /** Tiempo mínimo de estancia en puerta (ms) */
    private static final int MIN_GATE_TIME_MS = 3000;

    /** Tiempo máximo de estancia en puerta (ms) */
    private static final int MAX_GATE_TIME_MS = 8000;

    /** Lista de pistas — cada una con su ReentrantLock (semáforo binario) */
    private final List<Runway> runways;

    /** Lista de puertas de embarque */
    private final List<Gate> gates;

    /**
     * Semáforo de conteo para puertas de embarque.
     * Permite que hasta numPuertas aviones ocupen puertas simultáneamente.
     * Implementa el semáforo de conteo requerido en el proyecto.
     */
    private final Semaphore gateSemaphore;

    /**
     * Semáforos binarios para cada pista.
     * Semaphore(1, true) = semáforo binario con política FIFO (fair).
     * Garantiza que solo un avión use cada pista a la vez.
     */
    private final List<Semaphore> runwaySemaphores;

    /**
     * Lista de todos los aviones en el sistema
     * CopyOnWriteArrayList es una estructura de datos thread-safe que permite
     * iterar sin necesidad de sincronización explícita, ideal para el log
     * concurrente.
     * Cada vez que se modifica (add, remove, etc.), se hace una copia completa de
     * la lista
     */
    private final CopyOnWriteArrayList<Plane> planes;

    /** Log de eventos concurrentes — thread-safe */
    private final CopyOnWriteArrayList<EventoLog> logEventos;

    /**
     * Contador atómico para IDs de aviones }
     * AtomicInteger garantiza que cada avión reciba un ID único incluso en un
     * entorno concurrente sin necesidad de sincronización adicional.
     */
    private final AtomicInteger planeCounter;

    /** Bandera de simulación activa */
    private volatile boolean simulationActive;

    /** Bandera para demostrar condición de carrera */
    private volatile boolean raceConditionFlag;

    /** Recurso compartido SIN protección — para demostrar condición de carrera */
    private int unprotectedResource = 0;

    /** Recurso compartido CON protección — para comparar */
    private final Object protectedResourceLock = new Object();
    private int protectedResource = 0;

    /**
     * Constructor del servicio del aeropuerto.
     *
     * @param numPistas  Número de pistas disponibles
     * @param numPuertas Número de puertas disponibles
     */
    public AirportService(int nunRunway, int numGates) {
        this.nunRunway = nunRunway;
        this.numGates = numGates;
        this.simulationActive = true;
        this.raceConditionFlag = false;
        this.planes = new CopyOnWriteArrayList<>();
        this.logEventos = new CopyOnWriteArrayList<>();
        this.planeCounter = new AtomicInteger(0);

        // Inicializar semáforos para pistas
        this.runways = new ArrayList<>();
        this.runwaySemaphores = new ArrayList<>();
        for (int i = 1; i <= nunRunway; i++) {
            this.runways.add(new Runway(i));

            // Semaphore(1) = semáforo binario, fair=true previene inanición
            this.runwaySemaphores.add(new Semaphore(1, true));
        }

        // Inicializar puertas y su semáforo de conteo
        this.gates = new ArrayList<>();
        for (int i = 1; i <= numGates; i++) {
            this.gates.add(new Gate(i));
        }

        // Semáforo de conteo: permite hasta numGates aviones simultáneos
        this.gateSemaphore = new Semaphore(numGates, true);

        registerEvent(EventType.INFO,
                String.format("Aeropuerto iniciado: %d pistas, %d puertas", nunRunway, numGates), null);

    }

    // GESTION DE AVIONES Y CICLO DE VIDA COMO HILOS

    /**
     * Agrega un nuevo avión al aeropuerto y lanza su hilo de ejecución.
     * Cada avión es un hilo independiente que compite por recursos.
     *
     * @param nombre Nombre o aerolínea del avión
     * @return El avión creado
     */

    public Plane addPlane(String nombre) {

        String planeId = String.format("AV-%03d", planeCounter.incrementAndGet());
        Plane plane = new Plane(planeId, nombre);
        planes.add(plane);
        registerEvent(EventType.INFO, "Avión ingresó al sistema", plane.getIdPlane());

        // Lanzar hilo para simular el ciclo de vida del avión
        Thread planeThread = new Thread(() -> airplaneCycle(plane), "Hilo-" + plane.getIdPlane());
        planeThread.setDaemon(true);
        planeThread.start();

        return plane;
    }

    /**
     * Ciclo de vida completo de un avión como hilo.
     *
     * Orden de adquisición de recursos (prevención de deadlock):
     * 1. Adquirir pista (semáforo binario)
     * 2. Adquirir puerta (semáforo de conteo)
     * → Siempre el mismo orden → no hay espera circular → no hay deadlock
     *
     * @param avion El avión cuyo ciclo se ejecuta
     */
    public void airplaneCycle(Plane plane) {

        try {
            // fase 1: esperar y adquirir pista (semaforo binario)
            plane.setStatePlane(AirplaneState.WAITING_FYI);
            registerEvent(EventType.INFO, "Solicitando pista", plane.getIdPlane());

            // Encontrar una pista disponible (intentar adquirir semáforo binario)
            int runwayIndex = findAndAddRunway(plane);
            Runway assignedRunway = runways.get(runwayIndex);

            // fase 2: usar la pista (simular tiempo de aterrizaje/despegue)
            plane.setStatePlane(AirplaneState.ON_RUNWAY);
            plane.setAssignedRunway(assignedRunway.getRunwayNumber());
            assignedRunway.assignPlane(plane);
            registerEvent(EventType.RUNWAY_OCCUPIED, "Aterrizando en pista " + assignedRunway.getRunwayNumber(),
                    plane.getIdPlane());

            // Simular tiempo de aterrizaje/despegue
            int runwayTime = MIN_RUNWAY_TIME_MS + (int) (Math.random() * (MAX_RUNWAY_TIME_MS - MIN_RUNWAY_TIME_MS));
            Thread.sleep(runwayTime);

            // Fase 3: liberar pista
            assignedRunway.release();
            runwaySemaphores.get(runwayIndex).release();
            plane.setAssignedRunway(-1);
            registerEvent(EventType.RUNWAY_CLEARED, "Pista " + assignedRunway.getRunwayNumber() + " liberada",
                    plane.getIdPlane());

            // Fase 4: adquirir puerta (semaforo de conteo)
            plane.setStatePlane(AirplaneState.TOWARDS_DOOR);
            registerEvent(EventType.INFO, "Solicitando puerta", plane.getIdPlane());

            // El semaforo de conteo bloquea si todas las puertas están ocupadas, hasta que
            // una se libere
            gateSemaphore.acquire();

            // Encontrar y asignar perta fisica disponible
            Gate assignedGate = findAvailableGate(plane);
            plane.setStatePlane(AirplaneState.AT_DOOR);
            plane.setAssignedDoor(assignedGate.getGateNumber());
            registerEvent(EventType.GATE_OCCUPIED, "Avion en puerta " + assignedGate.getGateNumber(),
                    plane.getIdPlane());

            // Fase 5: Usar perta
            int gateTime = MIN_GATE_TIME_MS + (int) (Math.random() * (MAX_GATE_TIME_MS - MIN_GATE_TIME_MS));
            Thread.sleep(gateTime);

            // Fase 6: Liberar puerta
            assignedGate.releasePlane();
            plane.setAssignedDoor(-1);
            gateSemaphore.release();
            registerEvent(EventType.GATE_CLEARED, "Puerta " + assignedGate.getGateNumber() + " liberada",
                    plane.getIdPlane());

            // Fase 7: Avión completó su ciclo
            plane.setStatePlane(AirplaneState.FILLED);
            registerEvent(EventType.PLANE_COMPLETED, "Avion compelto ciclo exitiosamente", plane.getIdPlane());

        } catch (InterruptedException e) {
            plane.setStatePlane(AirplaneState.LOCKED);
            registerEvent(EventType.ERROR, "Avión interrumpido durante su ciclo", plane.getIdPlane());
            Thread.currentThread().interrupt();
        }

    }

    /**
     * Encuentra una pista disponible y adquiere su semáforo binario.
     * Implementa el patrón de búsqueda con tryAcquire para no bloquear
     * indefinidamente en una pista ocupada.
     *
     * @param avion El avión que solicita la pista
     * @return Índice de la pista adquirida (base 0)
     * @throws InterruptedException si el hilo es interrumpido
     */
    private int findAndAddRunway(Plane plane) throws InterruptedException {

        while (true) {
            for (int i = 0; i < runwaySemaphores.size(); i++) {
                // el tryAcquire() devuelve true si pudo adquirir el semáforo, false si no pudo
                // (pista ocupada)
                // tryAcquire(0) = no espera, devuelve inmediatamente
                if (runwaySemaphores.get(i).tryAcquire()) {
                    registerEvent(EventType.DEADLOCK_PREVENTION, "Pista " + (i + 1) + " adquirida (orden global)",
                            plane.getIdPlane());
                    return i;
                }
            }
            // Si no se pudo adquirir ninguna pista, esperar un poco antes de intentar de
            // nuevo
            Thread.sleep(200);
        }
    }

    /**
     * Encuentra una puerta física disponible y la asigna al avión.
     * El semáforo de conteo ya fue adquirido antes de llamar a este método,
     * por lo que siempre habrá al menos una puerta libre.
     *
     * @param avion El avión que ocupa la puerta
     * @return La puerta asignada
     */
    private synchronized Gate findAvailableGate(Plane plane) {

        for (Gate gate : gates) {
            if (gate.isAvailable()) {
                gate.assignPlane(plane);
                return gate;
            }
        }
        // No debería llegar aquí porque el semáforo de conteo garantiza que haya una
        // puerta disponible
        throw new IllegalStateException("No hay puerta disponible aunque el semáforo lo indica");
    }

    // CONDICION DE CARRERA DEMOSTRACION

    /**
     * Demuestra una condición de carrera con dos hilos modificando el mismo
     * recurso sin exclusión mutua. Requisito explícito del proyecto.
     *
     * CONDICIÓN DE CARRERA: Dos aviones leen el mismo valor, ambos incrementan,
     * pero uno sobreescribe el resultado del otro → pérdida de datos.
     */
    public synchronized void demonstrateRaceCondition() {
        registerEvent(EventType.RACE_CONDITION, "INICIANDO DEMOSTRACIÓN DE CONDICIÓN DE CARRERA ", null);

        unprotectedResource = 0;
        protectedResource = 0;

        // crear 5 hilos que modifican el recurso sin protección
        List<Thread> unprotectedThreads = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int num = i;
            Thread t = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    // SIN exclusión mutua: read-modify-write no atómico
                    int valueRead = unprotectedResource;

                    // Simular pausa entre lectura y escritura → aumenta probabilidad de carrera

                    try {
                        Thread.sleep(0, 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    unprotectedResource = valueRead + 1;
                }

                registerEvent(EventType.RACE_CONDITION,
                        String.format("Hilo %d terminó. Recurso SIN protección: %d (esperado ~500)",
                                num, unprotectedResource),
                        null);
            }, "CarreraHilo-" + i);
            unprotectedThreads.add(t);
        }

        // crear 5 hilos que modifican el recurso con protección
        List<Thread> protectedThreads = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int num = i;
            Thread t = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    // CON exclusión mutua: bloque sincronizado garantiza atomicidad
                    synchronized (protectedResourceLock) {
                        protectedResource++;
                    }
                }
                registerEvent(EventType.INFO,
                        String.format("Hilo %d terminó. Recurso CON protección: %d (esperado 500)",
                                num, protectedResource),
                        null);
            }, "ProtegidoHilo-" + i);
            protectedThreads.add(t);
        }

        // iniciar todos los hilos
        unprotectedThreads.forEach(Thread::start);
        protectedThreads.forEach(Thread::start);

        // Esperar resultados en hilo separado
        Thread monitorThread = new Thread(() -> {
            try {
                for (Thread t : unprotectedThreads)
                    t.join();
                for (Thread t : protectedThreads)
                    t.join();

                registerEvent(EventType.RACE_CONDITION,
                        String.format("RESULTADO FINAL — Sin protección: %d | Con protección: %d",
                                unprotectedResource, protectedResource),
                        null);

                if (unprotectedResource < 500) {
                    registerEvent(EventType.RACE_CONDITION, "CONDICIÓN DE CARRERA DETECTADA: se perdieron " +
                            (500 - unprotectedResource) + " incrementos", null);
                }

                registerEvent(EventType.INFO, "EXCLUSIÓN MUTUA: recurso protegido llegó a 500 correctamente", null);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Monitor-Carrera");

        monitorThread.setDaemon(true);
        monitorThread.start();

    }

    // CONTROL DE SIMULACIÓN

    /**
     * Inicia la simulación automática del aeropuerto.
     * Genera aviones periódicamente mientras la simulación esté activa.
     */
    public void startSimulation() {
        simulationActive = true;
        registerEvent(EventType.INFO, "Simulación iniciada", null);

        String[] airlines = { "AeroCol", "Latam", "Avianca", "Copa", "Wingo",
                "EasyFly", "JetBlue", "Delta", "American", "United" };

        Thread generatorThread = new Thread(() -> {
            int count = 0;
            while (simulationActive) {
                try {
                    String airline = airlines[count % airlines.length];
                    addPlane(airline);
                    count++;
                    // intervalo entre llegada de aviones (1-3 segundos)
                    Thread.sleep(1000 + (int) (Math.random() * 2000)); // Generar un avión cada segundo

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Generador-Aviones");
        generatorThread.setDaemon(true);
        generatorThread.start();
    }

    /**
     * Detiene la simulación automática.
     */
    public void stopSimulation() {
        simulationActive = false;
        registerEvent(EventType.INFO, "Simulación detenida", null);
    }

    /**
     * Reinicia la simulación limpiando todos los recursos.
     */
    public synchronized void resetSimulation() {
        simulationActive = false;
        planes.clear();
        logEventos.clear();
        planeCounter.set(0);
        unprotectedResource = 0;
        protectedResource = 0;

        // Reiniciar pistas
        for (int i = 0; i < runways.size(); i++) {
            runways.set(i, new Runway(i + 1));
        }

        // Reiniciar puertas
        for (int i = 0; i < gates.size(); i++) {
            gates.set(i, new Gate(i + 1));
        }

        registerEvent(EventType.INFO, "Simulación reiniciada", null);
    }

    // LOG CONCURRENTE

    /**
     * Registra un evento en el log concurrente compartido.
     * CopyOnWriteArrayList garantiza thread-safety sin bloqueos en lectura.
     *
     * @param tipo    Tipo del evento
     * @param mensaje Descripción del evento
     * @param avionId ID del avión relacionado (puede ser null)
     */
    private void registerEvent(EventType tipo, String mensaje, String avionId) {
        EventoLog evento = new EventoLog(tipo, mensaje, avionId);
        logEventos.add(evento);
        // Mantener solo los últimos 200 eventos para no saturar memoria
        if (logEventos.size() > 200) {
            logEventos.remove(0);
        }
    }

    // SERIALIZACIÓN DEL ESTADO PARA EL FRONTEND

    /**
     * Genera el estado completo del aeropuerto en formato JSON
     * para ser enviado al frontend mediante polling.
     *
     * @return String JSON con el estado completo
     */
    public String getStateJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // Pistas
        sb.append("\"pistas\":[");
        for (int i = 0; i < runways.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(runways.get(i).toJson());
        }

        sb.append("],");

        // Puertas
        sb.append("\"puertas\":[");
        for (int i = 0; i < gates.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(gates.get(i).toJson());
        }
        sb.append("]");

        // Aviones activos (no completados)
        List<Plane> activePlanes = new ArrayList<>();
        List<Plane> completedPlanes = new ArrayList<>();
        for (Plane plane : planes) {
            if (plane.getStatePlane() == AirplaneState.FILLED) {
                completedPlanes.add(plane);
            } else {
                activePlanes.add(plane);
            }
        }

        sb.append(",\"avionesActivos\":[");
        for (int i = 0; i < activePlanes.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(activePlanes.get(i).toJson());
        }
        sb.append("]");

        // Estadísticas

        long runwayFree = runways.stream().filter(Runway::isAvailable).count();
        long gateFree = gates.stream().filter(Gate::isAvailable).count();
        long runwayWaiting = planes.stream().filter(p -> p.getStatePlane() == AirplaneState.WAITING_FYI).count();

        sb.append(String.format(
                "\"estadisticas\":{\"pistasLibres\":%d,\"puertasLibres\":%d," +
                        "\"esperandoPista\":%d,\"avionesCompletados\":%d,\"totalAviones\":%d},",
                runwayFree, gateFree, runwayWaiting, completedPlanes.size(), planes.size()));

        // Ultimos 50 eventos del log
        sb.append("\"log\":[");
        List<EventoLog> recentEvents = new ArrayList<>(logEventos);
        int start = Math.max(0, recentEvents.size() - 50);
        for (int i = start; i < recentEvents.size(); i++) {
            if (i > start)
                sb.append(",");
            sb.append(recentEvents.get(i).toJson());
        }
        sb.append("]");

        sb.append(String.format("\"simulacionActiva\":%b", simulationActive));
        sb.append("}");
        return sb.toString();

    }

    public int getNunRunway() {
        return nunRunway;
    }

    public int getNumGates() {
        return numGates;
    }

    public List<Runway> getRunways() {
        return Collections.unmodifiableList(runways);
    }

    public List<Gate> getGates() {
        return Collections.unmodifiableList(gates);
    }

    public List<Plane> getPlanes() {
        return Collections.unmodifiableList(planes);
    }

    public List<EventoLog> getLogEventos() {
        return Collections.unmodifiableList(logEventos);
    }

    public boolean isSimulationActive() {
        return simulationActive;
    }

}
