package co.edu.uptc.airport.dto;

import co.edu.uptc.airport.model.Plane;
import co.edu.uptc.airport.model.Runway;
import lombok.Builder;
import lombok.Data;

/** Representación JSON de una pista */
@Data
@Builder
public class RunwayDTO {
    private int number;
    private boolean available;
    private String idPlane;
    private String namePlane;
    private int usesTotal;

    /** Convierte una entidad Pista a su DTO */
    public static RunwayDTO fromEntity(Runway runway) {
        Plane plane = runway.getPlaneActual();
        return RunwayDTO.builder()
                .number(runway.getRunwayNumber())
                .available(runway.isAvailable())
                .idPlane(plane != null ? plane.getIdPlane() : null)
                .namePlane(plane != null ? plane.getNamePlane() : null)
                .usesTotal(runway.getUseTotal())
                .build();
    }

}
