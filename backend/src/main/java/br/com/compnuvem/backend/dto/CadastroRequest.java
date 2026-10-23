package br.com.compnuvem.backend.dto;

import jakarta.validation.constraints.*;

public record CadastroRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String senha
) {
    @Override
    public String toString() {
        return "CadastroRequest[credenciais protegidas]";
    }
}
