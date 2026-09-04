package br.com.portaria.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * O Spring Security responde 401 e 403 com corpo vazio, antes de qualquer
 * controller existir — logo, fora do alcance do GlobalExceptionHandler. Sem
 * isto, duas das respostas mais comuns da API seriam as unicas a nao seguir a
 * RFC 7807 que o SPEC exige.
 */
@Component
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Sem token, token expirado ou assinatura invalida. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         org.springframework.security.core.AuthenticationException exception)
            throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "Nao autenticado",
                "Envie um token valido no cabecalho Authorization: Bearer.");
    }

    /** Autenticado, mas sem o papel exigido pela rota. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException exception)
            throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "Acesso negado",
                "Sua conta nao tem permissao para esta operacao.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", LocalDateTime.now());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
