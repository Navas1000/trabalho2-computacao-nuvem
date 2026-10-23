package br.com.compnuvem.backend.controller;

import br.com.compnuvem.backend.dto.*;
import br.com.compnuvem.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> cadastrar(@Valid @RequestBody CadastroRequest dados) {
        return auth.cadastrar(dados);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> entrar(@Valid @RequestBody LoginRequest dados) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.entrar(dados));
    }

    @GetMapping("/me")
    public Map<String, Object> consultarAtual() {
        return auth.consultarAtual();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sair(@RequestHeader("Authorization") String authorization) {
        auth.sair(authorization);
    }
}
