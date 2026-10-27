package br.com.compnuvem.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SecurityLimitsTests {
    static class Relogio extends Clock {
        Instant agora = Instant.parse("2026-10-26T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return agora; }
    }

    @Test
    void limitePorIpEContaExpiraSemDormir() {
        Relogio relogio = new Relogio();
        LoginRateLimiter limiter = new LoginRateLimiter(relogio);
        for (int i = 0; i < 5; i++) limiter.verificar("1.2.3.4", "conta" + i + "@teste.com");
        assertThrows(LoginRateLimitException.class, () -> limiter.verificar("1.2.3.4", "nova@teste.com"));
        for (int i = 0; i < 5; i++) limiter.verificar("2.3.4." + i, "ALVO@TESTE.COM");
        assertThrows(LoginRateLimitException.class, () -> limiter.verificar("outro", " alvo@teste.com "));
        relogio.agora = relogio.agora.plusSeconds(60);
        assertDoesNotThrow(() -> limiter.verificar("1.2.3.4", "alvo@teste.com"));
    }

    @Test
    void reservaAtomicaEmTentativasSimultaneas() throws Exception {
        LoginRateLimiter limiter = new LoginRateLimiter(new Relogio());
        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger aceitas = new AtomicInteger();
        try {
            var tarefas = new java.util.ArrayList<Callable<Void>>();
            for (int i = 0; i < 20; i++) tarefas.add(() -> {
                try { limiter.verificar("ip", "teste@teste.com"); aceitas.incrementAndGet(); }
                catch (LoginRateLimitException esperado) { }
                return null;
            });
            for (var futuro : pool.invokeAll(tarefas)) futuro.get();
            assertEquals(5, aceitas.get());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void enderecoRealSoEConfiavelNoProxyLocal() {
        ClientAddress address = new ClientAddress();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("198.51.100.1");
        req.addHeader("X-Real-IP", "203.0.113.1");
        assertEquals("198.51.100.1", address.obter(req));
        req.setRemoteAddr("127.0.0.1");
        assertEquals("203.0.113.1", address.obter(req));
    }

    @Test
    void corpoChunkedTambemTemLimiteAntesDoJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setMethod("POST");
        request.setRequestURI("/api/scripts");
        request.setContentType("application/json");
        request.setContent(new byte[2 * 1024 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new PayloadLimitFilter().doFilter(request, response, (req, res) -> fail("Nao deve desserializar"));
        assertEquals(413, response.getStatus());
    }
}
