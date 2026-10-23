package br.com.compnuvem.backend.service;

import br.com.compnuvem.backend.dto.*;
import br.com.compnuvem.backend.entity.Execucao;
import br.com.compnuvem.backend.repository.ExecucaoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static br.com.compnuvem.backend.service.EstadosExecucao.*;

@Service
@Transactional(readOnly = true)
public class RunnerService {
    private static final int LIMITE_SAIDA_BYTES = 4 * 1024 * 1024;
    private final ExecucaoRepository execucoes;
    private final byte[] token;

    public RunnerService(ExecucaoRepository execucoes,
                         @Value("${app.runner-token}") String token) {
        if (token.length() < 32) {
            throw new IllegalArgumentException("RUNNER_TOKEN deve ter pelo menos 32 caracteres");
        }
        this.execucoes = execucoes;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    private void autenticar(String recebido) {
        if (!MessageDigest.isEqual(token, recebido.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalido");
        }
    }

    public List<Map<String, Object>> listarFila(String tokenRecebido) {
        autenticar(tokenRecebido);
        return execucoes.findTop100ByStatusInOrderByIdAsc(Set.of(PENDENTE, PARADA_SOLICITADA))
                .stream().map(Respostas::fila).toList();
    }

    public List<Map<String, Object>> listarAtivas(String tokenRecebido) {
        autenticar(tokenRecebido);
        return execucoes.findTop500ByStatusInOrderByIdAsc(ATIVOS).stream()
                .map(execucao -> Respostas.execucao(execucao, false)).toList();
    }

    public Map<String, Object> consultar(long id, String tokenRecebido) {
        autenticar(tokenRecebido);
        Execucao execucao = execucoes.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Execucao inexistente"));
        return Respostas.execucao(execucao, true);
    }

    @Transactional
    public Map<String, Object> assumir(long id, String tokenRecebido) {
        autenticar(tokenRecebido);
        Execucao execucao = bloquear(id);
        if (!PENDENTE.equals(execucao.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Execucao indisponivel");
        }
        execucao.setStatus(EM_EXECUCAO);
        execucoes.saveAndFlush(execucao);
        Map<String, Object> dados = Respostas.execucao(execucao, true);
        dados.put("saida", execucao.getSaida());
        return Map.of("execucao", dados, "ambiente", Respostas.ambiente(execucao.getAmbiente()));
    }

    @Transactional
    public void adicionarSaida(long id, String tokenRecebido, SaidaRequest dados) {
        autenticar(tokenRecebido);
        Execucao execucao = bloquear(id);
        verificarEmExecucao(execucao);
        long tamanho = (long) execucao.getSaida().getBytes(StandardCharsets.UTF_8).length
                + dados.texto().getBytes(StandardCharsets.UTF_8).length;
        if (tamanho > LIMITE_SAIDA_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Limite de saida de 4 MiB atingido");
        }
        execucao.setSaida(execucao.getSaida() + dados.texto());
        execucoes.saveAndFlush(execucao);
    }

    @Transactional
    public void finalizar(long id, String tokenRecebido, ResultadoRequest dados) {
        autenticar(tokenRecebido);
        boolean resultadoInvalido = !FINAIS.contains(dados.status())
                || dados.codigoSaida() == null
                || (TERMINADO.equals(dados.status()) && dados.codigoSaida() != 0)
                || (ERRO.equals(dados.status()) && dados.codigoSaida() == 0);
        if (resultadoInvalido) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resultado invalido");
        }
        Execucao execucao = bloquear(id);
        verificarEmExecucao(execucao);
        execucao.setStatus(dados.status());
        execucao.setCodigoSaida(dados.codigoSaida());
        execucoes.saveAndFlush(execucao);
    }

    private Execucao bloquear(long id) {
        return execucoes.buscarComBloqueio(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Execucao inexistente"));
    }

    private void verificarEmExecucao(Execucao execucao) {
        if (!Set.of(EM_EXECUCAO, PARADA_SOLICITADA).contains(execucao.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Execucao inativa");
        }
    }
}
