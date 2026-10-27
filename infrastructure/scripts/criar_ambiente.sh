#!/usr/bin/env bash
# criar_ambiente.sh - Prepara o cgroup v2 de um ambiente e aplica CPU, memoria e I/O.
#
# Uso: criar_ambiente.sh <ambiente_id> <cpu_millicores> <memoria_mb> <io_bytes_segundo>
# Saida (stdout): JSON  {"ambiente_id":N,"cgroup":"...","cpu_max":"...","memory_max":N,"io_max":"..."|null}
# Idempotente: se o cgroup ja existe, apenas reaplica os limites.
#
# Os namespaces (PID, mount, UTS, IPC, rede, cgroup) sao criados no momento da
# execucao (executar.sh), pois vivem enquanto houver processo dentro deles.
source "$(dirname "$0")/lib.sh"

exigir_root
[[ $# -eq 4 ]] || die 2 "Uso: criar_ambiente.sh <ambiente_id> <cpu_millicores> <memoria_mb> <io_bytes_segundo>"
amb="$1"; cpu_mc="$2"; mem_mb="$3"; io_bps="$4"
validar_uint ambiente_id "$amb"
validar_uint cpu_millicores "$cpu_mc"
validar_uint memoria_mb "$mem_mb"
validar_uint io_bytes_segundo "$io_bps"
(( 10#$cpu_mc <= 64000 )) || die 2 "cpu_millicores acima do maximo (64000)"
(( 10#$mem_mb >= 16 && 10#$mem_mb <= 1048576 )) || die 2 "memoria_mb deve estar entre 16 e 1048576"

exigir_cgroup_v2
preparar_raiz_cgroup
cg=$(cgroup_ambiente "$amb")
mkdir -p "$cg"

# CPU: 1000 millicores = 1 CPU = 100000us de quota a cada periodo de 100000us.
# O kernel exige quota minima de 1000us, por isso o piso de 10 millicores.
quota=$(( 10#$cpu_mc * 100 ))
(( quota >= 1000 )) || quota=1000
echo "$quota 100000" > "$cg/cpu.max" || die 5 "Falha ao aplicar cpu.max"

# Memoria: limite rigido; sem swap; se estourar, o OOM killer encerra o grupo inteiro.
mem_bytes=$(( 10#$mem_mb * 1024 * 1024 ))
echo "$mem_bytes" > "$cg/memory.max" || die 5 "Falha ao aplicar memory.max"
echo 0 > "$cg/memory.swap.max" 2>/dev/null || true
echo 1 > "$cg/memory.oom.group" 2>/dev/null || true

# Processos: protecao basica contra fork bomb.
echo "$PIDS_MAX" > "$cg/pids.max" 2>/dev/null || true

# I/O: limite de leitura e escrita (bytes/s) no disco que guarda os dados do executor.
io_max_json=null
if dev=$(dispositivo_io) && [[ -n "$dev" ]]; then
  if echo "$dev rbps=$io_bps wbps=$io_bps" > "$cg/io.max" 2>/dev/null; then
    io_max_json="\"$dev rbps=$io_bps wbps=$io_bps\""
  else
    log "AVISO: kernel recusou io.max para o dispositivo $dev"
  fi
else
  log "AVISO: dispositivo de bloco nao identificado; limite de I/O nao aplicado"
fi

printf '{"ambiente_id":%s,"cgroup":"%s","cpu_max":"%s 100000","memory_max":%s,"io_max":%s}\n' \
  "$((10#$amb))" "$cg" "$quota" "$mem_bytes" "$io_max_json"
