package br.com.compnuvem.backend;

import br.com.compnuvem.backend.dto.*;
import br.com.compnuvem.backend.repository.*;
import br.com.compnuvem.backend.service.*;
import br.com.compnuvem.backend.security.UsuarioAutenticado;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:backend;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.runner-token=test-token-with-at-least-32-characters"
})
class BackendApplicationTests {
    private static final String TOKEN = "test-token-with-at-least-32-characters";

    @Autowired private ScriptService scripts;
    @Autowired private AmbienteService ambientes;
    @Autowired private ExecucaoService execucoes;
    @Autowired private RunnerService runner;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private AmbienteRepository ambienteRepository;
    @Autowired private ExecucaoRepository execucaoRepository;
    @Autowired private WebApplicationContext context;

    @Autowired private AuthService auth;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private SessaoRepository sessaoRepository;
    @Autowired private FilterChainProxy securityFilterChain;

    private String tokenAlice;
    private long usuarioAlice;
    private MockMvc http;
    private long scriptId;
    private long ambienteId;

    @BeforeEach
    void preparar() {
        execucaoRepository.deleteAll();
        ambienteRepository.deleteAll();
        scriptRepository.deleteAll();
        sessaoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioAlice = id(auth.cadastrar(new CadastroRequest("Alice", "alice@teste.com", "Senha123!")));
        tokenAlice = (String) auth.entrar(new LoginRequest("alice@teste.com", "Senha123!")).get("token");
        autenticarAlice();
        http = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .defaultRequest(get("/").header("Authorization", "Bearer " + tokenAlice))
                .build();
        scriptId = id(scripts.criar(new ScriptRequest("teste", "echo original")));
        ambienteId = id(ambientes.criar(new AmbienteRequest("ambiente", 500, 128, 1048576)));
    }

    private void autenticarAlice() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado(usuarioAlice, "alice@teste.com"), null, java.util.List.of()));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private long id(Map<String, Object> resposta) {
        return ((Number) resposta.get("id")).longValue();
    }

    private long criarExecucao() {
        autenticarAlice();
        return id(execucoes.criar(ambienteId, new ExecucaoRequest(scriptId)));
    }

    @Test
    void cicloDeVidaPreservaScriptOriginal() {
        long id = criarExecucao();
        scripts.atualizar(scriptId, new ScriptRequest("editado", "echo novo"));
        assertEquals("echo original", execucoes.buscar(id).get("script_conteudo"));
        assertEquals("PENDENTE", execucoes.buscar(id).get("status"));

        runner.assumir(id, TOKEN);
        runner.adicionarSaida(id, TOKEN, new SaidaRequest("ola\n"));
        assertEquals("ola\n", execucoes.lerSaida(id));
        runner.finalizar(id, TOKEN, new ResultadoRequest("TERMINADO", 0));
        assertEquals("TERMINADO", execucoes.buscar(id).get("status"));
        execucoes.excluir(id);
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> execucoes.buscar(id)).getStatusCode().value());
    }

    @Test
    void bloqueiaExecucaoDuplicadaEExclusaoDeAmbienteAtivo() {
        long id = criarExecucao();
        assertEquals(409, assertThrows(ResponseStatusException.class,
                this::criarExecucao).getStatusCode().value());
        runner.assumir(id, TOKEN);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> runner.assumir(id, TOKEN)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> ambientes.excluir(ambienteId)).getStatusCode().value());
    }

    @Test
    void cancelamentoEToken() {
        long id = criarExecucao();
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> runner.assumir(id, "invalido")).getStatusCode().value());
        execucoes.parar(id);
        assertEquals("CANCELADO", execucoes.buscar(id).get("status"));

        long segundo = criarExecucao();
        runner.assumir(segundo, TOKEN);
        execucoes.parar(segundo);
        assertEquals("PARADA_SOLICITADA", execucoes.buscar(segundo).get("status"));
        runner.finalizar(segundo, TOKEN, new ResultadoRequest("CANCELADO", 143));
        assertEquals("CANCELADO", execucoes.buscar(segundo).get("status"));
    }

    @Test
    void rejeitaResultadoIncoerente() {
        long id = criarExecucao();
        runner.assumir(id, TOKEN);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> runner.finalizar(id, TOKEN, new ResultadoRequest("TERMINADO", 1)))
                .getStatusCode().value());
    }

    @Test
    void httpValidaCorpoEPreservaNomesDosCampos() throws Exception {
        http.perform(post("/api/ambientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"a\",\"cpuMillicores\":500,\"memoriaMb\":-1,\"ioBytesSegundo\":1000}"))
                .andExpect(status().isBadRequest());
        http.perform(get("/api/ambientes/" + ambienteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpu_millicores").value(500))
                .andExpect(jsonPath("$.memoria_mb").value(128));
        http.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void uploadEProtecaoDoHistorico() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "teste.sh", "text/plain",
                "echo teste".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        http.perform(multipart("/api/scripts/upload").file(arquivo).param("nome", "upload"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.conteudo").value("echo teste"));
        criarExecucao();
        http.perform(delete("/api/scripts/" + scriptId)).andExpect(status().isConflict());
    }

    @Test
    void duasSolicitacoesConcorrentesCriamUmaUnicaExecucao() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        Callable<Integer> solicitar = () -> {
            inicio.await();
            try {
                criarExecucao();
                return 201;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };
        try {
            Future<Integer> primeira = pool.submit(solicitar);
            Future<Integer> segunda = pool.submit(solicitar);
            inicio.countDown();
            assertEquals(java.util.Set.of(201, 409), java.util.Set.of(
                    primeira.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS)));
            assertEquals(1, execucaoRepository.count());
        } finally {
            pool.shutdownNow();
        }
    }

    private String cadastrarBob() {
        auth.cadastrar(new CadastroRequest("Bob", "bob@teste.com", "Senha123!"));
        return (String) auth.entrar(new LoginRequest("bob@teste.com", "Senha123!")).get("token");
    }

    @Test
    void cadastroLoginLogoutEHashDaSenha() throws Exception {
        var usuario = usuarioRepository.findById(usuarioAlice).orElseThrow();
        assertNotEquals("Senha123!", usuario.getSenhaHash());
        assertTrue(usuario.getSenhaHash().startsWith("$2"));
        assertNotEquals(tokenAlice, sessaoRepository.findAll().get(0).getTokenHash());
        http.perform(get("/api/auth/me"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("alice@teste.com"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
        http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ALICE@TESTE.COM\",\"senha\":\"Senha123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        http.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
        http.perform(get("/api/scripts")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejeitaSenhaIncorretaEmailRepetidoESenhaCurta() throws Exception {
        http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@teste.com\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
        http.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Outra\",\"email\":\"ALICE@TESTE.COM\",\"senha\":\"Senha123!\"}"))
                .andExpect(status().isConflict());
        http.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Teste\",\"email\":\"teste@teste.com\",\"senha\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sessaoExpiradaENaoAutenticadoNaoAcessamDados() throws Exception {
        MockMvc semLogin = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain).build();
        semLogin.perform(get("/api/scripts")).andExpect(status().isUnauthorized());
        semLogin.perform(post("/api/ambientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        var sessao = sessaoRepository.findAll().get(0);
        sessao.setExpiraEm(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1));
        sessaoRepository.saveAndFlush(sessao);
        http.perform(get("/api/ambientes")).andExpect(status().isUnauthorized());
        http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@teste.com\",\"senha\":\"Senha123!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void outroUsuarioNaoListaNemLeAlteraOuExcluiRecursos() throws Exception {
        long execucaoId = criarExecucao();
        String bob = "Bearer " + cadastrarBob();
        for (String rota : java.util.List.of("/api/scripts", "/api/ambientes", "/api/execucoes")) {
            http.perform(get(rota).header("Authorization", bob))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        for (String rota : java.util.List.of("/api/scripts/" + scriptId,
                "/api/ambientes/" + ambienteId, "/api/execucoes/" + execucaoId,
                "/api/execucoes/" + execucaoId + "/saida")) {
            http.perform(get(rota).header("Authorization", bob)).andExpect(status().isNotFound());
        }
        http.perform(put("/api/scripts/" + scriptId).header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"invadido\",\"conteudo\":\"echo 1\"}"))
                .andExpect(status().isNotFound());
        http.perform(put("/api/ambientes/" + ambienteId).header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"invadido\",\"cpuMillicores\":500,\"memoriaMb\":128,\"ioBytesSegundo\":1000}"))
                .andExpect(status().isNotFound());
        for (String rota : java.util.List.of("/api/scripts/" + scriptId,
                "/api/ambientes/" + ambienteId, "/api/execucoes/" + execucaoId)) {
            http.perform(delete(rota).header("Authorization", bob)).andExpect(status().isNotFound());
        }
        http.perform(post("/api/execucoes/" + execucaoId + "/parar").header("Authorization", bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoPermiteExecutarScriptDeOutroUsuarioNemForjarDono() throws Exception {
        String bob = "Bearer " + cadastrarBob();
        http.perform(post("/api/ambientes").header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"bob\",\"cpuMillicores\":500,\"memoriaMb\":128,\"ioBytesSegundo\":1000,\"usuario_id\":" + usuarioAlice + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario_id").value(org.hamcrest.Matchers.not((int) usuarioAlice)));
        long ambienteBob = ambienteRepository.findAll().stream()
                .filter(a -> a.getNome().equals("bob")).findFirst().orElseThrow().getId();
        String corpo = "{\"scriptId\":" + scriptId + "}";
        http.perform(post("/api/ambientes/" + ambienteBob + "/execucoes").header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
        http.perform(post("/api/ambientes/" + ambienteId + "/execucoes").header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
        assertEquals(0, execucaoRepository.count());
    }

    @Test
    void runnerContinuaIsoladoDoLoginDosUsuarios() throws Exception {
        long execucaoId = criarExecucao();
        http.perform(get("/api/runner/fila").header("X-Runner-Token", "errado"))
                .andExpect(status().isUnauthorized());
        http.perform(post("/api/runner/execucoes/" + execucaoId + "/assumir")
                        .header("X-Runner-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.execucao.usuario_id").value(usuarioAlice));
        http.perform(get("/api/runner/execucoes").header("X-Runner-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(execucaoId));
        http.perform(get("/api/runner/execucoes/" + execucaoId).header("X-Runner-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EM_EXECUCAO"));
        http.perform(get("/api/runner/execucoes/" + execucaoId).header("X-Runner-Token", "errado"))
                .andExpect(status().isUnauthorized());
        MockMvc somenteRunner = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain).build();
        somenteRunner.perform(get("/api/scripts").header("X-Runner-Token", TOKEN))
                .andExpect(status().isUnauthorized());
    }
}