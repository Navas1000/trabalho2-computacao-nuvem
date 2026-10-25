# Infraestrutura e executor Linux (Integrante 3)

## Arquitetura

```
Navegador ──80──► Nginx ──/──► Angular (arquivos estáticos em /var/www/compnuvem)
 (host :8080)       │
                    └─/api/─► Spring Boot (127.0.0.1:8080, usuário sem privilégios) ──► MySQL
                                   ▲
                                   │ HTTP + X-Runner-Token  (/api/runner/**, bloqueado no Nginx)
                              runner.sh (systemd, root) ──► criar_ambiente / executar / status / parar / remover
                                                              └─► cgroup v2 + namespaces + setpriv
```

**Decisão de segurança:** o backend **não** executa comandos nem tem privilégios. Ele só grava a execução como
`PENDENTE`. O `runner` (único processo root) busca as execuções pela API interna, autenticado por token, e só
consegue chamar **5 scripts fixos**, que validam os argumentos (IDs numéricos) e nunca interpretam texto livre como comando.
O conteúdo do script do usuário é gravado em arquivo e roda **sem privilégios** dentro do ambiente isolado.

## Ciclo de uma execução

1. Front → `POST /api/ambientes/{id}/execucoes` → backend grava `PENDENTE`.
2. `runner` vê a execução na fila → `assumir` (vira `EM_EXECUCAO`) → recebe script e limites do ambiente.
3. `criar_ambiente.sh` cria `/sys/fs/cgroup/compnuvem/amb-<id>` com os limites.
4. `executar.sh` grava o script e inicia o `supervisor.sh` (desacoplado). Ele entra no cgroup e executa
   `unshare --pid --fork --mount-proc --mount --uts --ipc --net --cgroup` → `isolado.sh` → script do usuário.
5. stdout e stderr vão para arquivos; o runner envia a saída ao backend em blocos enquanto o programa roda.
6. Ao terminar (ou ao `parar`), o runner remove o cgroup e chama `finalizar` com `TERMINADO` (0), `ERRO` (≠0) ou `CANCELADO`.

## O que cada recurso do Linux faz aqui

| Requisito | Implementação |
|---|---|
| Namespaces | PID (script é o PID 1), mount (`/tmp` privado em tmpfs; segredos ocultos), UTS (hostname `amb-<id>`), IPC, **net** (só loopback, sem acesso à rede/MySQL), cgroup |
| CPU | `cpu.max` = `millicores × 100` de `100000` (500m → `50000 100000`) |
| Memória | `memory.max` (MB × 1 MiB), `memory.swap.max=0`, `memory.oom.group=1` (OOM mata o grupo) |
| I/O | `io.max` `rbps`/`wbps` no disco físico que guarda `/var/lib/compnuvem` (detectado via `lsblk`) |
| Processos | `pids.max=512` (contra fork bomb) |
| Encerrar filhos | `cgroup.kill` (SIGKILL atômico no grupo inteiro) |
| Privilégios | `setpriv`: usuário `compnuvem-job`, sem grupos, sem capabilities, `no-new-privs` |

Observações: a saída enviada ao backend é limitada a 4 MiB (regra do backend) e os logs em arquivo a 8 MiB cada.
Um ambiente executa **um programa por vez** (regra do backend: `409` se já houver execução ativa).

## Contrato dos scripts (`/opt/compnuvem/executor`)

Todos exigem root, imprimem **JSON no stdout** e erros como `{"erro":"..."}` no stderr.
Códigos de saída: `0` ok · `2` argumento inválido · `3` não existe · `4` conflito · `5` erro de sistema.

| Script | Argumentos | Saída |
|---|---|---|
| `criar_ambiente.sh` | `<ambiente_id> <cpu_millicores> <memoria_mb> <io_bytes_segundo>` | `{"ambiente_id","cgroup","cpu_max","memory_max","io_max"}` |
| `executar.sh` | `<execucao_id> <ambiente_id>` + script na **stdin** | `{"execucao_id","ambiente_id","pid","diretorio"}` |
| `status.sh` | `<execucao_id>` | `{"estado":"EM_EXECUCAO"\|"FINALIZADO"\|"PERDIDO", ...}` |
| `parar.sh` | `<execucao_id>` | `{"execucao_id","parada":true}` |
| `remover.sh` | `<ambiente_id>` | `{"ambiente_id","removido":true}` |

Internos (não fazem parte do contrato): `lib.sh`, `supervisor.sh`, `isolado.sh`, `runner.sh`.

Arquivos de cada execução: `/var/lib/compnuvem/exec/<execucao_id>/` → `stdout.log`, `stderr.log`, `saida.log`, `script.sh`, `exitcode`.

## Contrato com o backend (já implementado pelo Integrante 1)

O runner usa exatamente a API existente: `GET /api/runner/fila`, `GET /api/runner/execucoes`,
`POST /api/runner/execucoes/{id}/assumir|saida|finalizar`, header `X-Runner-Token`. Variáveis lidas pelo
backend: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `RUNNER_TOKEN` (≥ 32 caracteres), `SERVER_ADDRESS`, `FRONTEND_ORIGIN`.
Elas são geradas no provisionamento em `/etc/compnuvem/backend.env` (senha e token aleatórios por VM).

> Divergência com o enunciado: o banco usa o nome `compnuvem` (definido em `banco-de-dados/banco.sql`) e a API usa
> `POST /api/ambientes/{id}/execucoes` e `GET /api/execucoes/{id}/saida`. O provisionamento segue o que o código já faz.

## Estrutura

```
Vagrantfile                         VM + bootstrap (git clone) → provision.sh
infrastructure/
  provision/provision.sh            instala tudo, builda, configura serviços (idempotente)
  scripts/                          executor Linux + runner (copiados para /opt/compnuvem/executor, dono root)
  systemd/                          compnuvem-backend.service, compnuvem-runner.service
  nginx/compnuvem.conf              Angular + proxy /api
  tests/teste_executor.sh           teste de namespaces/cgroups (mede CPU, memória e I/O)
  tests/teste_api.sh                teste ponta a ponta pela API
```

## Operação e solução de problemas

```bash
systemctl status compnuvem-backend compnuvem-runner nginx mysql
journalctl -u compnuvem-backend -n 100 --no-pager
journalctl -u compnuvem-runner -f
ls /sys/fs/cgroup/compnuvem/            # cgroups ativos (um por ambiente em execução)
sudo /opt/compnuvem/src/infrastructure/provision/provision.sh   # reprovisiona sem perder banco/segredos
```

| Sintoma | Causa provável |
|---|---|
| Execução fica `PENDENTE` | runner parado ou sem conexão: `journalctl -u compnuvem-runner` |
| `io_max: null` na criação | kernel/disco sem suporte; o teste marca como "pulou" |
| Falha de DNS no `apt`/`npm` | rede de laboratório; já usamos o DNS proxy do VirtualBox, tente novamente |
| Porta 8080 ocupada | o Vagrant escolhe outra e informa; ou use `HOST_PORT=8081` |
