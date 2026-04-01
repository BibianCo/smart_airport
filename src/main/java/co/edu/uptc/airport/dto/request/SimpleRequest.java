package co.edu.uptc.airport.dto.request;

import lombok.Builder;
import lombok.Data;

/** Respuesta genérica de operaciones */
@Data
@Builder
public class SimpleRequest {

    private boolean ok;
    private String message;

    public static SimpleRequest success(String message) {
        return SimpleRequest.builder()
                .ok(true)
                .message(message)
                .build();
    }

    public static SimpleRequest error(String message) {
        return SimpleRequest.builder()
                .ok(false)
                .message(message)
                .build();
    }

}
