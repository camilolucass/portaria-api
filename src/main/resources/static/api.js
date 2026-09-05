/**
 * Cliente da API. Um lugar so para o token, para o cabecalho Authorization e
 * para o tratamento de erro — as paginas nao falam com fetch diretamente.
 */

const TOKEN_KEY = 'portaria.token';
const ROLES_KEY = 'portaria.roles';

/*
 * O token fica em localStorage. E o mesmo compromisso de qualquer SPA sem
 * cookie httpOnly: se houver XSS na pagina, o token vaza. Aceitavel aqui porque
 * a aplicacao nao renderiza HTML vindo do servidor nem de terceiros; num sistema
 * com dinheiro real, o certo e cookie httpOnly + SameSite e um refresh token.
 */
export const auth = {
    save(token, roles) {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(ROLES_KEY, JSON.stringify(roles || []));
    },
    token() {
        return localStorage.getItem(TOKEN_KEY);
    },
    roles() {
        try {
            return JSON.parse(localStorage.getItem(ROLES_KEY)) || [];
        } catch {
            return [];
        }
    },
    has(role) {
        return this.roles().includes(role);
    },
    clear() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(ROLES_KEY);
    },
    /** Toda pagina interna chama isto: sem token, nao ha o que mostrar. */
    require() {
        if (!this.token()) {
            location.replace('/index.html');
            throw new Error('sem token');
        }
    },
};

/** Erro que carrega o ProblemDetail devolvido pela API. */
export class ApiError extends Error {
    constructor(status, problem) {
        super(problem?.detail || problem?.title || `Erro ${status}`);
        this.status = status;
        this.title = problem?.title || 'Erro';
        this.fields = problem?.fields || null;
    }
}

async function request(path, { method = 'GET', body } = {}) {
    const headers = { Accept: 'application/json' };
    const token = auth.token();
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    const response = await fetch(path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
    });

    // token expirado ou invalido: nao adianta mostrar erro, o caminho e refazer login
    if (response.status === 401) {
        auth.clear();
        location.replace('/index.html');
        throw new Error('sessao expirada');
    }

    if (!response.ok) {
        let problem = null;
        try {
            problem = await response.json();
        } catch {
            /* resposta sem corpo */
        }
        throw new ApiError(response.status, problem);
    }

    if (response.status === 204) return null;
    return response.json();
}

export const api = {
    get: (path) => request(path),
    post: (path, body) => request(path, { method: 'POST', body }),

    async login(email, password) {
        const response = await fetch('/api/v1/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }),
        });
        if (!response.ok) {
            const problem = await response.json().catch(() => null);
            throw new ApiError(response.status, problem);
        }
        return response.json();
    },

    /**
     * O PNG do QR exige Authorization, entao <img src="..."> nao funciona: o
     * navegador nao manda cabecalho em requisicao de imagem. Busca-se o blob e
     * usa-se uma object URL.
     */
    async qrCodeUrl(ticketId) {
        const response = await fetch(`/api/v1/tickets/${ticketId}/qr`, {
            headers: { Authorization: `Bearer ${auth.token()}` },
        });
        if (!response.ok) throw new ApiError(response.status, null);
        return URL.createObjectURL(await response.blob());
    },
};

/** Centavos como int (convencao do SPEC) exibidos como moeda. */
export function brl(cents) {
    return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

export function dateTime(value) {
    if (!value) return '—';
    return new Date(value).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

/** Escapa antes de injetar no DOM: nomes de evento e titular vem do usuario. */
export function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (c) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    })[c]);
}

export function showMessage(element, text, kind = 'error') {
    element.className = `msg ${kind}`;
    element.textContent = text;
    element.hidden = false;
}

export function mountHeader(title) {
    const roles = auth.roles().join(', ') || 'sem papel';
    document.querySelector('header .who').textContent = roles;
    document.querySelector('header h1').textContent = title;
    document.querySelector('#logout').addEventListener('click', () => {
        auth.clear();
        location.replace('/index.html');
    });
}
