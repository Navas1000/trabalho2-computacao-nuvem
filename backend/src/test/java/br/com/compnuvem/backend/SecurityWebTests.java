package br.com.compnuvem.backend;

import br.com.compnuvem.backend.dto.*;
import br.com.compnuvem.backend.service.AuthService;
import br.com.compnuvem.backend.security.LoginRateLimiter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:defesas;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.runner-token=test-token-with-at-least-32-characters"
})
class SecurityWebTests {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy chain;
    @Autowired AuthService auth;
    @Autowired LoginRateLimiter limiter;
    MockMvc http;
    String email;

    @BeforeEach
    void preparar() {
        ((java.util.Map<?, ?>)org.springframework.test.util.ReflectionTestUtils.getField(limiter, "janelas")).clear();
        email = java.util.UUID.randomUUID() + "@teste.com";
        auth.cadastrar(new CadastroRequest("Teste", email, "Senha123!"));
        String token = (String) auth.entrar(new LoginRequest(email, "Senha123!")).get("token");
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(chain)
                .defaultRequest(get("/").header("Authorization", "Bearer " + token)).build();
    }

    @Test
    void limitaLoginEInformaEspera() throws Exception {
        String json = "{\"email\":\"" + email + "\",\"senha\":\"errada\"}";
        for (int i = 0; i < 5; i++) {
            http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnauthorized());
        }
        http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json)
                        .header("X-Forwarded-For", "203.0.113.42"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void headersProtegemAmbasAsCadeias() throws Exception {
        http.perform(get("/api/health"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'none'")));
        http.perform(get("/api/runner/fila").header("X-Runner-Token", "invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void limitaBytesUtf8NaCriacaoEAtualizacao() throws Exception {
        String grande = "{\"nome\":\"grande\",\"conteudo\":\"" + "é".repeat(131073) + "\"}";
        http.perform(post("/api/scripts").contentType(MediaType.APPLICATION_JSON).content(grande))
                .andExpect(status().isPayloadTooLarge());
        http.perform(put("/api/scripts/1").contentType(MediaType.APPLICATION_JSON).content(grande))
                .andExpect(status().isPayloadTooLarge());
        http.perform(post("/api/scripts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"limite\",\"conteudo\":\"" + "a".repeat(262144) + "\"}"))
                .andExpect(status().isCreated());
        http.perform(post("/api/scripts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"vazio\",\"conteudo\":\"   \"}"))
                .andExpect(status().isBadRequest());
        MockMultipartFile vazio = new MockMultipartFile("arquivo", "vazio.sh", "text/plain", "   ".getBytes());
        http.perform(multipart("/api/scripts/upload").file(vazio).param("nome", "vazio"))
                .andExpect(status().isBadRequest());
        MockMultipartFile maior = new MockMultipartFile("arquivo", "grande.sh", "text/plain", new byte[262145]);
        http.perform(multipart("/api/scripts/upload").file(maior).param("nome", "grande"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void naoExibeSegredosEmErros() throws Exception {
        http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("x".repeat(4097)))
                .andExpect(status().isPayloadTooLarge());
        http.perform(post("/api/scripts").contentType(MediaType.APPLICATION_JSON)
                        .content("{senha:DB_PASSWORD_SUPER_SECRETO,token:RUNNER_TOKEN_SUPER_SECRETO"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SUPER_SECRETO"))));
        var resposta = new br.com.compnuvem.backend.exception.ApiErrors()
                .erroInterno(new RuntimeException("jdbc:mysql://root:senha@localhost/compnuvem RUNNER_TOKEN"));
        assertFalse(resposta.getBody().toString().contains("jdbc:"));
        assertFalse(resposta.getBody().toString().contains("RUNNER_TOKEN"));
    }
}
