package co.edu.uptc.airport.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Modelo que representa un evento registrado en el log concurrente del
 * aeropuerto.
 * Los eventos son inmutables una vez creados y se almacenan con marca de
 * tiempo.
 *
 * El log compartido cumple el requisito de "registro de eventos concurrentes"
 * especificado en el proyecto.
 *
 * @author Bibian Corredor
 * @author Valentina Vega
 * @version 1.0
 */
public class EventoLog {

    /** Formateador de tiempo para mostrar HH:mm:ss.SSS */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** Tipo del evento */
    private final EventType eventType;

    /** Mensaje descriptivo del evento */
    private final String message;

    /** Identificador del avión relacionado (puede ser null) */
    private final String planeId;

    /** Hora exacta del evento */
    private final String timestamp;

    /** Marca de tiempo en milisegundos para ordenamiento */
    private final long timestampMs;

    /**
     * Constructor del evento de log.
     *
     * @param tipo    Tipo de evento
     * @param mensaje Descripción del evento
     * @param avionId ID del avión (puede ser null)
     */
    public EventoLog(EventType eventType, String message, String planeId) {
        this.eventType = eventType;
        this.message = message;
        this.planeId = planeId;
        this.timestampMs = System.currentTimeMillis();
        this.timestamp = LocalTime.now().format(TIME_FORMATTER);
    }

    public static DateTimeFormatter getTimeFormatter() {
        return TIME_FORMATTER;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getMessage() {
        return message;
    }

    public String getPlaneId() {
        return planeId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    /**
     * Serializa el evento a formato JSON.
     *
     * @return String JSON del evento
     */
    public String toJson() {
        return String.format(
                "{\"type\": \"%s\", \"message\": \"%s\", \"planeId\": \"%s\", \"timestamp\": \"%s\"}",
                eventType.name(), message.replace("\"", "'"), planeId != null ? planeId : "null", timestamp);
    }

    @Override
    public String toString() {
        return String.format("[%s] [%s] %s", timestamp, eventType, message);
    }

}
