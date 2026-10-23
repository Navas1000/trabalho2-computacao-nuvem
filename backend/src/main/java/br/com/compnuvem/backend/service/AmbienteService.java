package br.com.compnuvem.backend.service;

import br.com.compnuvem.backend.dto.*;
import br.com.compnuvem.backend.entity.Ambiente;
import br.com.compnuvem.backend.repository.*;
import br.com.compnuvem.backend.security.UsuarioAtual;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AmbienteService {
    private final AmbienteRepository ambientes;
    private final ExecucaoRepository execucoes;
    private final UsuarioAtual usuarioAtual;
    private final UsuarioRepository usuarios;

    public AmbienteService(AmbienteRepository ambientes, ExecucaoRepository execucoes,
                          UsuarioAtual usuarioAtual, UsuarioRepository usuarios) {
        this.ambientes = ambientes;
        this.execucoes = execucoes;
        this.usuarioAtual = usuarioAtual;
        this.usuarios = usuarios;
    }

    public Ambiente buscarEntidade(long id) {
        return ambientes.findByIdAndUsuarioId(id, usuarioAtual.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambiente inexistente"));
    }

    /** Deve ser chamado dentro da transacao que modifica ou usa o ambiente. */
    @Transactional
    public Ambiente bloquear(long id) {
        return ambientes.buscarComBloqueio(id, usuarioAtual.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambiente inexistente"));
    }

    public void verificarDisponivel(long id) {
        if (execucoes.existsByAmbienteIdAndStatusIn(id, EstadosExecucao.ATIVOS)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ambiente com execucao ativa");
        }
    }

    public List<Map<String, Object>> listar() {
        return ambientes.findTop500ByUsuarioIdOrderByIdDesc(usuarioAtual.id()).stream().map(Respostas::ambiente).toList();
    }

    public Map<String, Object> buscar(long id) {
        return Respostas.ambiente(buscarEntidade(id));
    }

    @Transactional
    public Map<String, Object> criar(AmbienteRequest dados) {
        Ambiente ambiente = new Ambiente();
        ambiente.setUsuario(usuarios.getReferenceById(usuarioAtual.id()));
        preencher(ambiente, dados);
        return Respostas.ambiente(ambientes.saveAndFlush(ambiente));
    }

    @Transactional
    public Map<String, Object> atualizar(long id, AmbienteRequest dados) {
        Ambiente ambiente = bloquear(id);
        verificarDisponivel(id);
        preencher(ambiente, dados);
        return Respostas.ambiente(ambientes.saveAndFlush(ambiente));
    }

    @Transactional
    public void excluir(long id) {
        Ambiente ambiente = bloquear(id);
        verificarDisponivel(id);
        execucoes.deleteByAmbienteId(id);
        execucoes.flush();
        ambientes.delete(ambiente);
        ambientes.flush();
    }

    private void preencher(Ambiente ambiente, AmbienteRequest dados) {
        ambiente.setNome(dados.nome());
        ambiente.setCpuMillicores(dados.cpuMillicores());
        ambiente.setMemoriaMb(dados.memoriaMb());
        ambiente.setIoBytesSegundo(dados.ioBytesSegundo());
    }
}
