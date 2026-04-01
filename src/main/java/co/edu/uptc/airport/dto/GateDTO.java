package co.edu.uptc.airport.dto;

import co.edu.uptc.airport.model.Gate;
import co.edu.uptc.airport.model.Plane;
import lombok.Builder;
import lombok.Data;

/** Representación JSON de una puerta de embarque */
@Data
@Builder
public class GateDTO {
    private int number;
    private boolean available;
    private String idPlane;
    private String namePlane;
    private int usesTotal;

    /** Convierte una entidad Puerta a su DTO */
    public static GateDTO from(Gate gate) {
        Plane plane = gate.getOccupiedPlane();
        return GateDTO.builder()
                .number(gate.getGateNumber())
                .available(gate.isAvailable())
                .idPlane(plane != null ? plane.getIdPlane() : null)
                .namePlane(plane != null ? plane.getNamePlane() : null)
                .usesTotal(gate.getUsageCount())
                .build();
    }

}
