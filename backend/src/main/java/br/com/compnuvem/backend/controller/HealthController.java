package br.com.compnuvem.backend.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate db;

    public HealthController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    public Map<String, String> verificar() {
        db.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "UP");
    }
}
