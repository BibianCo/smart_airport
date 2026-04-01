package co.edu.uptc.airport.dto;

import lombok.Builder;
import lombok.Data;

/** Estadísticas agregadas del aeropuerto */
@Data
@Builder
public class StatisticsDTO {

    private long runwayFree;
    private long gateFree;
    private long waitRunway;
    private long planesCompleted;
    private long planesTotal;

}
