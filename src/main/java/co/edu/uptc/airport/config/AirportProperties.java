package co.edu.uptc.airport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Clase de configuración que mapea las propiedades del archivo
 * {@code application.properties} con el prefijo {@code airport.*}.
 *
 * <p>
 * Spring Boot inyecta automáticamente los valores definidos en
 * {@code application.properties} a través de
 * {@code @ConfigurationProperties}. Esto centraliza toda la
 * configuración de la simulación en un solo lugar y permite
 * modificarla sin recompilar el código.
 *
 * <p>
 * Ejemplo de uso:
 * 
 * <pre>{@code
 * @Autowired
 * private AirportProperties props;
 * int pistas = props.getNumRunways(); // lee airport.num-runways
 * }</pre>
 *
 * @author Simulación Aeropuerto — Sistemas Operativos UPTC
 * @version 1.0
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "airport")
public class AirportProperties {

    /** Número de pistas de aterrizaje/despegue (airport.num-runways) */
    private int numRunways = 3;

    /** Número de puertas de embarque (airport.num-gates) */
    private int numGates = 5;

    /** Tiempo mínimo de uso de pista en ms (airport.tiempo-min-runway-ms) */
    private int tiempoMinRunwayMs = 2000;

    /** Tiempo máximo de uso de pista en ms (airport.tiempo-max-runway-ms) */
    private int tiempoMaxRunwayMs = 5000;

    /** Tiempo mínimo de estancia en puerta en ms (airport.tiempo-min-gate-ms) */
    private int tiempoMinGateMs = 3000;

    /** Tiempo máximo de estancia en puerta en ms (airport.tiempo-max-gate-ms) */
    private int tiempoMaxGateMs = 8000;

}
