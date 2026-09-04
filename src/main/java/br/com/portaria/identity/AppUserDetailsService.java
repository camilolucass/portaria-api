package br.com.portaria.identity;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Ponte entre o app_user e o Spring Security.
 *
 * Passar pelo DaoAuthenticationProvider em vez de comparar a senha na mao nao e
 * cerimonia: ele executa um hash descartavel quando o e-mail nao existe, para
 * que "usuario inexistente" e "senha errada" levem o mesmo tempo. Sem isso, o
 * tempo de resposta vira um oraculo de quais e-mails tem conta.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository repository;

    public AppUserDetailsService(AppUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = repository.findByEmail(normalize(email))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais invalidas"));

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .authorities(user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .toList())
                .build();
    }

    static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
