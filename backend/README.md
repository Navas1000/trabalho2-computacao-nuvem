# Backend — refinamentos de seguranca e demonstracao

## Alteracoes desta entrega (26/10/2026)
- SecurityConfig: SAMEORIGIN, nosniff e CSP nas cadeias de usuarios e runner, conforme https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html.
- LoginRateLimiter: ate cinco tentativas de login por minuto, tanto por IP quanto por email normalizado, com reserva atomica antes do BCrypt e resposta 429/Retry-After. Conta tentativas validas e invalidas para tambem limitar o custo de autenticacoes bem-sucedidas. Janela fixa em memoria, limitada a 10000 chaves; expira automaticamente. Reinicio limpa contadores. Para multiplas instancias, usar armazenamento compartilhado; a VM atual tem uma instancia.
- ClientAddress: usa o endereco da conexao. Somente para proxy em loopback aceita X-Real-IP, sobrescrito pelo Nginx da branch infra. Ignora X-Forwarded-For do cliente. Manter backend ligado em 127.0.0.1; SERVER_ADDRESS permite configuracao explicita.
- PayloadLimitFilter: limita corpo antes de desserializar, incluindo chunked. Login: 4 KiB; cadastro: 8 KiB; demais corpos nao multipart: 2 MiB. O teto maior acomoda escapes JSON; o conteudo final de cada script continua limitado a 256 KiB UTF-8.
- ScriptService: aplica o limite de bytes ao criar/editar e no upload. Vazio, somente espacos ou byte nulo retorna 400; excesso retorna 413. Multipart conserva limites de 256 KiB por arquivo e 300 KiB por requisicao. Nao remove comandos dos scripts: isolamento e limites de execucao sao responsabilidade do daemon/cgroups.
- ApiErrors/properties: respostas de erro nao incluem payload recebido, causas JDBC, stack traces ou credenciais. A aplicacao nao registra DB_PASSWORD, RUNNER_TOKEN ou corpo de login. BackendApplication desativa o usuario/senha automatica do Spring (a autenticacao real e a do banco).
- mvnw.cmd: corrigida leitura de Target nulo em diretorio comum no Windows. Build continua autocontido no repositorio, com dependencias publicas baixadas pelo Maven.

Nenhuma dependencia nova no pom.xml. Endpoints, autenticacao por usuario e X-Runner-Token foram preservados. O segredo atual do runner e um token compartilhado comparado em tempo constante, nao uma assinatura HMAC por requisicao.

## Massa de demonstracao
O arquivo unico de estrutura continua em banco-de-dados/banco.sql, idempotente para criacao das tabelas atuais. Dados opcionais estao em banco-de-dados/dados_teste.sql, como solicitado pelo grupo.

Importar em um MySQL novo, dentro da VM Linux:
```sh
mysql < banco-de-dados/banco.sql
mysql compnuvem < banco-de-dados/dados_teste.sql
```

Conta EXCLUSIVA de demonstracao academica: demo@compnuvem.local / DemoNuvem2026!
Senha no banco e BCrypt. A importacao nao redefine senhas nem duplica scripts/ambiente em reexecucoes sequenciais. Nao importar essa conta publica em producao.

A conta possui os quatro scripts teste_cpu.sh, teste_memoria.sh, teste_loop_infinito.sh e teste_isolamento.sh, mais um ambiente de 250 millicores, 128 MiB e 1 MiB/s. Nenhuma execucao e iniciada pelo SQL. Entrar pelo frontend, selecionar um script e executar no ambiente. Parar os testes de CPU/loop pelo botao correspondente. O teste de memoria usa somente Bash, sem dependencia adicional de Python, e deve terminar por limite de memoria. O teste de isolamento imprime informacoes para inspecao; a efetividade precisa ser verificada no executor Linux.

Conteudos SQL usam bytes UTF-8 em hexadecimal para preservar exatamente novas linhas e caracteres de shell em diferentes sql_mode. Eles aparecem como texto normal apos importacao e consulta pela API.

## Integracao com a infraestrutura
O trabalho de origin/infra foi integrado localmente, preservando alteracoes locais. O provision.sh agora importa banco-de-dados/dados_teste.sql depois do schema, por padrao na VM academica. LOAD_DEMO_DATA=0 desativa. Inclui teste_seguranca.sh e falha explicitamente quando algum autoteste falha, em vez de apenas emitir aviso.

A main remota ainda precisa receber os commits integrados para que o Vagrantfile padrao reconstrua esta mesma versao via GitHub. Nenhum push foi feito pelo assistente.
## Nome do banco e compatibilidade
A API ja aceita DB_URL completo. O provision consultado usa jdbc:mysql://localhost:3306/compnuvem; isso foi mantido. banco.sql cria compnuvem explicitamente e dados_teste.sql usa o banco selecionado no cliente MySQL. DB_URL nao renomeia tabelas/bancos. Se outro nome for oficialmente exigido, alinhar CREATE DATABASE/USE no SQL, DB_NAME no provision e DB_URL juntos. As imagens do enunciado fornecidas neste chat nao especificam nome de banco ou rotas /executar e /output; confirmar exigencias adicionais no documento usado pelo grupo antes de quebrar contratos existentes.

## Build e limites da verificacao
Com JDK 17, na pasta backend:
```sh
./mvnw clean package
./mvnw -B -DskipTests clean package
```
No Windows, usar mvnw.cmd. Testes incluem H2/JPA, Spring Security/MockMvc, isolamento entre usuarios, cota de login e concorrencia, headers, limites UTF-8 e corpo chunked. O teste dos dados verifica hash/senha publica de demonstracao e os quatro scripts. Nao substitui importar SQL no MySQL real nem executar Vagrant/Linux. -DskipTests compila testes, mas nao os executa.

Resultado verificado em 26/10/2026: Maven Wrapper clean package passou com 22 testes, 0 falhas/erros; -B -DskipTests clean package tambem passou. A integracao e os testes reais na VM estao descritos em RELATORIO_TESTES.md, na raiz do projeto.
