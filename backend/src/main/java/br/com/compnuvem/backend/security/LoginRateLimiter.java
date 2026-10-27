package br.com.compnuvem.backend.security;

import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import br.com.compnuvem.backend.service.AuthService;
import org.springframework.stereotype.Component;

/** Janela fixa de um minuto, independente por endereco e por conta. */
@Component
public class LoginRateLimiter {
    private static final int MAX_ENTRADAS = 10000;
    private static final int TENTATIVAS = 5;
    private static final long JANELA_MS = 60000;
    private final Map<String, Janela> janelas = new HashMap<>();
    private final Clock clock;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void verificar(String ip, String email) {
        long agora = clock.millis();
        janelas.entrySet().removeIf(entry -> entry.getValue().fim <= agora);
        String chaveIp = "ip:" + ip;
        String chaveConta = "conta:" + AuthService.hashToken(email.trim().toLowerCase(Locale.ROOT));
        long aguardar = 0;
        for (String chave : new String[]{chaveIp, chaveConta}) {
            Janela janela = janelas.get(chave);
            if (janela != null && janela.quantidade >= TENTATIVAS) {
                aguardar = Math.max(aguardar, janela.fim - agora);
            }
        }
        if (aguardar > 0) {
            throw new LoginRateLimitException((aguardar + 999) / 1000);
        }
        int novas = (janelas.containsKey(chaveIp) ? 0 : 1) + (janelas.containsKey(chaveConta) ? 0 : 1);
        if (janelas.size() + novas > MAX_ENTRADAS) {
            throw new LoginRateLimitException(60);
        }
        // Reservar antes do BCrypt impede que tentativas simultaneas ultrapassem a cota.
        for (String chave : new String[]{chaveIp, chaveConta}) {
            Janela janela = janelas.computeIfAbsent(chave, ignored -> new Janela(agora + JANELA_MS));
            janela.quantidade++;
        }
    }

    private static final class Janela {
        private final long fim;
        private int quantidade;

        private Janela(long fim) {
            this.fim = fim;
        }
    }
}
