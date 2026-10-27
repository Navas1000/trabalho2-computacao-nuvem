#!/usr/bin/env bash
# status.sh - Consulta o estado de uma execucao.
#
# Uso: status.sh <execucao_id>
# Saida (stdout): JSON
#   {"execucao_id":N,"estado":"EM_EXECUCAO","pid":N,"processos":N}
#   {"execucao_id":N,"estado":"FINALIZADO","codigo_saida":N,"parada_solicitada":true|false}
#   {"execucao_id":N,"estado":"PERDIDO"}   (supervisor morreu sem registrar o resultado)
# Codigo de saida 3 se a execucao nao existir neste servidor.
source "$(dirname "$0")/lib.sh"

[[ $# -eq 1 ]] || die 2 "Uso: status.sh <execucao_id>"
validar_uint execucao_id "$1"
exec_id=$((10#$1))
dir=$(dir_execucao "$exec_id")
[[ -d "$dir" ]] || die 3 "Execucao $exec_id inexistente"

if [[ -s "$dir/exitcode" ]]; then
  parada=false; [[ -e "$dir/stop" ]] && parada=true
  printf '{"execucao_id":%s,"estado":"FINALIZADO","codigo_saida":%s,"parada_solicitada":%s}\n' \
    "$exec_id" "$(cat "$dir/exitcode")" "$parada"
  exit 0
fi

pid=$(cat "$dir/supervisor.pid" 2>/dev/null || true)
# Confere o PID E o nome do processo, para nao confundir com PID reaproveitado.
if [[ "$pid" =~ ^[0-9]+$ ]] && grep -aq supervisor.sh "/proc/$pid/cmdline" 2>/dev/null; then
  amb=$(cat "$dir/ambiente_id" 2>/dev/null || true)
  procs=0
  if [[ "$amb" =~ ^[0-9]+$ && -r "$(cgroup_ambiente "$amb")/cgroup.procs" ]]; then
    procs=$(wc -l < "$(cgroup_ambiente "$amb")/cgroup.procs")
  fi
  printf '{"execucao_id":%s,"estado":"EM_EXECUCAO","pid":%s,"processos":%s}\n' "$exec_id" "$pid" "$procs"
else
  printf '{"execucao_id":%s,"estado":"PERDIDO"}\n' "$exec_id"
fi
