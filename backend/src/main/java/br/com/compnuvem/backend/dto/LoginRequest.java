package br.com.compnuvem.backend.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String senha
) {
    @Override
    public String toString() {
        return "LoginRequest[credenciais protegidas]";
    }
}
