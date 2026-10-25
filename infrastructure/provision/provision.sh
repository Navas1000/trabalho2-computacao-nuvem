#!/usr/bin/env bash
# provision.sh - Provisiona a VM do zero a partir do repositorio ja clonado.
#
# Chamado pelo Vagrantfile (como root) depois do 'git clone'. Pode ser executado novamente
# (idempotente): atualiza o codigo, recompila e reinicia os servicos sem perder segredos nem banco.
#
# Etapas: pacotes -> Node.js -> usuarios -> segredos -> MySQL -> build backend -> build frontend
#         -> executor Linux -> systemd/Nginx -> teste de fumaca -> resumo.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_HOME=/opt/compnuvem
CONF_DIR=/etc/compnuvem
WEB_ROOT=/var/www/compnuvem
NODE_VERSION="${NODE_VERSION:-22.14.0}"      # Angular 21 exige Node ^20.19 || ^22.12 || >=24
DB_NAME=compnuvem                            # fixo: banco-de-dados/banco.sql usa este nome
DB_USER=compnuvem
HOST_PORT="${HOST_PORT:-8080}"               # apenas para a mensagem final
INFRA="$REPO_DIR/infrastructure"

etapa() { printf '\n==> [%s] %s\n' "$(date +%T)" "$*"; }
aviso() { printf '!! AVISO: %s\n' "$*" >&2; }
falha() { printf '!! ERRO: %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || falha "execute como root"
[[ -d "$INFRA/scripts" ]] || falha "pasta infrastructure/scripts nao encontrada em $REPO_DIR"

# ---------------------------------------------------------------- cgroup v2
etapa "Verificando cgroups v2"
if [[ "$(stat -fc %T /sys/fs/cgroup)" != "cgroup2fs" ]]; then
  falha "cgroup v2 nao esta ativo. Use Ubuntu 22.04+ (ubuntu/jammy64) ou adicione 'systemd.unified_cgroup_hierarchy=1' ao kernel."
fi
echo "cgroup v2 ativo. Controladores: $(cat /sys/fs/cgroup/cgroup.controllers)"

# ---------------------------------------------------------------- pacotes
etapa "Instalando pacotes (Git, Java 17, MySQL, Nginx e utilitarios)"
apt-get update -y
apt-get install -y --no-install-recommends \
  ca-certificates curl git jq openssl xz-utils util-linux \
  openjdk-17-jdk-headless mysql-server nginx

# ---------------------------------------------------------------- Node.js
etapa "Instalando Node.js $NODE_VERSION"
if [[ "$(node -v 2>/dev/null || true)" != "v$NODE_VERSION" ]]; then
  arq="node-v$NODE_VERSION-linux-x64.tar.xz"
  tmp="$(mktemp -d)"
  curl -fsSL "https://nodejs.org/dist/v$NODE_VERSION/$arq" -o "$tmp/$arq"
  curl -fsSL "https://nodejs.org/dist/v$NODE_VERSION/SHASUMS256.txt" -o "$tmp/SHASUMS256.txt"
  (cd "$tmp" && grep " $arq\$" SHASUMS256.txt | sha256sum -c -)   # verifica integridade
  rm -rf /usr/local/lib/nodejs && mkdir -p /usr/local/lib/nodejs
  tar -xJf "$tmp/$arq" -C /usr/local/lib/nodejs --strip-components=1
  ln -sf /usr/local/lib/nodejs/bin/node /usr/local/bin/node
  ln -sf /usr/local/lib/nodejs/bin/npm  /usr/local/bin/npm
  ln -sf /usr/local/lib/nodejs/bin/npx  /usr/local/bin/npx
  rm -rf "$tmp"
fi
echo "node $(node -v) / npm $(npm -v) / $(java -version 2>&1 | head -n1)"

# ---------------------------------------------------------------- usuarios
etapa "Criando usuarios de servico"
# compnuvem: roda o backend (sem privilegios). compnuvem-job: executa os scripts dos usuarios.
id compnuvem >/dev/null 2>&1 || useradd --system --home-dir "$APP_HOME" --no-create-home --shell /usr/sbin/nologin compnuvem
id compnuvem-job >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin compnuvem-job

# ---------------------------------------------------------------- segredos
etapa "Gerando segredos e arquivos de configuracao em $CONF_DIR"
install -d -m 0750 -o root -g compnuvem "$CONF_DIR"
if [[ ! -s "$CONF_DIR/backend.env" ]]; then
  DB_PASSWORD="$(openssl rand -hex 24)"
  RUNNER_TOKEN="$(openssl rand -hex 32)"   # 64 caracteres (o backend exige >= 32)
  umask 077
  cat > "$CONF_DIR/backend.env" <<EOF
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
DB_URL=jdbc:mysql://localhost:3306/$DB_NAME?serverTimezone=UTC&characterEncoding=UTF-8
DB_USER=$DB_USER
DB_PASSWORD=$DB_PASSWORD
RUNNER_TOKEN=$RUNNER_TOKEN
FRONTEND_ORIGIN=http://localhost:$HOST_PORT
EOF
  cat > "$CONF_DIR/runner.env" <<EOF
API_URL=http://127.0.0.1:8080
RUNNER_TOKEN=$RUNNER_TOKEN
RUNNER_INTERVALO=1
EOF
  umask 022
else
  echo "Segredos ja existem; mantidos."
fi
chown root:compnuvem "$CONF_DIR/backend.env"; chmod 0640 "$CONF_DIR/backend.env"
chown root:root "$CONF_DIR/runner.env";       chmod 0600 "$CONF_DIR/runner.env"
# shellcheck disable=SC1091
DB_PASSWORD="$(grep '^DB_PASSWORD=' "$CONF_DIR/backend.env" | cut -d= -f2-)"

# ---------------------------------------------------------------- MySQL
etapa "Configurando MySQL e criando o banco"
systemctl enable --now mysql
for _ in $(seq 1 60); do mysqladmin ping --silent 2>/dev/null && break; sleep 1; done
mysqladmin ping --silent || falha "MySQL nao respondeu"

SCHEMA="$REPO_DIR/banco-de-dados/banco.sql"
if [[ -f "$SCHEMA" ]]; then
  mysql < "$SCHEMA"                       # banco.sql e idempotente (IF NOT EXISTS)
  echo "Esquema aplicado: $SCHEMA"
else
  aviso "esquema SQL nao encontrado em $SCHEMA; banco nao criado"
fi
# Usuario da aplicacao com o minimo de privilegios (a aplicacao nao altera o esquema).
mysql <<SQL
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT SELECT, INSERT, UPDATE, DELETE ON $DB_NAME.* TO '$DB_USER'@'localhost';
FLUSH PRIVILEGES;
SQL

# ---------------------------------------------------------------- backend
etapa "Compilando o backend (Spring Boot / Maven Wrapper)"
install -d -m 0755 "$APP_HOME/backend"
if [[ -f "$REPO_DIR/backend/pom.xml" ]]; then
  cd "$REPO_DIR/backend"
  sed -i 's/\r$//' mvnw                    # protege contra CRLF vindo do Windows
  chmod +x mvnw
  MAVEN_OPTS="-Xmx1g" ./mvnw -B -ntp -DskipTests clean package
  jar="$(ls target/*.jar | grep -v '\.original$' | head -n1)"
  install -m 0644 "$jar" "$APP_HOME/backend/app.jar"
  echo "Instalado: $APP_HOME/backend/app.jar"
else
  aviso "backend/pom.xml nao encontrado; backend nao compilado"
fi

# ---------------------------------------------------------------- frontend
etapa "Compilando o frontend (Angular)"
FRONT=""
for candidato in "$REPO_DIR/frontend/projeto-angular" "$REPO_DIR/frontend"; do
  [[ -f "$candidato/package.json" ]] && { FRONT="$candidato"; break; }
done
if [[ -n "$FRONT" ]]; then
  cd "$FRONT"
  export CI=1 NG_CLI_ANALYTICS=false NODE_OPTIONS="--max-old-space-size=3072"
  if [[ -f package-lock.json ]]; then npm ci --no-audit --no-fund; else aviso "sem package-lock.json; usando npm install"; npm install --no-audit --no-fund; fi
  npm run build
  index="$(find dist -name index.html -path '*browser*' | head -n1)"
  [[ -n "$index" ]] || index="$(find dist -name index.html | head -n1)"
  [[ -n "$index" ]] || falha "build do Angular nao gerou index.html"
  rm -rf "$WEB_ROOT" && install -d -m 0755 "$WEB_ROOT"
  cp -r "$(dirname "$index")"/. "$WEB_ROOT"/
  echo "Frontend publicado em $WEB_ROOT (origem: $(dirname "$index"))"
else
  aviso "projeto Angular nao encontrado; site nao publicado"
  install -d -m 0755 "$WEB_ROOT"
  echo '<h1>Frontend ainda nao disponivel</h1>' > "$WEB_ROOT/index.html"
fi

# ---------------------------------------------------------------- executor
etapa "Instalando o executor Linux (scripts de namespaces/cgroups)"
# Os scripts rodam como root; por isso ficam em pasta de propriedade do root (nao gravavel pelo backend).
install -d -m 0755 -o root -g root "$APP_HOME" "$APP_HOME/executor"
rm -f "$APP_HOME/executor/"*.sh
install -m 0755 -o root -g root "$INFRA"/scripts/*.sh "$APP_HOME/executor/"
install -d -m 0755 -o root -g root /var/lib/compnuvem /var/lib/compnuvem/exec
ls -l "$APP_HOME/executor"

# ---------------------------------------------------------------- servicos
etapa "Configurando systemd e Nginx"
install -m 0644 "$INFRA"/systemd/compnuvem-backend.service /etc/systemd/system/
install -m 0644 "$INFRA"/systemd/compnuvem-runner.service  /etc/systemd/system/
systemctl daemon-reload

install -m 0644 "$INFRA/nginx/compnuvem.conf" /etc/nginx/sites-available/compnuvem.conf
ln -sf /etc/nginx/sites-available/compnuvem.conf /etc/nginx/sites-enabled/compnuvem.conf
rm -f /etc/nginx/sites-enabled/default
nginx -t

systemctl enable nginx compnuvem-backend compnuvem-runner >/dev/null 2>&1
systemctl restart nginx
if [[ -f "$APP_HOME/backend/app.jar" ]]; then
  systemctl restart compnuvem-backend
  systemctl restart compnuvem-runner
fi

etapa "Aguardando o backend ficar saudavel (ate 3 min)"
ok=0
for _ in $(seq 1 90); do
  if curl -fsS http://127.0.0.1/api/health 2>/dev/null | grep -q UP; then ok=1; break; fi
  sleep 2
done
if (( ok )); then
  echo "Backend saudavel via Nginx: $(curl -fsS http://127.0.0.1/api/health)"
else
  aviso "backend nao respondeu. Veja: journalctl -u compnuvem-backend -n 80 --no-pager"
fi

# ---------------------------------------------------------------- teste de fumaca
if [[ -x "$INFRA/tests/teste_executor.sh" || -f "$INFRA/tests/teste_executor.sh" ]] && [[ "${SKIP_SELFTEST:-0}" != "1" ]]; then
  etapa "Executando teste de fumaca do executor (namespaces + cgroups)"
  EXECUTOR_DIR="$APP_HOME/executor" bash "$INFRA/tests/teste_executor.sh" \
    || aviso "teste de fumaca do executor reportou falhas (veja acima)"
fi

etapa "Provisionamento concluido"
cat <<EOF

  Aplicacao:  http://localhost:$HOST_PORT   (no host, via port forwarding do Vagrant)
  Servicos:   systemctl status compnuvem-backend compnuvem-runner nginx mysql
  Logs:       journalctl -u compnuvem-backend -u compnuvem-runner -f
  Logs jobs:  /var/lib/compnuvem/exec/<id_execucao>/{stdout,stderr,saida}.log

EOF
