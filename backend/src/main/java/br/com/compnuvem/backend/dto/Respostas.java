package br.com.compnuvem.backend.dto;

import br.com.compnuvem.backend.entity.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mantem os nomes JSON existentes sem serializar relacionamentos JPA. */
public final class Respostas {
    private Respostas() {
    }

    public static Map<String, Object> script(Script script, boolean incluirConteudo) {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("id", script.getId());
        resposta.put("usuario_id", script.getUsuario().getId());
        resposta.put("nome", script.getNome());
        if (incluirConteudo) {
            resposta.put("conteudo", script.getConteudo());
        }
        resposta.put("criado_em", script.getCriadoEm());
        return resposta;
    }

    public static Map<String, Object> ambiente(Ambiente ambiente) {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("id", ambiente.getId());
        resposta.put("usuario_id", ambiente.getUsuario().getId());
        resposta.put("nome", ambiente.getNome());
        resposta.put("cpu_millicores", ambiente.getCpuMillicores());
        resposta.put("memoria_mb", ambiente.getMemoriaMb());
        resposta.put("io_bytes_segundo", ambiente.getIoBytesSegundo());
        resposta.put("criado_em", ambiente.getCriadoEm());
        return resposta;
    }

    public static Map<String, Object> execucao(Execucao execucao, boolean incluirScript) {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("id", execucao.getId());
        resposta.put("usuario_id", execucao.getUsuario().getId());
        resposta.put("ambiente_id", execucao.getAmbiente().getId());
        resposta.put("script_id", execucao.getScript().getId());
        if (incluirScript) {
            resposta.put("script_conteudo", execucao.getScriptConteudo());
        }
        resposta.put("status", execucao.getStatus());
        resposta.put("codigo_saida", execucao.getCodigoSaida());
        resposta.put("criado_em", execucao.getCriadoEm());
        resposta.put("atualizado_em", execucao.getAtualizadoEm());
        return resposta;
    }

    public static Map<String, Object> fila(Execucao execucao) {
        return Map.of("id", execucao.getId(), "ambiente_id", execucao.getAmbiente().getId(),
                "status", execucao.getStatus());
    }
}
