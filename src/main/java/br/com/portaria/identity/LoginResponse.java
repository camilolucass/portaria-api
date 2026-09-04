package br.com.portaria.identity;

import java.time.Instant;
import java.util.List;

public record LoginResponse(String token, String tokenType, Instant expiresAt, List<String> roles) {

    static LoginResponse from(TokenService.IssuedToken issued) {
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(), issued.roles());
    }
}
