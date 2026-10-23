package br.com.compnuvem.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ExecucaoRequest(@NotNull @Positive Long scriptId) {
}
