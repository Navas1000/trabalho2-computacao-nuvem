package br.com.compnuvem.backend.controller;

import br.com.compnuvem.backend.service.ExecucaoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/execucoes")
public class ExecucaoController {
    private final ExecucaoService service;

    public ExecucaoController(ExecucaoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public Map<String, Object> buscar(@PathVariable long id) {
        return service.buscar(id);
    }

    @GetMapping(value = "/{id}/saida", produces = "text/plain;charset=UTF-8")
    public String lerSaida(@PathVariable long id) {
        return service.lerSaida(id);
    }

    @PostMapping("/{id}/parar")
    public Map<String, Object> parar(@PathVariable long id) {
        return service.parar(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable long id) {
        service.excluir(id);
    }
}
