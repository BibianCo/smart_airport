package co.edu.uptc.airport.dto;

import co.edu.uptc.airport.model.EventoLog;
import lombok.Builder;
import lombok.Data;

/** Representación JSON de un evento del log */
@Data
@Builder
public class EventLogDTO {

    private String type;
    private String message;
    private String idPlane; // Opcional, puede ser null si el evento no está relacionado con un avión
                            // específico
    private String timestamp; // Formato ISO 8601

    /** Convierte una entidad EventoLog a su DTO */
    public static EventLogDTO fromEntity(EventoLog evento) {
        return EventLogDTO.builder()
                .type(evento.getEventType().name())
                .message(evento.getMessage())
                .idPlane(evento.getPlaneId())
                .timestamp(evento.getTimestamp())
                .build();
    }

}
