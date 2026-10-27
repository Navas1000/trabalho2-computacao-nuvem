package br.com.compnuvem.backend.service;

import br.com.compnuvem.backend.dto.Respostas;
import br.com.compnuvem.backend.dto.ScriptRequest;
import br.com.compnuvem.backend.entity.Script;
import br.com.compnuvem.backend.repository.*;
import br.com.compnuvem.backend.security.UsuarioAtual;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ScriptService {
    private final ScriptRepository scripts;
    private final ExecucaoRepository execucoes;
    private final UsuarioAtual usuarioAtual;
    private final UsuarioRepository usuarios;

    public ScriptService(ScriptRepository scripts, ExecucaoRepository execucoes,
                          UsuarioAtual usuarioAtual, UsuarioRepository usuarios) {
        this.scripts = scripts;
        this.execucoes = execucoes;
        this.usuarioAtual = usuarioAtual;
        this.usuarios = usuarios;
    }

    public Script buscarEntidade(long id) {
        return scripts.findByIdAndUsuarioId(id, usuarioAtual.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Script inexistente"));
    }

    public List<Map<String, Object>> listar() {
        return scripts.findTop500ByUsuarioIdOrderByIdDesc(usuarioAtual.id()).stream()
                .map(script -> Respostas.script(script, false)).toList();
    }

    public Map<String, Object> buscar(long id) {
        return Respostas.script(buscarEntidade(id), true);
    }

    @Transactional
    public Map<String, Object> criar(ScriptRequest dados) {
        validarConteudo(dados.conteudo());
        Script script = new Script();
        script.setUsuario(usuarios.getReferenceById(usuarioAtual.id()));
        script.setNome(dados.nome());
        script.setConteudo(dados.conteudo());
        return Respostas.script(scripts.saveAndFlush(script), true);
    }

    @Transactional
    public Map<String, Object> atualizar(long id, ScriptRequest dados) {
        validarConteudo(dados.conteudo());
        Script script = buscarEntidade(id);
        script.setNome(dados.nome());
        script.setConteudo(dados.conteudo());
        return Respostas.script(scripts.saveAndFlush(script), true);
    }

    private void validarConteudo(String conteudo) {
        if (conteudo == null || conteudo.isBlank() || conteudo.indexOf(0) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie um script de texto nao vazio");
        }
        if (conteudo.getBytes(StandardCharsets.UTF_8).length > 256 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Script excede 256 KiB em UTF-8");
        }
    }

    @Transactional
    public void excluir(long id) {
        Script script = buscarEntidade(id);
        if (execucoes.existsByScriptId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Script referenciado por uma execucao");
        }
        scripts.delete(script);
        scripts.flush();
    }

    @Transactional
    public Map<String, Object> upload(String nome, MultipartFile arquivo) throws IOException {
        if (arquivo.getSize() > 262144) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo excede 256 KiB");
        }
        if (nome.isBlank() || nome.length() > 120 || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome ou arquivo invalido");
        }

        String conteudo;
        try {
            conteudo = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(arquivo.getBytes())).toString();
        } catch (CharacterCodingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo deve ser UTF-8");
        }

        if (conteudo.isBlank() || conteudo.indexOf(0) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie um script de texto");
        }
        return criar(new ScriptRequest(nome, conteudo));
    }
}
