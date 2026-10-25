#!/usr/bin/env bash
# Funcoes e constantes comuns do executor Linux (namespaces + cgroups v2).
# Este arquivo e carregado ("source") pelos demais scripts; nao e executado diretamente.
#
# Codigos de saida padronizados dos scripts:
#   0 sucesso | 1 erro generico | 2 argumento invalido | 3 recurso inexistente
#   4 conflito (ja em execucao) | 5 erro de sistema (cgroup v2, permissao, etc.)

set -euo pipefail
umask 022

# Todos os caminhos podem ser sobrescritos por variaveis de ambiente (uteis em testes).
CGROUP_FS="${CGROUP_FS:-/sys/fs/cgroup}"
CGROUP_ROOT="${CGROUP_ROOT:-$CGROUP_FS/compnuvem}"
STATE_DIR="${STATE_DIR:-/var/lib/compnuvem/exec}"
JOB_USER="${JOB_USER:-compnuvem-job}"
EXECUTOR_DIR="${EXECUTOR_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"

# Limites fixos de seguranca aplicados a todo ambiente.
PIDS_MAX="${PIDS_MAX:-512}"           # protege contra fork bomb
TMP_TAMANHO_MB="${TMP_TAMANHO_MB:-64}" # tamanho do /tmp privado do ambiente
LOG_MAX_KB="${LOG_MAX_KB:-8192}"      # limite de cada arquivo de log (8 MiB)

log() { printf '[%s] %s\n' "$(basename "$0")" "$*" >&2; }

# Imprime um erro em JSON no stderr e encerra com o codigo informado.
die() {
  local codigo="$1"; shift
  local msg="$*"
  msg=${msg//\\/\\\\}; msg=${msg//\"/\\\"}
  printf '{"erro":"%s"}\n' "$msg" >&2
  exit "$codigo"
}

exigir_root() {
  [[ $EUID -eq 0 ]] || die 5 "Este script precisa ser executado como root (o runner roda como root)"
}

# Valida inteiro positivo (sem sinal, sem zeros a esquerda desnecessarios, ate 18 digitos).
validar_uint() {
  local nome="$1" valor="${2-}"
  [[ "$valor" =~ ^[0-9]{1,18}$ ]] || die 2 "Argumento '$nome' invalido: esperado inteiro positivo"
  (( 10#$valor > 0 )) || die 2 "Argumento '$nome' deve ser maior que zero"
}

exigir_cgroup_v2() {
  [[ "$(stat -fc %T "$CGROUP_FS" 2>/dev/null)" == "cgroup2fs" ]] \
    || die 5 "cgroup v2 nao esta montado em $CGROUP_FS"
  [[ -w "$CGROUP_FS/cgroup.subtree_control" ]] || die 5 "Sem permissao de escrita em $CGROUP_FS"
}

dir_execucao() { printf '%s/%s' "$STATE_DIR" "$1"; }
cgroup_ambiente() { printf '%s/amb-%s' "$CGROUP_ROOT" "$1"; }

# Habilita os controladores necessarios na raiz do cgroup do projeto (idempotente).
preparar_raiz_cgroup() {
  mkdir -p "$CGROUP_ROOT"
  local c
  for c in cpu memory io pids; do
    if grep -qw "$c" "$CGROUP_FS/cgroup.controllers"; then
      echo "+$c" > "$CGROUP_FS/cgroup.subtree_control" 2>/dev/null || true
      echo "+$c" > "$CGROUP_ROOT/cgroup.subtree_control" 2>/dev/null || true
    fi
  done
}

# Descobre "MAJOR:MINOR" do disco fisico que guarda $STATE_DIR (io.max exige o disco inteiro).
dispositivo_io() {
  mkdir -p "$STATE_DIR"
  local origem pai
  origem=$(findmnt -no SOURCE -T "$STATE_DIR" 2>/dev/null | head -n1)
  [[ -b "$origem" ]] || return 1
  while pai=$(lsblk -no PKNAME "$origem" 2>/dev/null | head -n1) && [[ -n "$pai" ]]; do
    origem="/dev/$pai"
  done
  lsblk -dno MAJ:MIN "$origem" 2>/dev/null | tr -d ' '
}

# Mata todos os processos de um cgroup (e dos filhos) e espera ele esvaziar.
matar_cgroup() {
  local cg="$1" i p
  [[ -d "$cg" ]] || return 0
  if [[ -w "$cg/cgroup.kill" ]]; then
    echo 1 > "$cg/cgroup.kill" 2>/dev/null || true
  else
    for p in $(cat "$cg/cgroup.procs" 2>/dev/null); do kill -KILL "$p" 2>/dev/null || true; done
  fi
  for i in $(seq 1 50); do
    [[ -s "$cg/cgroup.procs" ]] || return 0
    sleep 0.1
  done
  return 1
}
