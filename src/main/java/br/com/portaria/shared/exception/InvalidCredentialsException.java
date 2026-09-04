package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Uma unica excecao para e-mail inexistente, senha errada e conta desabilitada.
 * A mensagem e identica de proposito: quem tenta entrar nao deve descobrir
 * quais e-mails tem conta.
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Credenciais invalidas",
                "E-mail ou senha incorretos.");
    }
}
