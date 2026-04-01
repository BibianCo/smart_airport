package co.edu.uptc.airport.dto;

import co.edu.uptc.airport.model.Plane;
import lombok.Builder;
import lombok.Data;

/** Representación JSON de un avión activo */
@Data
@Builder
public class PlaneDTO {
    private String idPlane;
    private String namePlane;
    private String status; // Ej: "En pista", "En puerta", "Completado"
    private String statusDesc;
    private int runwayNumber; // Número de pista actual, o -1 si no está en pista
    private int gateNumber; // Número de puerta actual, o -1 si no está en puerta

    /** Convierte una entidad Avion a su DTO */
    public static PlaneDTO fromEntity(Plane plane) {
        return PlaneDTO.builder()
                .idPlane(plane.getIdPlane())
                .namePlane(plane.getNamePlane())
                .status(plane.getStatePlane().getDescription())
                .statusDesc(plane.getStatePlane().name())
                .runwayNumber(plane.getAssignedRunway())
                .gateNumber(plane.getAssignedDoor())
                .build();
    }

}
