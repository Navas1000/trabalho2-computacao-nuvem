#!/usr/bin/env bash
# teste_api.sh - Teste de ponta a ponta: Nginx -> Spring Boot -> MySQL -> runner -> executor Linux.
#
# Uso (na VM ou no host, com a aplicacao no ar):  bash infrastructure/tests/teste_api.sh [URL_BASE]
# URL_BASE padrao: http://localhost:8080 (no host) - dentro da VM use http://127.0.0.1
# Requer: curl e jq. Cria um usuario descartavel e remove o que criou ao final.
set -uo pipefail
BASE="${1:-http://localhost:8080}"
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); printf '  [ OK ] %s\n' "$*"; }
nok() { FAIL=$((FAIL+1)); printf '  [FALHA] %s\n' "$*"; }
chk() { local d="$1"; shift; if "$@"; then ok "$d"; else nok "$d"; fi; }
for c in curl jq; do command -v "$c" >/dev/null || { echo "Instale $c"; exit 2; }; done

TOKEN=""
api() { # api METODO caminho [json]  -> corpo em $R, codigo em $C
  local m="$1" p="$2" d="${3:-}" out
  local a=(-sS --max-time 20 -X "$m" -H "Authorization: Bearer $TOKEN")
  [[ -n "$d" ]] && a+=(-H 'Content-Type: application/json' -d "$d")
  out=$(curl "${a[@]}" -w '\n%{http_code}' "$BASE$p") || { C=000; R=""; return 1; }
  C="${out##*$'\n'}"; R="${out%$'\n'*}"
}
# esperar_status <id> <STATUS...> <segundos>
esperar_status() {
  local id="$1" t="${@: -1}" i s; local alvo=("${@:2:$#-2}")
  for i in $(seq 1 $(( t * 2 ))); do
    api GET "/api/execucoes/$id"; s=$(jq -r .status <<<"$R")
    for a in "${alvo[@]}"; do [[ "$s" == "$a" ]] && return 0; done
    sleep 0.5
  done
  return 1
}

echo "== Teste de API em $BASE"
api GET /api/health 2>/dev/null
chk "health UP (backend + MySQL)" test "$C" = "200"

EMAIL="teste$(date +%s)@exemplo.com"; SENHA="Senha-Teste-123"
api POST /api/auth/cadastro "$(jq -n --arg e "$EMAIL" --arg s "$SENHA" '{nome:"Teste",email:$e,senha:$s}')"
chk "cadastro de usuario (201)" test "$C" = "201"
api POST /api/auth/login "$(jq -n --arg e "$EMAIL" --arg s "$SENHA" '{email:$e,senha:$s}')"
TOKEN=$(jq -r .token <<<"$R")
chk "login retorna token" test -n "$TOKEN" -a "$TOKEN" != null

api GET /api/runner/fila
chk "API do runner nao e exposta pelo Nginx (404)" test "$C" = "404"

# Ambiente: 500 millicores, 64 MB, 2 MiB/s
api POST /api/ambientes '{"nome":"amb-teste","cpuMillicores":500,"memoriaMb":64,"ioBytesSegundo":2097152}'
AMB=$(jq -r .id <<<"$R"); chk "criar ambiente (201)" test "$C" = "201"

# Script que termina
api POST /api/scripts "$(jq -n '{nome:"ola.sh",conteudo:"echo ola-do-ambiente\necho erro-teste >&2\nhostname\nexit 0"}')"
S1=$(jq -r .id <<<"$R"); chk "armazenar script (201)" test "$C" = "201"
api POST "/api/ambientes/$AMB/execucoes" "{\"scriptId\":$S1}"
E1=$(jq -r .id <<<"$R"); chk "executar (201, assincrono)" test "$C" = "201"
chk "execucao ficou TERMINADO" esperar_status "$E1" TERMINADO 30
api GET "/api/execucoes/$E1/saida"
chk "saida contem stdout" grep -q 'ola-do-ambiente' <<<"$R"
chk "saida contem stderr" grep -q 'erro-teste' <<<"$R"
chk "hostname do ambiente (amb-$AMB)" grep -q "amb-$AMB" <<<"$R"

# Script com erro
api POST /api/scripts "$(jq -n '{nome:"erro.sh",conteudo:"echo falhando\nexit 7"}')"
S2=$(jq -r .id <<<"$R")
api POST "/api/ambientes/$AMB/execucoes" "{\"scriptId\":$S2}"; E2=$(jq -r .id <<<"$R")
chk "script com 'exit 7' ficou ERRO" esperar_status "$E2" ERRO 30
api GET "/api/execucoes/$E2"; chk "codigo_saida = 7" test "$(jq -r .codigo_saida <<<"$R")" = "7"

# Programa que nao termina + parar
api POST /api/scripts "$(jq -n '{nome:"loop.sh",conteudo:"while true; do echo tick; sleep 1; done"}')"
S3=$(jq -r .id <<<"$R")
api POST "/api/ambientes/$AMB/execucoes" "{\"scriptId\":$S3}"; E3=$(jq -r .id <<<"$R")
chk "programa infinito entrou em EM_EXECUCAO" esperar_status "$E3" EM_EXECUCAO 20
sleep 3
api GET "/api/execucoes/$E3/saida"; chk "saida parcial (tick) disponivel enquanto roda" grep -q tick <<<"$R"
api POST "/api/ambientes/$AMB/execucoes" "{\"scriptId\":$S1}"
chk "segunda execucao no mesmo ambiente ativo e recusada (409)" test "$C" = "409"
api POST "/api/execucoes/$E3/parar"
chk "execucao parada ficou CANCELADO" esperar_status "$E3" CANCELADO 30

# Ambientes simultaneos
api POST /api/ambientes '{"nome":"amb-2","cpuMillicores":300,"memoriaMb":64,"ioBytesSegundo":1048576}'; AMB2=$(jq -r .id <<<"$R")
api POST /api/scripts "$(jq -n '{nome:"dorme.sh",conteudo:"echo inicio; sleep 5; echo fim"}')"; S4=$(jq -r .id <<<"$R")
api POST "/api/ambientes/$AMB/execucoes"  "{\"scriptId\":$S4}"; EA=$(jq -r .id <<<"$R")
api POST "/api/ambientes/$AMB2/execucoes" "{\"scriptId\":$S4}"; EB=$(jq -r .id <<<"$R")
esperar_status "$EA" EM_EXECUCAO 15 && esperar_status "$EB" EM_EXECUCAO 15 && ok "ambos em execucao ao mesmo tempo" || nok "ambos em execucao ao mesmo tempo"
esperar_status "$EA" TERMINADO 30 && esperar_status "$EB" TERMINADO 30 && ok "ambos TERMINADO" || nok "ambos TERMINADO"

# Remocao
for e in $E1 $E2 $E3 $EA $EB; do api DELETE "/api/execucoes/$e"; done
api DELETE "/api/ambientes/$AMB";  chk "remover ambiente (204)" test "$C" = "204"
api DELETE "/api/ambientes/$AMB2"
for s in $S1 $S2 $S3 $S4; do api DELETE "/api/scripts/$s"; done

printf '\n=== Resultado: %d OK, %d falhas ===\n' "$PASS" "$FAIL"
(( FAIL == 0 ))
