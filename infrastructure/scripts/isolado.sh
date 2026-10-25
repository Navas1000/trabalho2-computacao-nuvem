#!/usr/bin/env bash
# isolado.sh - INTERNO. Roda DENTRO dos namespaces (iniciado pelo supervisor via unshare).
#
# Uso: isolado.sh <execucao_id> <ambiente_id>
# Prepara o sistema de arquivos do ambiente, descarta privilegios e executa o script do usuario.
source "$(dirname "$0")/lib.sh"

exec_id="$1"; amb="$2"
validar_uint execucao_id "$exec_id"
validar_uint ambiente_id "$amb"
origem="$(dir_execucao "$exec_id")/script.sh"

# UTS namespace: hostname proprio.
hostname "amb-$amb"

# Mount namespace: /tmp privado em tmpfs (a memoria do tmpfs conta no limite do cgroup).
mount -t tmpfs -o "size=${TMP_TAMANHO_MB}m,mode=1777,nosuid,nodev" tmpfs /tmp

# Usuario sem privilegios que executara o script.
if id "$JOB_USER" >/dev/null 2>&1; then
  uid=$(id -u "$JOB_USER"); gid=$(id -g "$JOB_USER")
else
  uid=65534; gid=65534
fi
install -m 0755 -o "$uid" -g "$gid" "$origem" /tmp/script.sh

# Esconde do job os dados do executor e os segredos (token do runner, senha do banco).
for oculto in "$(dirname "$STATE_DIR")" /etc/compnuvem /opt/compnuvem; do
  [[ -d "$oculto" ]] && mount -t tmpfs -o size=1m,mode=0555 tmpfs "$oculto"
done

cd /tmp
# Se o script comeca com shebang (#!), respeita o interpretador; senao usa bash.
if [[ "$(head -c2 /tmp/script.sh)" == "#!" ]]; then
  comando=(/tmp/script.sh)
else
  comando=(/bin/bash /tmp/script.sh)
fi

# Rede: o namespace 'net' novo so possui loopback (sem acesso a rede externa nem ao MySQL local).
# Privilegios: usuario comum, sem grupos extras, sem capabilities e sem ganhar novos privilegios.
exec env -i HOME=/tmp USER="$JOB_USER" LOGNAME="$JOB_USER" LANG=C.UTF-8 \
     PATH=/usr/local/bin:/usr/bin:/bin TERM=dumb \
  setpriv --reuid="$uid" --regid="$gid" --clear-groups \
          --bounding-set=-all --inh-caps=-all --no-new-privs \
  "${comando[@]}"
