package br.com.portaria.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI portariaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Portaria API")
                .version("v1")
                .description("""
                        Venda de ingressos e controle de entrada por QR Code.

                        Quatro decisoes explicam o comportamento desta API:

                        - o estoque e reservado na criacao do pedido, nao no pagamento;
                        - um pedido PENDING expira em 15 minutos e devolve o estoque;
                        - os ingressos so nascem na transicao para PAID;
                        - o codigo do QR e assinado com HMAC-SHA256 e cada ingresso
                          entra uma unica vez, mesmo com varias portarias simultaneas.

                        Erros seguem RFC 7807 (application/problem+json).
                        """)
                .contact(new Contact().name("Lucas Camilo"))
                .license(new License().name("MIT")));
    }
}
