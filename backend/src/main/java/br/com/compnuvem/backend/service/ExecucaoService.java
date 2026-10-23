package br.com.compnuvem.backend.service;

import br.com.compnuvem.backend.dto.*;
import br.com.compnuvem.backend.entity.*;
import br.com.compnuvem.backend.repository.ExecucaoRepository;
import br.com.compnuvem.backend.security.UsuarioAtual;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import static br.com.compnuvem.backend.service.EstadosExecucao.*;

@Service
@Transactional(readOnly = true)
public class ExecucaoService {
    private final ExecucaoRepository execucoes;
    private final AmbienteService ambientes;
    private final ScriptService scripts;
    private final UsuarioAtual usuarioAtual;

    public ExecucaoService(ExecucaoRepository execucoes, AmbienteService ambientes, ScriptService scripts, UsuarioAtual usuarioAtual) {
        this.execucoes = execucoes;
        this.ambientes = ambientes;
        this.scripts = scripts;
        this.usuarioAtual = usuarioAtual;
    }

    public Execucao buscarEntidade(long id) {
        return execucoes.findByIdAndUsuarioId(id, usuarioAtual.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Execucao inexistente"));
    }

    @Transactional
    public Execucao bloquear(long id) {
        return execucoes.buscarDoUsuarioComBloqueio(id, usuarioAtual.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Execucao inexistente"));
    }

    public List<Map<String, Object>> listar() {
        return execucoes.findTop500ByUsuarioIdOrderByIdDesc(usuarioAtual.id()).stream()
                .map(execucao -> Respostas.execucao(execucao, false)).toList();
    }

    public Map<String, Object> buscar(long id) {
        return Respostas.execucao(buscarEntidade(id), true);
    }

    public String lerSaida(long id) {
        return buscarEntidade(id).getSaida();
    }

    @Transactional
    public Map<String, Object> criar(long ambienteId, ExecucaoRequest dados) {
        Ambiente ambiente = ambientes.bloquear(ambienteId);
        ambientes.verificarDisponivel(ambienteId);
        Script script = scripts.buscarEntidade(dados.scriptId());

        Execucao execucao = new Execucao();
        execucao.setUsuario(ambiente.getUsuario());
        execucao.setAmbiente(ambiente);
        execucao.setScript(script);
        execucao.setScriptConteudo(script.getConteudo());
        execucao.setStatus(PENDENTE);
        execucao.setSaida("");
        return Respostas.execucao(execucoes.saveAndFlush(execucao), true);
    }

    @Transactional
    public Map<String, Object> parar(long id) {
        Execucao execucao = bloquear(id);
        if (PENDENTE.equals(execucao.getStatus())) {
            execucao.setStatus(CANCELADO);
        } else if (EM_EXECUCAO.equals(execucao.getStatus())) {
            execucao.setStatus(PARADA_SOLICITADA);
        }
        return Respostas.execucao(execucoes.saveAndFlush(execucao), true);
    }

    @Transactional
    public void excluir(long id) {
        Execucao execucao = bloquear(id);
        if (!FINAIS.contains(execucao.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pare a execucao antes de excluir");
        }
        execucoes.delete(execucao);
        execucoes.flush();
    }
}
