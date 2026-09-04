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

    public AuthService(AuthenticationManager authenticationManager,
                       AppUserRepository repository,
                       TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.repository = repository;
        this.tokenService = tokenService;
    }

    /**
     * E-mail inexistente, senha errada e conta desabilitada saem todos como a
     * mesma InvalidCredentialsException, pelo mesmo motivo do codigo de QR
     * (RN-10): a resposta nao pode dizer quais e-mails tem conta neste sistema.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = AppUserDetailsService.normalize(request.email());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException();
        }

        AppUser user = repository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        return LoginResponse.from(tokenService.issueFor(user));
    }
}
