package br.com.compnuvem.backend.service;

import java.util.Set;

public final class EstadosExecucao {
    public static final String PENDENTE = "PENDENTE";
    public static final String EM_EXECUCAO = "EM_EXECUCAO";
    public static final String PARADA_SOLICITADA = "PARADA_SOLICITADA";
    public static final String TERMINADO = "TERMINADO";
    public static final String ERRO = "ERRO";
    public static final String CANCELADO = "CANCELADO";
    public static final Set<String> ATIVOS = Set.of(PENDENTE, EM_EXECUCAO, PARADA_SOLICITADA);
    public static final Set<String> FINAIS = Set.of(TERMINADO, ERRO, CANCELADO);

    private EstadosExecucao() {
    }
}
