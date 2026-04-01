package co.edu.uptc.airport.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Estado completo del aeropuerto enviado al frontend en cada poll.
 * Jackson lo serializa automáticamente a JSON.
 */
@Data
@Builder
public class AirportStateDTO {

    private List<RunwayDTO> runways;
    private List<GateDTO> gates;
    private List<PlaneDTO> planes;
    private StatisticsDTO statistics;
    private List<EventLogDTO> log;
    private boolean sumulationActive;

}
