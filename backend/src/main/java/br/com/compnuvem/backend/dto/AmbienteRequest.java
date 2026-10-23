package br.com.compnuvem.backend.dto;

import jakarta.validation.constraints.*;

public record AmbienteRequest(
        @NotBlank @Size(max = 120) String nome,
        @Min(1) @Max(64000) int cpuMillicores,
        @Min(16) @Max(1048576) int memoriaMb,
        @Min(1) long ioBytesSegundo
) {
}
