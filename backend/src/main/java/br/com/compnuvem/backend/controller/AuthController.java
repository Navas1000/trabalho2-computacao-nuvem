package br.com.compnuvem.backend.controller;

import br.com.compnuvem.backend.dto.*;
import br.com.compnuvem.backend.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import br.com.compnuvem.backend.security.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final LoginRateLimiter rateLimiter;
    private final ClientAddress clientAddress;

    public AuthController(AuthService auth, LoginRateLimiter rateLimiter, ClientAddress clientAddress) {
        this.auth = auth;
        this.rateLimiter = rateLimiter;
        this.clientAddress = clientAddress;
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> cadastrar(@Valid @RequestBody CadastroRequest dados) {
        return auth.cadastrar(dados);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> entrar(@Valid @RequestBody LoginRequest dados,
                                                       HttpServletRequest request) {
        rateLimiter.verificar(clientAddress.obter(request), dados.email());
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
