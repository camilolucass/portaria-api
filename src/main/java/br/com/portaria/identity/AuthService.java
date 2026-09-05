package br.com.portaria.identity;

import br.com.portaria.shared.exception.InvalidCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository repository;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttempts;

    public AuthService(AuthenticationManager authenticationManager,
                       AppUserRepository repository,
                       TokenService tokenService,
                       LoginAttemptService loginAttempts) {
        this.authenticationManager = authenticationManager;
        this.repository = repository;
        this.tokenService = tokenService;
        this.loginAttempts = loginAttempts;
    }

    /**
     * E-mail inexistente, senha errada e conta desabilitada saem todos como a
     * mesma InvalidCredentialsException, pelo mesmo motivo do codigo de QR
     * (RN-10): a resposta nao pode dizer quais e-mails tem conta neste sistema.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String clientAddress) {
        String email = AppUserDetailsService.normalize(request.email());

        // antes do BCrypt, de proposito: tentativa bloqueada nao consome hash
        loginAttempts.assertNotBlocked(clientAddress, email);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException e) {
            loginAttempts.recordFailure(clientAddress, email);
            throw new InvalidCredentialsException();
        }

        AppUser user = repository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        loginAttempts.recordSuccess(clientAddress, email);
        return LoginResponse.from(tokenService.issueFor(user));
    }
}
