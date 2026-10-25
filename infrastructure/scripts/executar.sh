#!/usr/bin/env bash
# executar.sh - Executa um script dentro do ambiente isolado, em segundo plano.
#
# Uso: executar.sh <execucao_id> <ambiente_id>      (conteudo do script lido da ENTRADA PADRAO)
# Pre-requisito: criar_ambiente.sh <ambiente_id> ... ja executado.
# Saida (stdout): JSON  {"execucao_id":N,"ambiente_id":N,"pid":N,"diretorio":"..."}
#
# Nao recebe comandos nem caminhos arbitrarios: so IDs numericos e o conteudo do
# script, que sera executado sem privilegios dentro dos namespaces + cgroup.
# Arquivos gerados em <STATE_DIR>/<execucao_id>/ :
#   script.sh  stdout.log  stderr.log  saida.log (stdout+stderr em ordem)
#   supervisor.pid  exitcode (aparece quando termina)  stop (se houve pedido de parada)
source "$(dirname "$0")/lib.sh"

exigir_root
[[ $# -eq 2 ]] || die 2 "Uso: executar.sh <execucao_id> <ambiente_id> (script na entrada padrao)"
exec_id="$1"; amb="$2"
validar_uint execucao_id "$exec_id"
validar_uint ambiente_id "$amb"
exec_id=$((10#$exec_id)); amb=$((10#$amb))

cg=$(cgroup_ambiente "$amb")
[[ -d "$cg" ]] || die 3 "Ambiente $amb nao preparado (execute criar_ambiente.sh antes)"
[[ ! -s "$cg/cgroup.procs" ]] || die 4 "Ambiente $amb ja possui processos em execucao"

dir=$(dir_execucao "$exec_id")
[[ ! -e "$dir" ]] || die 4 "Execucao $exec_id ja existe"
mkdir -p "$STATE_DIR"
chmod 755 "$(dirname "$STATE_DIR")" 2>/dev/null || true
mkdir -m 0750 "$dir"
echo "$amb" > "$dir/ambiente_id"

# Le o script (limite de 256 KiB, igual ao backend).
head -c 262145 > "$dir/script.sh"
tamanho=$(stat -c %s "$dir/script.sh")
if (( tamanho == 0 )); then rm -rf "$dir"; die 2 "Script vazio"; fi
if (( tamanho > 262144 )); then rm -rf "$dir"; die 2 "Script acima de 256 KiB"; fi
chmod 0640 "$dir/script.sh"

# Supervisor desacoplado (setsid): sobrevive a reinicios do runner e registra o exitcode.
setsid "$EXECUTOR_DIR/supervisor.sh" "$exec_id" "$amb" </dev/null >/dev/null 2>&1 &
disown

# Espera o supervisor registrar o PID (ate 5 s).
for _ in $(seq 1 50); do
  [[ -s "$dir/supervisor.pid" ]] && break
  sleep 0.1
done
[[ -s "$dir/supervisor.pid" ]] || die 5 "Supervisor nao iniciou"

printf '{"execucao_id":%s,"ambiente_id":%s,"pid":%s,"diretorio":"%s"}\n' \
  "$exec_id" "$amb" "$(cat "$dir/supervisor.pid")" "$dir"
