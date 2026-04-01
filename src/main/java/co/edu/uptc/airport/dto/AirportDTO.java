package co.edu.uptc.airport.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Clases DTO (Data Transfer Object) usadas por el controlador REST
 * para serializar el estado del aeropuerto a JSON via Jackson.
 *
 * <h3>Por qué DTOs?</h3>
 * <p>
 * Las entidades del modelo contienen lógica de sincronización (semáforos,
 * locks) que no debe exponerse directamente en la API. Los DTOs son POJOs
 * simples que Jackson serializa automáticamente a JSON. Esto también desacopla
 * la representación de red del modelo de dominio interno.
 *
 * <p>
 * Usamos clases estáticas internas para mantener todos los DTOs en
 * un solo archivo cohesivo.
 *
 * @author Bibian Corredor
 * @author Valentina Vega
 * @version 1.0
 */
public final class AirportDTO {

    public AirportDTO() {
    }

    /**
     * Estado completo del aeropuerto enviado al frontend en cada poll.
     * Jackson lo serializa automáticamente a JSON.
     */
    @Data
    @Builder
    public static class AirportStateDTO {
        private int number;
        private boolean available;
        private String idPlane;
        private String namePlane;
        private int usesTotal;

        /** Convierte una entidad Pista a su DTO */

    }

}
