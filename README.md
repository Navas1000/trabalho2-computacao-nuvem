# Projeto 2 — Computação em Nuvem (PUC-Campinas)

Aplicação web para criar e gerenciar **ambientes isolados no Linux** usando **namespaces** e **cgroups v2**.

| Camada | Tecnologia | Pasta |
|---|---|---|
| Frontend | Angular 21 | `frontend/projeto-angular` |
| Backend | Spring Boot (Java 17) + JPA | `backend` |
| Banco | MySQL 8 | `banco-de-dados/banco.sql` |
| Executor Linux + infraestrutura | Bash, namespaces, cgroups v2, systemd, Nginx | `infrastructure/` |
| VM | Vagrant + VirtualBox + Ubuntu 22.04 | `Vagrantfile` |

## Como subir tudo (um comando)

Pré-requisitos no computador: [VirtualBox](https://www.virtualbox.org/) e [Vagrant](https://www.vagrantup.com/).

```bash
vagrant up
```

A VM é criada, o projeto é clonado do GitHub, tudo é instalado e compilado e a aplicação fica em
**http://localhost:8080**. A primeira execução leva de 10 a 20 minutos (downloads e builds).

Para reconstruir do zero (teste obrigatório):

```bash
vagrant destroy -f
vagrant up
```

Opções (variáveis de ambiente do host, todas opcionais):

| Variável | Padrão | Uso |
|---|---|---|
| `REPO_BRANCH` | `main` | Branch clonada pela VM (ex.: `infra` para testar antes do merge) |
| `REPO_URL` | repositório do projeto | Repositório clonado |
| `HOST_PORT` | `8080` | Porta do host (se ocupada, o Vagrant escolhe outra e avisa) |
| `PRIVATE_IP` | — | IP de rede privada extra (ex.: `192.168.56.10`) |

PowerShell: `$env:REPO_BRANCH="infra"; vagrant up` — Bash: `REPO_BRANCH=infra vagrant up`.

> A VM clona o que está **publicado no GitHub**. Alterações locais não enviadas (`git push`) não entram na VM.

## Verificando

```bash
vagrant ssh
sudo bash /opt/compnuvem/src/infrastructure/tests/teste_executor.sh   # namespaces + cgroups (sem backend)
bash /opt/compnuvem/src/infrastructure/tests/teste_api.sh http://127.0.0.1   # ponta a ponta pela API
```

Detalhes da arquitetura, do contrato do executor e solução de problemas: [`infrastructure/README.md`](infrastructure/README.md).
