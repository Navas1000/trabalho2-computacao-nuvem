#!/usr/bin/env bash
# remover.sh - Remove o cgroup de um ambiente e libera os recursos.
#
# Uso: remover.sh <ambiente_id>
# Saida (stdout): JSON {"ambiente_id":N,"removido":true}
# Encerra qualquer processo remanescente, aguarda o cgroup esvaziar e o remove.
# Os namespaces desaparecem sozinhos quando o ultimo processo deles termina.
# Os logs das execucoes (<STATE_DIR>/<id>/) sao mantidos para consulta posterior.
source "$(dirname "$0")/lib.sh"

exigir_root
[[ $# -eq 1 ]] || die 2 "Uso: remover.sh <ambiente_id>"
validar_uint ambiente_id "$1"
amb=$((10#$1))
cg=$(cgroup_ambiente "$amb")

if [[ -d "$cg" ]]; then
  matar_cgroup "$cg" || die 5 "Processos do ambiente $amb nao encerraram"
  # O rmdir pode falhar por instantes enquanto o kernel libera os ultimos processos.
  for _ in $(seq 1 20); do
    rmdir "$cg" 2>/dev/null && break
    sleep 0.1
  done
  [[ ! -d "$cg" ]] || die 5 "Nao foi possivel remover o cgroup $cg"
fi

printf '{"ambiente_id":%s,"removido":true}\n' "$amb"
