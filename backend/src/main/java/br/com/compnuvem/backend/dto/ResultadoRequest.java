package br.com.compnuvem.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResultadoRequest(@NotBlank String status, @NotNull Integer codigoSaida) {
}
