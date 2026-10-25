#!/usr/bin/env bash
# supervisor.sh - INTERNO (iniciado por executar.sh). Roda fora do cgroup do ambiente.
#
# Uso: supervisor.sh <execucao_id> <ambiente_id>
# Responsabilidades:
#   1. mover o processo para o cgroup do ambiente e criar os namespaces (unshare);
#   2. capturar stdout e stderr em arquivos (e em saida.log, intercalados);
#   3. aguardar o termino, encerrar sobras, detectar OOM e gravar o 'exitcode'.
# Como roda FORA do cgroup, sobrevive ao cgroup.kill e consegue registrar o codigo de saida.
source "$(dirname "$0")/lib.sh"

exec_id="$1"; amb="$2"
validar_uint execucao_id "$exec_id"
validar_uint ambiente_id "$amb"
dir=$(dir_execucao "$exec_id")
cg=$(cgroup_ambiente "$amb")
echo $$ > "$dir/supervisor.pid"

mkfifo "$dir/out.fifo" "$dir/err.fifo"
: > "$dir/stdout.log"; : > "$dir/stderr.log"; : > "$dir/saida.log"

# Dois 'tee': gravam o fluxo no arquivo proprio e no saida.log (O_APPEND preserva a ordem).
# O ulimit -f limita o tamanho dos logs; ao estourar, o job recebe SIGPIPE.
( ulimit -f "$LOG_MAX_KB"; exec tee -a "$dir/stdout.log" < "$dir/out.fifo" >> "$dir/saida.log" ) &
tee_out=$!
( ulimit -f "$LOG_MAX_KB"; exec tee -a "$dir/stderr.log" < "$dir/err.fifo" >> "$dir/saida.log" ) &
tee_err=$!

# Subshell do job: entra no cgroup, depois substitui-se pelo unshare (herdado pelos filhos).
(
  if [[ -e "$dir/stop" ]]; then exit 143; fi          # parada pedida antes de iniciar
  echo "$BASHPID" > "$cg/cgroup.procs"
  exec unshare --pid --fork --kill-child --mount-proc \
       --mount --uts --ipc --net --cgroup \
       "$EXECUTOR_DIR/isolado.sh" "$exec_id" "$amb"
) > "$dir/out.fifo" 2> "$dir/err.fifo" &
job=$!

rc=0
wait "$job" || rc=$?

# Encerra eventuais processos remanescentes do ambiente (filhos que ficaram para tras).
matar_cgroup "$cg" || true
wait "$tee_out" "$tee_err" 2>/dev/null || true
rm -f "$dir/out.fifo" "$dir/err.fifo"

# OOM: o kernel mata sem avisar o job; registramos para o usuario entender o codigo 137.
if grep -Eq '^oom_kill [1-9]' "$cg/memory.events" 2>/dev/null; then
  msg="[executor] processo encerrado pelo kernel: limite de memoria do ambiente excedido"
  echo "$msg" | tee -a "$dir/stderr.log" >> "$dir/saida.log"
fi

echo "$rc" > "$dir/exitcode.tmp"
mv "$dir/exitcode.tmp" "$dir/exitcode"   # gravacao atomica: o runner so enxerga o arquivo completo
