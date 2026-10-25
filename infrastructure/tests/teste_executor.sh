#!/usr/bin/env bash
# teste_executor.sh - Teste de fumaca do executor Linux. NAO precisa de backend nem de banco.
#
# Uso (na VM, como root):  sudo bash infrastructure/tests/teste_executor.sh
# Valida de verdade: isolamento por namespaces, limites de cgroup v2 (CPU, memoria, I/O),
# captura de stdout/stderr, parada com filhos, status e remocao do cgroup.
# Usa IDs 9001-9006 (ambiente e execucao) e limpa tudo ao final.
set -uo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -n "${EXECUTOR_DIR:-}" ]]; then
  EXEC_BIN="$EXECUTOR_DIR"
elif [[ -d /opt/compnuvem/executor ]]; then
  EXEC_BIN=/opt/compnuvem/executor
else
  EXEC_BIN="$SELF_DIR/../scripts"
fi
STATE_DIR="${STATE_DIR:-/var/lib/compnuvem/exec}"
CGROUP_ROOT="${CGROUP_ROOT:-/sys/fs/cgroup/compnuvem}"
IO_ARQ=/var/tmp/compnuvem-io-teste.bin

[[ $EUID -eq 0 ]] || { echo "Execute como root (sudo)."; exit 2; }
[[ -x "$EXEC_BIN/criar_ambiente.sh" || -f "$EXEC_BIN/criar_ambiente.sh" ]] || { echo "Scripts nao encontrados em $EXEC_BIN"; exit 2; }

PASS=0; FAIL=0; SKIP=0
ok()   { PASS=$((PASS+1)); printf '  [ OK ] %s\n' "$*"; }
nok()  { FAIL=$((FAIL+1)); printf '  [FALHA] %s\n' "$*"; }
skip() { SKIP=$((SKIP+1)); printf '  [PULOU] %s\n' "$*"; }
secao() { printf '\n--- %s\n' "$*"; }
verifica() { local desc="$1"; shift; if "$@"; then ok "$desc"; else nok "$desc"; fi; }
has()   { grep -q -- "$1" <<<"$2"; }
hasnt() { ! grep -q -- "$1" <<<"$2"; }

dir() { echo "$STATE_DIR/$1"; }
cg()  { echo "$CGROUP_ROOT/amb-$1"; }
saida() { cat "$(dir "$1")/saida.log" 2>/dev/null; }

# aguardar_fim <id> <segundos>
aguardar_fim() {
  local i
  for i in $(seq 1 $(( $2 * 10 ))); do
    [[ -s "$(dir "$1")/exitcode" ]] && return 0
    sleep 0.1
  done
  return 1
}

limpar() {
  local n
  for n in 9001 9002 9003 9004 9005 9006; do
    "$EXEC_BIN/parar.sh" "$n" >/dev/null 2>&1
    "$EXEC_BIN/remover.sh" "$n" >/dev/null 2>&1
    rm -rf "$(dir "$n")"
  done
  rm -f "$IO_ARQ"
}
trap limpar EXIT
limpar

echo "Executor: $EXEC_BIN"
echo "Kernel:   $(uname -r)"

# ------------------------------------------------------------------ 1
secao "1. Validacao de argumentos (nao aceita entrada arbitraria)"
verifica "rejeita ambiente_id nao numerico"      bash -c "! '$EXEC_BIN/criar_ambiente.sh' 'x;rm -rf /' 1000 64 1000 2>/dev/null"
verifica "rejeita memoria abaixo do minimo"      bash -c "! '$EXEC_BIN/criar_ambiente.sh' 9001 1000 4 1000 2>/dev/null"
verifica "rejeita execucao em ambiente inexistente" bash -c "! echo 'echo oi' | '$EXEC_BIN/executar.sh' 9001 9001 2>/dev/null"
verifica "status de execucao inexistente retorna 3" bash -c "'$EXEC_BIN/status.sh' 9001 >/dev/null 2>&1; [[ \$? -eq 3 ]]"

# ------------------------------------------------------------------ 2
secao "2. Criacao do ambiente e limites no cgroup v2 (CPU 500m, 64 MB, I/O 2 MiB/s)"
"$EXEC_BIN/criar_ambiente.sh" 9001 500 64 2097152 >/tmp/criar.json 2>/tmp/criar.err
verifica "criar_ambiente.sh retornou sucesso"    test $? -eq 0
cat /tmp/criar.json
verifica "cpu.max = 50000 100000"                test "$(cat "$(cg 9001)/cpu.max" 2>/dev/null)" = "50000 100000"
verifica "memory.max = 64 MiB"                   test "$(cat "$(cg 9001)/memory.max" 2>/dev/null)" = "67108864"
verifica "memory.swap.max = 0"                   test "$(cat "$(cg 9001)/memory.swap.max" 2>/dev/null)" = "0"
IO_APLICADO=0
if grep -q 'rbps=2097152' "$(cg 9001)/io.max" 2>/dev/null; then
  IO_APLICADO=1; ok "io.max aplicado: $(grep rbps "$(cg 9001)/io.max")"
else
  skip "io.max nao aplicado neste disco/kernel ($(cat /tmp/criar.err 2>/dev/null | tail -n1))"
fi

# ------------------------------------------------------------------ 3
secao "3. Execucao isolada: namespaces, usuario sem privilegios, stdout/stderr em arquivos"
cat > /tmp/job1.sh <<'EOF'
echo "hostname=$(hostname)"
echo "pid=$$"
echo "uid=$(id -u)"
echo "interfaces=$(ls /sys/class/net | tr '\n' ',')"
echo "segredos=$(ls /etc/compnuvem 2>/dev/null | wc -l)"
echo "este-e-stderr" >&2
echo "cgroup_ns=$(cat /proc/self/cgroup)"
exit 0
EOF
"$EXEC_BIN/executar.sh" 9001 9001 < /tmp/job1.sh >/tmp/exec.json
verifica "executar.sh retornou sucesso" test $? -eq 0
verifica "execucao terminou em ate 15 s" aguardar_fim 9001 15
S="$(saida 9001)"; echo "$S" | sed 's/^/        | /'
verifica "UTS namespace: hostname proprio (amb-9001)" has '^hostname=amb-9001$' "$S"
verifica "PID namespace: script e o PID 1"           has '^pid=1$' "$S"
verifica "roda sem privilegios (uid != 0)"           hasnt '^uid=0$' "$S"
verifica "NET namespace: apenas loopback"            has '^interfaces=lo,$' "$S"
verifica "mount namespace: segredos do host ocultos" has '^segredos=0$' "$S"
verifica "stdout gravado em stdout.log"              grep -q 'hostname=amb-9001' "$(dir 9001)/stdout.log"
verifica "stderr gravado em stderr.log"              grep -q 'este-e-stderr' "$(dir 9001)/stderr.log"
verifica "saida.log combina stdout e stderr"         has 'este-e-stderr' "$S"
verifica "status.sh = FINALIZADO com codigo 0"       bash -c "'$EXEC_BIN/status.sh' 9001 | grep -q '\"estado\":\"FINALIZADO\",\"codigo_saida\":0'"
verifica "host nao foi afetado (hostname do host preservado)" bash -c "[[ \"\$(hostname)\" != amb-9001 ]]"
"$EXEC_BIN/remover.sh" 9001 >/dev/null
verifica "remover.sh apagou o cgroup" test ! -d "$(cg 9001)"

# ------------------------------------------------------------------ 4
secao "4. Limite de CPU (500 millicores): laco infinito por ~6 s deve usar ~50% de 1 CPU"
"$EXEC_BIN/criar_ambiente.sh" 9002 500 128 2097152 >/dev/null 2>&1
echo 'while :; do :; done' | "$EXEC_BIN/executar.sh" 9002 9002 >/dev/null
sleep 1
U0=$(awk '/^usage_usec/{print $2}' "$(cg 9002)/cpu.stat"); T0=$(date +%s%N)
sleep 6
U1=$(awk '/^usage_usec/{print $2}' "$(cg 9002)/cpu.stat"); T1=$(date +%s%N)
PCT=$(awk -v u0="$U0" -v u1="$U1" -v t0="$T0" -v t1="$T1" 'BEGIN{printf "%.0f", (u1-u0)/((t1-t0)/1000)*100}')
echo "        uso medio de CPU no intervalo: ${PCT}% de 1 CPU (limite configurado: 50%)"
verifica "CPU limitada a no maximo ~60%"  test "$PCT" -le 60
verifica "CPU realmente utilizada (>= 30%)" test "$PCT" -ge 30
"$EXEC_BIN/parar.sh" 9002 >/dev/null
aguardar_fim 9002 10
"$EXEC_BIN/remover.sh" 9002 >/dev/null

# ------------------------------------------------------------------ 5
secao "5. Limite de memoria (64 MB): alocar 300 MB deve acionar o OOM killer"
"$EXEC_BIN/criar_ambiente.sh" 9003 1000 64 2097152 >/dev/null 2>&1
if command -v python3 >/dev/null; then
  echo 'python3 -c "x = bytearray(300*1024*1024); print(len(x))"; echo "sobreviveu"' \
    | "$EXEC_BIN/executar.sh" 9003 9003 >/dev/null
  aguardar_fim 9003 20
  S="$(saida 9003)"; echo "$S" | sed 's/^/        | /'
  verifica "processo foi morto (codigo diferente de 0)" bash -c "[[ \"\$(cat '$(dir 9003)/exitcode' 2>/dev/null)\" != 0 ]]"
  verifica "executor registrou estouro de memoria"      grep -q 'limite de memoria' <<<"$S"
else
  skip "python3 ausente"
fi
"$EXEC_BIN/remover.sh" 9003 >/dev/null

# ------------------------------------------------------------------ 6
secao "6. Limite de I/O (2 MiB/s): ler 8 MiB com O_DIRECT deve levar >= 2 s"
if (( IO_APLICADO )); then
  dd if=/dev/urandom of="$IO_ARQ" bs=1M count=16 status=none && sync && chmod 644 "$IO_ARQ"
  "$EXEC_BIN/criar_ambiente.sh" 9004 1000 128 2097152 >/dev/null 2>&1
  T0=$(date +%s.%N)
  echo "dd if=$IO_ARQ of=/dev/null bs=1M count=8 iflag=direct 2>&1" \
    | "$EXEC_BIN/executar.sh" 9004 9004 >/dev/null
  aguardar_fim 9004 60
  T1=$(date +%s.%N)
  DT=$(awk -v a="$T0" -v b="$T1" 'BEGIN{printf "%.1f", b-a}')
  echo "        leitura de 8 MiB levou ${DT}s ($(saida 9004 | tail -n1))"
  verifica "leitura limitada pelo io.max (>= 2 s)" bash -c "awk -v d=$DT 'BEGIN{exit !(d>=2)}'"
  "$EXEC_BIN/remover.sh" 9004 >/dev/null
else
  skip "io.max indisponivel"
fi

# ------------------------------------------------------------------ 7
secao "7. Programa que nao termina + processos filhos: parar.sh deve encerrar tudo"
"$EXEC_BIN/criar_ambiente.sh" 9005 1000 128 2097152 >/dev/null 2>&1
printf 'sleep 300 &\nsleep 300 &\necho iniciado\nwait\n' | "$EXEC_BIN/executar.sh" 9005 9005 >/dev/null
sleep 1.5
verifica "status.sh = EM_EXECUCAO"           bash -c "'$EXEC_BIN/status.sh' 9005 | grep -q '\"estado\":\"EM_EXECUCAO\"'"
NP=$(wc -l < "$(cg 9005)/cgroup.procs")
verifica "varios processos no cgroup (pai + filhos): $NP" test "$NP" -ge 3
verifica "saida parcial ja disponivel enquanto roda" grep -q iniciado "$(dir 9005)/saida.log"
"$EXEC_BIN/parar.sh" 9005 >/dev/null
verifica "execucao registrou o fim apos parar"   aguardar_fim 9005 10
verifica "cgroup ficou sem processos (filhos encerrados)" test ! -s "$(cg 9005)/cgroup.procs"
verifica "codigo de saida 137 (SIGKILL)"         test "$(cat "$(dir 9005)/exitcode")" = "137"
verifica "marcador de parada solicitada gravado" test -e "$(dir 9005)/stop"
"$EXEC_BIN/remover.sh" 9005 >/dev/null
verifica "recursos liberados (cgroup removido)"  test ! -d "$(cg 9005)"

# ------------------------------------------------------------------ 8
secao "8. Execucoes simultaneas em ambientes diferentes"
"$EXEC_BIN/criar_ambiente.sh" 9005 500 64 2097152 >/dev/null 2>&1
"$EXEC_BIN/criar_ambiente.sh" 9006 500 64 2097152 >/dev/null 2>&1
echo 'echo A; sleep 3; echo fim-A' | "$EXEC_BIN/executar.sh" 9007 9005 >/dev/null
echo 'echo B; sleep 3; echo fim-B' | "$EXEC_BIN/executar.sh" 9008 9006 >/dev/null
sleep 1
verifica "ambas EM_EXECUCAO ao mesmo tempo" bash -c "'$EXEC_BIN/status.sh' 9007 | grep -q EM_EXECUCAO && '$EXEC_BIN/status.sh' 9008 | grep -q EM_EXECUCAO"
aguardar_fim 9007 15; aguardar_fim 9008 15
verifica "saidas independentes (A e B)" bash -c "grep -q fim-A '$(dir 9007)/saida.log' && grep -q fim-B '$(dir 9008)/saida.log' && ! grep -q fim-B '$(dir 9007)/saida.log'"
"$EXEC_BIN/remover.sh" 9005 >/dev/null; "$EXEC_BIN/remover.sh" 9006 >/dev/null
rm -rf "$(dir 9007)" "$(dir 9008)"

# ------------------------------------------------------------------ resumo
printf '\n=== Resultado: %d OK, %d falhas, %d puladas ===\n' "$PASS" "$FAIL" "$SKIP"
(( FAIL == 0 ))
