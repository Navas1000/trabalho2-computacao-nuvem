package br.com.compnuvem.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaidaRequest(@NotNull @Size(max = 262144) String texto) {
}
