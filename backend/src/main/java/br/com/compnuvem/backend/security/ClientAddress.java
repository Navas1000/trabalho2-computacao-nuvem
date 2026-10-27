package br.com.compnuvem.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientAddress {
    public String obter(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        // Nginx local sobrescreve X-Real-IP. Nunca confiar em X-Forwarded-For do cliente.
        if ("127.0.0.1".equals(peer) || "::1".equals(peer) || "0:0:0:0:0:0:0:1".equals(peer)) {
            String real = request.getHeader("X-Real-IP");
            if (real != null && real.matches("[0-9a-fA-F:.]{3,45}")) {
                return real;
            }
        }
        return peer;
    }
}
