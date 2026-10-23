package br.com.compnuvem.backend.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
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
}
