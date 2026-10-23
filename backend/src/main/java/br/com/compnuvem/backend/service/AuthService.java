package br.com.compnuvem.backend.service;

import br.com.compnuvem.backend.dto.*;
import br.com.compnuvem.backend.entity.*;
import br.com.compnuvem.backend.repository.*;
import br.com.compnuvem.backend.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final UsuarioRepository usuarios;
    private final SessaoRepository sessoes;
    private final PasswordEncoder encoder;
    private final UsuarioAtual atual;
    private final SecureRandom random = new SecureRandom();
    private final String hashInexistente;

    public AuthService(UsuarioRepository usuarios, SessaoRepository sessoes,
                       PasswordEncoder encoder, UsuarioAtual atual) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.encoder = encoder;
        this.atual = atual;
        this.hashInexistente = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Map<String, Object> cadastrar(CadastroRequest dados) {
        validarSenha(dados.senha());
        String email = normalizarEmail(dados.email());
        if (usuarios.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado");
        }
        Usuario usuario = new Usuario();
        usuario.setNome(dados.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(encoder.encode(dados.senha()));
        return respostaUsuario(usuarios.saveAndFlush(usuario));
    }

    @Transactional
    public Map<String, Object> entrar(LoginRequest dados) {
        validarSenha(dados.senha());
        Usuario usuario = usuarios.findByEmail(normalizarEmail(dados.email())).orElse(null);
        boolean confere = encoder.matches(dados.senha(),
                usuario == null ? hashInexistente : usuario.getSenhaHash());
        if (usuario == null || !confere) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou senha invalidos");
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Sessao sessao = new Sessao();
        sessao.setUsuario(usuario);
        sessao.setTokenHash(hashToken(token));
        sessao.setExpiraEm(LocalDateTime.now(ZoneOffset.UTC).plusHours(12));
        sessoes.saveAndFlush(sessao);
        return Map.of("token", token, "tipo", "Bearer", "expiraEm", sessao.getExpiraEm(),
                "usuario", respostaUsuario(usuario));
    }

    public UsuarioAutenticado autenticarToken(String token) {
        if (token == null || token.length() != 43) {
            return null;
        }
        return sessoes.findByTokenHashAndExpiraEmAfter(hashToken(token), LocalDateTime.now(ZoneOffset.UTC))
                .map(sessao -> new UsuarioAutenticado(sessao.getUsuario().getId(),
                        sessao.getUsuario().getEmail()))
                .orElse(null);
    }

    public Map<String, Object> consultarAtual() {
        return respostaUsuario(usuarios.findById(atual.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessao invalida")));
    }

    @Transactional
    public void sair(String authorization) {
        sessoes.deleteByTokenHashAndUsuarioId(hashToken(authorization.substring(7)), atual.id());
    }

    private Map<String, Object> respostaUsuario(Usuario usuario) {
        return Map.of("id", usuario.getId(), "nome", usuario.getNome(), "email", usuario.getEmail());
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validarSenha(String senha) {
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha deve ter no maximo 72 bytes UTF-8");
        }
    }

    public static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }
}
