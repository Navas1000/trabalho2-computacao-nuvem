package br.com.compnuvem.backend.controller;

import br.com.compnuvem.backend.dto.ScriptRequest;
import br.com.compnuvem.backend.service.ScriptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scripts")
public class ScriptController {
    private final ScriptService service;

    public ScriptController(ScriptService service) {
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> criar(@Valid @RequestBody ScriptRequest dados) {
        return service.criar(dados);
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> upload(@RequestParam String nome,
                                      @RequestParam MultipartFile arquivo) throws IOException {
        return service.upload(nome, arquivo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> atualizar(@PathVariable long id, @Valid @RequestBody ScriptRequest dados) {
        return service.atualizar(id, dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable long id) {
        service.excluir(id);
    }
}
