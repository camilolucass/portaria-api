package br.com.portaria.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolve o usuario autenticado a partir do token.
 *
 * O subject do JWT e o public_id; a conta e relida do banco a cada requisicao
 * que precisa de identidade. E uma consulta a mais, e proposital: um token vale
 * 60 minutos, e sem reler ninguem perde acesso ao ser desabilitado dentro
 * dessa janela.
 */
@Service
public class CurrentUserService {

    private final AppUserRepository repository;

    public CurrentUserService(AppUserRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AppUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Requisicao sem autenticacao chegou a uma rota protegida");
        }

        UUID publicId;
        try {
            publicId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new org.springframework.security.access.AccessDeniedException("Token invalido");
        }

        return repository.findByPublicId(publicId)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Conta nao encontrada ou desabilitada"));
    }
}
