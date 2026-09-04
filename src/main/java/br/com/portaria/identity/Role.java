package br.com.portaria.identity;

/**
 * Papeis da Fase 2. Os nomes vao para o banco como texto (user_role_ck) e para
 * o token como claim, sem o prefixo ROLE_ — o prefixo e detalhe do Spring
 * Security e nao deve vazar para o contrato do JWT nem para o schema.
 */
public enum Role {
    ORGANIZER, GATE, BUYER
}
