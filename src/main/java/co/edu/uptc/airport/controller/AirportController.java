package co.edu.uptc.airport.controller;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.uptc.airport.dto.AirportStateDTO;
import co.edu.uptc.airport.dto.EventLogDTO;
import co.edu.uptc.airport.dto.GateDTO;
import co.edu.uptc.airport.dto.PlaneDTO;
import co.edu.uptc.airport.dto.RunwayDTO;
import co.edu.uptc.airport.dto.StatisticsDTO;
import co.edu.uptc.airport.dto.request.AddPlaneRequest;
import co.edu.uptc.airport.dto.request.SimpleRequest;
import co.edu.uptc.airport.model.EventoLog;
import co.edu.uptc.airport.model.Plane;
import co.edu.uptc.airport.service.AirportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controlador REST del aeropuerto.
 *
 * <h3>Anotaciones de Spring MVC:</h3>
 * <ul>
 * <li>{@code @RestController} = {@code @Controller} + {@code @ResponseBody}.
 * Todos los métodos retornan JSON automáticamente vía Jackson.</li>
 * <li>{@code @RequestMapping("/api")} — prefijo base para todos los
 * endpoints.</li>
 * <li>{@code @RequiredArgsConstructor} (Lombok) — inyección por constructor
 * (patrón recomendado en Spring, más testeable que @Autowired en campo).</li>
 * </ul>
 *
 * <h3>Endpoints expuestos:</h3>
 * 
 * <pre>
 * GET  /api/estado      → Estado completo del aeropuerto
 * POST /api/iniciar     → Inicia simulación automática
 * POST /api/detener     → Detiene simulación automática
 * POST /api/reiniciar   → Reinicia el sistema
 * POST /api/avion       → Agrega un avión manualmente
 * POST /api/carrera     → Demuestra condición de carrera
 * </pre>
 *
 * @author Bibian Corredor
 * @author Valentina Vega
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AirportController {

        private final AirportService airportService;

        /**
         * Retorna el estado completo del aeropuerto en JSON.
         *
         * <p>
         * Este endpoint es consultado por el frontend cada 500ms (polling).
         * Construye el {@link EstadoAeropuerto} DTO mapeando las entidades del
         * servicio. Jackson serializa automáticamente el objeto a JSON.
         *
         * @return {@link ResponseEntity} con el estado completo
         */
        @GetMapping("/state")
        public ResponseEntity<AirportStateDTO> getState() {

                // Mapear pistas a DTOs
                List<RunwayDTO> runways = airportService.getRunways().stream()
                                .map(RunwayDTO::fromEntity)
                                .toList();

                // Mapear puertas a DTOs
                List<GateDTO> gates = airportService.getGates().stream()
                                .map(GateDTO::from)
                                .toList();

                // Mapear aviones activos a DTOs
                List<PlaneDTO> planes = airportService.getActivePlane().stream()
                                .map(PlaneDTO::fromEntity)
                                .toList();

                // Construir estadísticas
                StatisticsDTO statistics = StatisticsDTO.builder()
                                .runwayFree(airportService.getRunwaysAvailable())
                                .gateFree(airportService.getGatesAvailable())
                                .waitRunway(airportService.getPlanesWaitingForRunway())
                                .planesCompleted(airportService.getCompletedPlanes().size())
                                .planesTotal(airportService.getPlanes().size())
                                .build();

                // Últimos 50 eventos del log (los más recientes)
                List<EventoLog> allEvent = airportService.getLogEventos();
                int fromIndex = Math.max(0, allEvent.size() - 50);
                List<EventLogDTO> logDTO = allEvent.subList(fromIndex, allEvent.size()).stream()
                                .map(EventLogDTO::fromEntity)
                                .toList();

                // Ensamblar respuesta final
                AirportStateDTO response = AirportStateDTO.builder()
                                .runways(runways)
                                .gates(gates)
                                .planes(planes)
                                .statistics(statistics)
                                .log(logDTO)
                                .simulationActive(airportService.isSimulationActive())
                                .gatePermits(airportService.getGatesAvailable())
                                .build();

                return ResponseEntity.ok(response);

        }

        /**
         * Inicia la generación automática de aviones.
         *
         * @return Confirmación de la operación
         */
        @PostMapping("/start")
        public ResponseEntity<SimpleRequest> startSimulation() {
                airportService.startSimulation();
                return ResponseEntity.ok(SimpleRequest.success("Simulación iniciada correctamente"));
        }

        /**
         * Detiene la generación automática de aviones.
         * Los aviones ya en ciclo continúan hasta completar.
         *
         * @return Confirmación de la operación
         */
        @PostMapping("/stop")
        public ResponseEntity<SimpleRequest> stopSimulation() {
                airportService.stopSimulation();
                return ResponseEntity.ok(SimpleRequest.success("Simulación detenida correctamente"));
        }

        /**
         * Reinicia completamente el sistema del aeropuerto.
         *
         * @return Confirmación de la operación
         */
        @PostMapping("/reset")
        public ResponseEntity<SimpleRequest> resetSimulation() {
                airportService.resetSimulation();
                return ResponseEntity.ok(SimpleRequest.success("Simulación reiniciada correctamente"));
        }

        /**
         * Agrega un avión manualmente a la simulación.
         *
         * <p>
         * Body esperado:
         * 
         * <pre>{@code {"nombre": "Avianca"}}</pre>
         *
         * @param request Cuerpo de la petición con el nombre del avión
         * @return El avión creado como DTO
         */
        @PostMapping("/plane")
        public ResponseEntity<SimpleRequest> addPlane(@RequestBody AddPlaneRequest request) {

                String planeName = request.getName() != null ? request.getName() : "Anónimo";
                Plane plane = airportService.addPlane(planeName);

                return ResponseEntity.ok(SimpleRequest
                                .success("Avion " + plane.getIdPlane() + "(" + planeName + ") agregado al sistema"));
        }

        /**
         * Lanza la demostración de condición de carrera.
         * Los resultados se reflejan en el log de eventos en tiempo real.
         *
         * @return Confirmación del inicio de la demostración
         */
        @PostMapping("/race_condition")
        public ResponseEntity<SimpleRequest> startRaceCondition() {
                airportService.demonstrateRaceCondition();
                return ResponseEntity.ok(SimpleRequest.success(
                                "Demostración de condición de carrera iniciada."));
        }

        /**
         * Captura excepciones no manejadas y retorna una respuesta de error
         * estructurada.
         *
         * @param ex La excepción capturada
         * @return Respuesta de error con HTTP 500
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<SimpleRequest> handleException(Exception ex) {
                log.error("Error en el controlador del aeropuerto: ", ex.getMessage(), ex);
                return ResponseEntity.internalServerError()
                                .body(SimpleRequest.error("Ocurrió un error en el servidor: " + ex.getMessage()));
        }

}
