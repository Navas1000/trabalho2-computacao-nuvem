#!/usr/bin/env bash
# parar.sh - Encerra uma execucao e todos os seus processos filhos.
#
# Uso: parar.sh <execucao_id>
# Saida (stdout): JSON {"execucao_id":N,"parada":true}
# Idempotente: se a execucao ja terminou, retorna sucesso sem fazer nada.
# O cgroup.kill do kernel (SIGKILL atomico em todo o grupo) garante que nenhum filho escape.
source "$(dirname "$0")/lib.sh"

exigir_root
[[ $# -eq 1 ]] || die 2 "Uso: parar.sh <execucao_id>"
validar_uint execucao_id "$1"
exec_id=$((10#$1))
dir=$(dir_execucao "$exec_id")
[[ -d "$dir" ]] || die 3 "Execucao $exec_id inexistente"

# Marca o pedido ANTES de matar: o runner usa esse marcador para classificar como CANCELADO,
# e o supervisor evita iniciar um job cuja parada ja foi solicitada.
touch "$dir/stop"

if [[ ! -s "$dir/exitcode" ]]; then
  amb=$(cat "$dir/ambiente_id" 2>/dev/null || true)
  if [[ "$amb" =~ ^[0-9]+$ ]]; then
    matar_cgroup "$(cgroup_ambiente "$amb")" || die 5 "Processos do ambiente $amb nao encerraram"
  fi
fi

printf '{"execucao_id":%s,"parada":true}\n' "$exec_id"
