package co.edu.uptc.airport.controller;

import java.io.IOException;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.uptc.airport.service.AirportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

}
