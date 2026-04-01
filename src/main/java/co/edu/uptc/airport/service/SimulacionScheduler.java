package co.edu.uptc.airport.service;

import java.util.Random;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler de Spring que genera aviones automáticamente cuando la simulación
 * está activa.
 *
 * <h3>Por qué {@code @Scheduled} en lugar de un Thread manual?</h3>
 * <p>
 * Spring gestiona el ciclo de vida del pool de threads de scheduling.
 * Con {@code @Scheduled(fixedDelay = N)} no es necesario crear ni administrar
 * hilos manualmente — Spring se encarga. Esto es más idiomático en Spring Boot
 * y más fácil de gestionar que un hilo daemon manual.
 *
 * <p>
 * El método {@link #generarAvionAutomatico()} se ejecuta periódicamente
 * cada {@value INTERVALO_FIJO_MS}ms, y solo agrega un avión si la simulación
 * está activa y hay capacidad en el sistema.
 *
 * @author Bibian Corredor
 * @author Valentina Vega
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulacionScheduler {

    /** Intervalo base del scheduler en milisegundos */
    private static final int INTERVALO_FIJO_MS = 500;

    /** Máximo de aviones activos simultáneos para no saturar la simulación */
    private static final int MAX_PLANE_ACTIVE = 20;

    private final AirportService airportService;
    private final Random random = new Random();

    /** Acumulador de tiempo para simular intervalos variables entre llegadas */
    private long accumulatorTime = 0;
    private long nextArrival = 0;

    /**
     * Tarea programada que se ejecuta cada {@value INTERVALO_FIJO_MS}ms.
     *
     * <p>
     * Cuando la simulación está activa, genera un avión si ha transcurrido
     * el intervalo de llegada calculado aleatoriamente dentro del rango
     * configurado en {@code application.properties}.
     *
     * <p>
     * El uso de {@code fixedDelay} (en lugar de {@code fixedRate}) garantiza
     * que el siguiente tick no comienza hasta que el anterior haya terminado,
     * evitando solapamientos.
     */
    @Scheduled(fixedDelay = INTERVALO_FIJO_MS)
    public void generateAutomaticplane() {
        if (!airportService.isSimulationActive()) {
            accumulatorTime = 0; // Reinicia el acumulador si la simulación no está activa
            nextArrival = 0;
            return;
        }

        accumulatorTime += INTERVALO_FIJO_MS;

        // Calcular próxima llegada si aún no hay una programada

        if (nextArrival == 0) {
            int min = airportService.getAirportProperties().getIntervaloMinLlegadaMs();
            int max = airportService.getAirportProperties().getIntervaloMaxLlegadaMs();
            nextArrival = min + random.nextInt(max - min);
        }

        // Verificar si es momento de agregar un avión

        if (accumulatorTime >= nextArrival) {
            accumulatorTime = 0; // Reinicia el acumulador
            nextArrival = 0; // Reinicia la próxima llegada

            long activePlanes = airportService.getActivePlane().size();
            if (activePlanes >= MAX_PLANE_ACTIVE) {
                String[] airlines = airportService.getAirlines();
                String name = airlines[random.nextInt(airlines.length)];
                airportService.addPlane(name);
            }
        }
    }

}
