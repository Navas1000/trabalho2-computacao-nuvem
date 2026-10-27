package br.com.compnuvem.backend.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Limita o corpo antes da desserializacao JSON, inclusive em transferencia chunked. */
public class PayloadLimitFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        String type = request.getContentType();
        boolean multipart = type != null && type.toLowerCase(java.util.Locale.ROOT).startsWith("multipart/");
        if (!path.startsWith("/api/") || multipart
                || !(method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
            chain.doFilter(request, response);
            return;
        }
        // JSON pode representar um byte como seis caracteres escapados (escape Unicode).
        int limite = path.equals("/api/auth/login") ? 4096
                : path.equals("/api/auth/cadastro") ? 8192 : 2 * 1024 * 1024;
        if (request.getContentLengthLong() > limite) {
            rejeitar(response);
            return;
        }
        byte[] bytes = request.getInputStream().readNBytes(limite + 1);
        if (bytes.length > limite) {
            rejeitar(response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Leitura assincrona nao utilizada pela API");
                    }
                };
            }

            @Override
            public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }

    private void rejeitar(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"erro\":\"Corpo da requisicao excede o limite permitido\"}");
    }
}
