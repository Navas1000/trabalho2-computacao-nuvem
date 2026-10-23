package br.com.compnuvem.backend.security;

import br.com.compnuvem.backend.service.AuthService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class TokenFilter extends OncePerRequestFilter {
    private final AuthService auth;

    public TokenFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api/health") || path.equals("/api/auth/login")
                || path.equals("/api/auth/cadastro") || request.getMethod().equals("OPTIONS");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            UsuarioAutenticado usuario = header.startsWith("Bearer ")
                    ? auth.autenticarToken(header.substring(7)) : null;
            if (usuario == null) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"erro\":\"Sessao invalida ou expirada\"}");
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(usuario, null,
                    List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}
