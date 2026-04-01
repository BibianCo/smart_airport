package co.edu.uptc.airport.dto.request;

import lombok.Data;

/** Body del POST /api/avion */
@Data
public class AddPlaneRequest {
    private String name;

}
