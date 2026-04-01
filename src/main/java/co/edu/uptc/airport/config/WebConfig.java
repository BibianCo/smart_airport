package co.edu.uptc.airport.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración global de CORS (Cross-Origin Resource Sharing) para Spring MVC.
 *
 * <p>
 * Permite que el frontend web pueda hacer peticiones a la API REST
 * independientemente del origen (útil en desarrollo y cuando el frontend
 * se sirve desde un dominio diferente al backend).
 *
 * <p>
 * En producción, reemplazar {@code "*"} con los orígenes específicos
 * permitidos por razones de seguridad.
 *
 * @author Bibian Corredor
 * @author Valentina Vega
 * @version 1.0
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Configura las reglas CORS para todos los endpoints de la API.
     *
     * @param registry Registro de configuraciones CORS de Spring MVC
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

}
