package br.com.compnuvem.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScriptRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(max = 262144) String conteudo
) {
}
