#!/usr/bin/env bash
# runner.sh - Daemon que liga o backend Spring Boot ao executor Linux.
#
# O backend NAO executa comandos nem tem privilegios: ele apenas registra execucoes no banco.
# Este runner (systemd, root) consulta a API interna /api/runner/** autenticado por X-Runner-Token
# e traduz cada execucao nos 5 scripts do executor (criar_ambiente, executar, status, parar, remover).
#
# Fluxo por tick (RUNNER_INTERVALO segundos):
#   1. GET  /api/runner/fila                  -> execucoes PENDENTE (iniciar) e PARADA_SOLICITADA (parar)
#   2. POST /api/runner/execucoes/{id}/assumir -> recebe script + limites do ambiente; marca EM_EXECUCAO
#   3. POST /api/runner/execucoes/{id}/saida   -> envia a saida incremental
#   4. POST /api/runner/execucoes/{id}/finalizar -> TERMINADO | ERRO | CANCELADO + codigo de saida
#   5. reconciliacao: execucoes ativas no banco sem processo local (ex.: VM reiniciada) viram ERRO
#
# Variaveis (vindas de /etc/compnuvem/runner.env): RUNNER_TOKEN (obrigatoria), API_URL,
# RUNNER_INTERVALO, RETENCAO_DIAS.
source "$(dirname "$0")/lib.sh"
set +e   # o daemon precisa sobreviver a falhas pontuais (API fora do ar, script falhou, etc.)

API_URL="${API_URL:-http://127.0.0.1:8080}"
RUNNER_INTERVALO="${RUNNER_INTERVALO:-1}"
RETENCAO_DIAS="${RETENCAO_DIAS:-7}"
CHUNK_MAX=49152                 # bytes de saida enviados por requisicao
EXEC_BIN="$EXECUTOR_DIR"

exigir_root
[[ -n "${RUNNER_TOKEN:-}" ]] || die 1 "RUNNER_TOKEN nao definido"
for cmd in curl jq unshare setpriv iconv; do
  command -v "$cmd" >/dev/null || die 5 "Dependencia ausente: $cmd"
done

# O token vai para um arquivo de cabecalho (0600) para nao aparecer em 'ps'.
RUN_DIR="${RUNTIME_DIRECTORY:-/run/compnuvem-runner}"
mkdir -p "$RUN_DIR"; chmod 700 "$RUN_DIR"
HDR="$RUN_DIR/header"
( umask 077; printf 'X-Runner-Token: %s\n' "$RUNNER_TOKEN" > "$HDR" )
TMP="$RUN_DIR/tmp"; mkdir -p "$TMP"

info() { printf '%s [runner] %s\n' "$(date '+%F %T')" "$*"; }

HTTP_CODE=000; RESP=""
# api <METODO> <caminho> [arquivo_json]  -> preenche HTTP_CODE e RESP; retorna 1 se a rede falhar.
api() {
  local metodo="$1" caminho="$2" corpo="${3:-}" out
  local args=(-sS --max-time 20 -X "$metodo" -H "@$HDR")
  [[ -n "$corpo" ]] && args+=(-H 'Content-Type: application/json' --data-binary "@$corpo")
  if ! out=$(curl "${args[@]}" -w '\n%{http_code}' "$API_URL$caminho" 2>/dev/null); then
    HTTP_CODE=000; RESP=""; return 1
  fi
  HTTP_CODE="${out##*$'\n'}"
  RESP="${out%$'\n'*}"
  return 0
}

# Envia texto como saida da execucao. Retorna o codigo HTTP em HTTP_CODE.
enviar_texto() {
  local id="$1" arquivo="$2"
  tr -d '\000' < "$arquivo" | iconv -c -f UTF-8 -t UTF-8 2>/dev/null | jq -Rs '{texto: .}' > "$TMP/body.json"
  api POST "/api/runner/execucoes/$id/saida" "$TMP/body.json"
}

# Envia o que ainda nao foi enviado do saida.log. $2=1 => descarrega tudo (execucao terminou).
enviar_saida() {
  local id="$1" final="${2:-0}" dir off size n ultimo parcial
  dir=$(dir_execucao "$id")
  [[ -e "$dir/saida_encerrada" ]] && return 0
  local tentativas=0
  while (( tentativas++ < 100 )); do
    off=$(cat "$dir/enviado" 2>/dev/null || echo 0)
    size=$(stat -c %s "$dir/saida.log" 2>/dev/null || echo 0)
    (( size > off )) || return 0
    n=$(( size - off )); (( n > CHUNK_MAX )) && n=$CHUNK_MAX
    tail -c +$(( off + 1 )) "$dir/saida.log" | head -c "$n" > "$TMP/chunk"
    if (( ! final )); then
      # Execucao em andamento: nao corta linhas ao meio; espera a linha terminar.
      ultimo=$(tail -c1 "$TMP/chunk" | od -An -tx1 | tr -d ' \n')
      if [[ "$ultimo" != "0a" ]]; then
        parcial=$(tail -n1 "$TMP/chunk" | wc -c)
        if (( parcial < n )); then
          n=$(( n - parcial ))
          head -c "$n" "$TMP/chunk" > "$TMP/chunk2" && mv "$TMP/chunk2" "$TMP/chunk"
        elif (( n < CHUNK_MAX )); then
          return 0
        fi
      fi
    fi
    enviar_texto "$id" "$TMP/chunk"
    case "$HTTP_CODE" in
      204) echo $(( off + n )) > "$dir/enviado" ;;
      409) touch "$dir/saida_encerrada"; info "execucao $id: backend recusou mais saida (limite/inativa)"; return 0 ;;
      *)   return 1 ;;   # tenta de novo no proximo tick
    esac
  done
}

# finalizar_execucao <id> <STATUS> <codigo>  -> retorna 0 se o backend aceitou (ou ja estava final).
finalizar_execucao() {
  local id="$1" status="$2" codigo="$3"
  jq -n --arg s "$status" --argjson c "$codigo" '{status:$s, codigoSaida:$c}' > "$TMP/fim.json"
  api POST "/api/runner/execucoes/$id/finalizar" "$TMP/fim.json" || return 1
  case "$HTTP_CODE" in
    204) info "execucao $id finalizada: $status ($codigo)"; return 0 ;;
    409|404) info "execucao $id ja estava encerrada no backend (HTTP $HTTP_CODE)"; return 0 ;;
    *)   info "falha ao finalizar execucao $id (HTTP $HTTP_CODE)"; return 1 ;;
  esac
}

# Falha ao preparar/iniciar: registra a mensagem na saida e encerra como ERRO.
falhar_execucao() {
  local id="$1" codigo="$2" msg="$3"
  printf '[executor] %s\n' "$msg" > "$TMP/falha.txt"
  enviar_texto "$id" "$TMP/falha.txt"
  finalizar_execucao "$id" ERRO "$codigo"
}

iniciar_execucao() {
  local id="$1" dados amb cpu mem io
  [[ -e "$(dir_execucao "$id")" ]] && return 0        # ja tratada por este servidor
  api POST "/api/runner/execucoes/$id/assumir" || return 1
  if [[ "$HTTP_CODE" != "200" ]]; then
    [[ "$HTTP_CODE" == "409" ]] || info "assumir $id retornou HTTP $HTTP_CODE"
    return 0
  fi
  dados="$RESP"
  amb=$(jq -r '.ambiente.id' <<<"$dados")
  cpu=$(jq -r '.ambiente.cpu_millicores' <<<"$dados")
  mem=$(jq -r '.ambiente.memoria_mb' <<<"$dados")
  io=$(jq -r '.ambiente.io_bytes_segundo' <<<"$dados")
  info "execucao $id assumida (ambiente $amb: cpu=${cpu}m mem=${mem}MB io=${io}B/s)"

  local err
  if ! "$EXEC_BIN/criar_ambiente.sh" "$amb" "$cpu" "$mem" "$io" >/dev/null 2>"$TMP/err"; then
    err=$(tail -n1 "$TMP/err")
    falhar_execucao "$id" 125 "falha ao criar o ambiente: ${err:-erro desconhecido}"; return 0
  fi
  if ! jq -j '.execucao.script_conteudo' <<<"$dados" | "$EXEC_BIN/executar.sh" "$id" "$amb" >/dev/null 2>"$TMP/err"; then
    err=$(tail -n1 "$TMP/err")
    "$EXEC_BIN/remover.sh" "$amb" >/dev/null 2>&1
    falhar_execucao "$id" 126 "falha ao iniciar a execucao: ${err:-erro desconhecido}"; return 0
  fi
  touch "$(dir_execucao "$id")/ativo"
}

# Execucoes iniciadas por este runner que ainda nao foram reportadas como finalizadas.
processar_ativas() {
  local ativo dir id amb codigo status
  for ativo in "$STATE_DIR"/*/ativo; do
    [[ -e "$ativo" ]] || continue
    dir=$(dirname "$ativo"); id=$(basename "$dir")
    if [[ -s "$dir/exitcode" ]]; then
      enviar_saida "$id" 1 || continue                  # so finaliza depois de enviar toda a saida
      amb=$(cat "$dir/ambiente_id")
      "$EXEC_BIN/remover.sh" "$amb" >/dev/null 2>&1    # libera cgroup (CPU/memoria/I/O)
      codigo=$(cat "$dir/exitcode")
      if [[ -e "$dir/stop" ]]; then status=CANCELADO
      elif [[ "$codigo" == "0" ]]; then status=TERMINADO
      else status=ERRO; fi
      finalizar_execucao "$id" "$status" "$codigo" && rm -f "$ativo"
    else
      enviar_saida "$id" 0
    fi
  done
}

# Execucoes ativas no banco sem processo local (ex.: runner/VM reiniciado) nao podem ficar presas.
reconciliar() {
  api GET /api/runner/execucoes || return 1
  [[ "$HTTP_CODE" == "200" ]] || return 1
  local linha id status
  while IFS=$'\t' read -r id status; do
    [[ -n "$id" ]] || continue
    [[ -e "$(dir_execucao "$id")/ativo" ]] && continue
    [[ "$status" == "PENDENTE" ]] && continue
    info "reconciliando execucao orfa $id ($status)"
    if [[ "$status" == "PARADA_SOLICITADA" ]]; then
      finalizar_execucao "$id" CANCELADO 143
    else
      falhar_execucao "$id" 255 "execucao perdida: o executor foi reiniciado enquanto ela rodava"
    fi
  done < <(jq -r '.[] | [.id, .status] | @tsv' <<<"$RESP")
}

limpar_antigos() {
  # Remove logs de execucoes finalizadas ha mais de RETENCAO_DIAS dias e cgroups sem uso.
  find "$STATE_DIR" -mindepth 1 -maxdepth 1 -type d -mtime +"$RETENCAO_DIAS" \
       ! -exec test -e '{}/ativo' \; -exec rm -rf {} + 2>/dev/null
  local cg amb
  for cg in "$CGROUP_ROOT"/amb-*; do
    [[ -d "$cg" && ! -s "$cg/cgroup.procs" ]] || continue
    amb=${cg##*/amb-}
    "$EXEC_BIN/remover.sh" "$amb" >/dev/null 2>&1
  done
}

parar_loop=0
trap 'parar_loop=1' TERM INT   # encerra o laco; os supervisores continuam (KillMode=process)

info "runner iniciado (API_URL=$API_URL, intervalo=${RUNNER_INTERVALO}s)"
mkdir -p "$STATE_DIR"
preparar_raiz_cgroup 2>/dev/null
conectado=0; tick=0
while (( ! parar_loop )); do
  if api GET /api/runner/fila && [[ "$HTTP_CODE" == "200" ]]; then
    if (( ! conectado )); then
      info "conectado ao backend"; conectado=1
      limpar_antigos
      reconciliar
    fi
    while IFS=$'\t' read -r id status; do
      [[ -n "$id" ]] || continue
      case "$status" in
        PENDENTE)          iniciar_execucao "$id" ;;
        PARADA_SOLICITADA) [[ -e "$(dir_execucao "$id")/ativo" ]] && "$EXEC_BIN/parar.sh" "$id" >/dev/null 2>&1 ;;
      esac
    done < <(jq -r '.[] | [.id, .status] | @tsv' <<<"$RESP")
    processar_ativas
    (( ++tick % 30 == 0 )) && reconciliar
  else
    if (( conectado )); then info "backend indisponivel (HTTP $HTTP_CODE); aguardando"; conectado=0; fi
  fi
  sleep "$RUNNER_INTERVALO" & wait $!
done
info "runner encerrado"
