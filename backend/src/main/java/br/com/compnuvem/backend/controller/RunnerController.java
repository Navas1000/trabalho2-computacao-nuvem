package br.com.compnuvem.backend.controller;

import br.com.compnuvem.backend.dto.*;
import br.com.compnuvem.backend.service.RunnerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/runner")
public class RunnerController {
    private final RunnerService service;

    public RunnerController(RunnerService service) {
        this.service = service;
    }

    @GetMapping("/fila")
    public List<Map<String, Object>> listarFila(@RequestHeader("X-Runner-Token") String token) {
        return service.listarFila(token);
    }

    @GetMapping("/execucoes")
    public List<Map<String, Object>> listarAtivas(@RequestHeader("X-Runner-Token") String token) {
        return service.listarAtivas(token);
    }

    @GetMapping("/execucoes/{id}")
    public Map<String, Object> consultar(@PathVariable long id,
                                        @RequestHeader("X-Runner-Token") String token) {
        return service.consultar(id, token);
    }

    @PostMapping("/execucoes/{id}/assumir")
    public Map<String, Object> assumir(@PathVariable long id,
                                      @RequestHeader("X-Runner-Token") String token) {
        return service.assumir(id, token);
    }

    @PostMapping("/execucoes/{id}/saida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adicionarSaida(@PathVariable long id, @RequestHeader("X-Runner-Token") String token,
                              @Valid @RequestBody SaidaRequest dados) {
        service.adicionarSaida(id, token, dados);
    }

    @PostMapping("/execucoes/{id}/finalizar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finalizar(@PathVariable long id, @RequestHeader("X-Runner-Token") String token,
                          @Valid @RequestBody ResultadoRequest dados) {
        service.finalizar(id, token, dados);
    }
}
