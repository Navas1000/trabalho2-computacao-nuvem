package br.com.compnuvem.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UsuarioAtual {
    public long id() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login necessario");
        }
        return usuario.id();
    }
}
