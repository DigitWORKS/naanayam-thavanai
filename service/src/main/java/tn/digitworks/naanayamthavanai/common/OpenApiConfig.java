package tn.digitworks.naanayamthavanai.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI metadata. Declares the JWT bearer scheme so the UI shows "Authorize" and sends
 * {@code Authorization: Bearer <accessToken>} on every call. Public endpoints (login, register,
 * refresh) ignore the header.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI naanayamOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Naanayam Thavanai API").version("1.0.0"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the accessToken from POST /auth/login (without 'Bearer ')")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
