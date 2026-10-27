package br.com.compnuvem.backend.exception;

import br.com.compnuvem.backend.security.LoginRateLimitException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(LoginRateLimitException.class)
    public ResponseEntity<?> limiteLogin(LoginRateLimitException exception) {
        return ResponseEntity.status(429).header("Retry-After", Long.toString(exception.getRetryAfter()))
                .body(Map.of("erro", "Muitas tentativas de login. Aguarde e tente novamente."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> conflito(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(
                Map.of("erro", "Registro referenciado ou dados incompativeis com o banco"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> dadosInvalidos(MethodArgumentNotValidException exception) {
        var campos = exception.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(Map.of("erro", "Dados invalidos", "campos", campos));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> jsonInvalido(HttpMessageNotReadableException exception) {
        // Nao devolver o corpo recebido nem mensagens internas de desserializacao.
        return ResponseEntity.badRequest().body(Map.of("erro", "JSON invalido"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> uploadGrande(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413).body(Map.of("erro", "Arquivo excede o limite permitido"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> respostaEsperada(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("erro", exception.getReason() == null ? "Requisicao recusada" : exception.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> erroInterno(Exception exception) {
        // Sem exception.getMessage()/stack trace: causas JDBC podem conter segredos.
        return ResponseEntity.internalServerError().body(Map.of("erro", "Erro interno ao processar requisicao"));
    }
}
