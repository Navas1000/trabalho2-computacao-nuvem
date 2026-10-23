package br.com.compnuvem.backend.controller;

import br.com.compnuvem.backend.dto.*;
import br.com.compnuvem.backend.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ambientes")
public class AmbienteController {
    private final AmbienteService ambientes;
    private final ExecucaoService execucoes;

    public AmbienteController(AmbienteService ambientes, ExecucaoService execucoes) {
        this.ambientes = ambientes;
        this.execucoes = execucoes;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return ambientes.listar();
    }

    @GetMapping("/{id}")
    public Map<String, Object> buscar(@PathVariable long id) {
        return ambientes.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> criar(@Valid @RequestBody AmbienteRequest dados) {
        return ambientes.criar(dados);
    }

    @PutMapping("/{id}")
    public Map<String, Object> atualizar(@PathVariable long id, @Valid @RequestBody AmbienteRequest dados) {
        return ambientes.atualizar(id, dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable long id) {
        ambientes.excluir(id);
    }

    @PostMapping("/{id}/execucoes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> executar(@PathVariable long id, @Valid @RequestBody ExecucaoRequest dados) {
        return execucoes.criar(id, dados);
    }
}
