package br.com.compnuvem.backend;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.file.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class DemoDataTests {
    @Test
    void exemplosTemDonoSenhaValidaEScriptsDentroDoLimite() throws Exception {
        String sql = Files.readString(Path.of(System.getProperty("basedir"), "..", "banco-de-dados", "dados_teste.sql"));
        var hash = Pattern.compile("'(\\$2a\\$12\\$[^']+)'").matcher(sql);
        assertTrue(hash.find());
        assertTrue(new BCryptPasswordEncoder().matches("DemoNuvem2026!", hash.group(1)));
        var conteudos = Pattern.compile("CONVERT\\(0x([0-9A-F]+) USING utf8mb4\\)").matcher(sql);
        int quantidade = 0;
        while (conteudos.find()) {
            byte[] bytes = java.util.HexFormat.of().parseHex(conteudos.group(1));
            assertTrue(bytes.length <= 262144);
            String script = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(script.startsWith("#!/bin/bash\n"));
            assertFalse(script.contains("\r"));
            quantidade++;
        }
        assertEquals(4, quantidade);
        for (String nome : java.util.List.of("teste_cpu.sh", "teste_memoria.sh", "teste_loop_infinito.sh", "teste_isolamento.sh")) {
            assertTrue(sql.contains(nome));
        }
        assertTrue(sql.contains("WHERE NOT EXISTS"));
        assertTrue(sql.contains("@demo_usuario"));
    }
}
