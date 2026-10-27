package br.com.compnuvem.backend.security;

public class LoginRateLimitException extends RuntimeException {
    private final long retryAfter;

    public LoginRateLimitException(long retryAfter) {
        super("Muitas tentativas de login. Aguarde e tente novamente.");
        this.retryAfter = retryAfter;
    }

    public long getRetryAfter() {
        return retryAfter;
    }
}
