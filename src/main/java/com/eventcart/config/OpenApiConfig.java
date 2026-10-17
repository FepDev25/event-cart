package com.eventcart.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI eventCartOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EventCart API")
                        .version("v1")
                        .description("Limited Commerce Engine — lanzamientos, reservas y checkout simulado"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("Token de sesión opaco")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
