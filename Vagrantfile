# -*- mode: ruby -*-
# vi: set ft=ruby :
#
# Projeto 2 - Computacao em Nuvem (PUC-Campinas)
# `vagrant up` cria a VM, clona o projeto do GitHub, instala tudo, compila backend e frontend,
# cria o banco, configura systemd + Nginx e publica a aplicacao em http://localhost:8080.
#
# Nada depende de arquivos locais: a pasta compartilhada e desabilitada e todo o codigo vem do GitHub.
# Variaveis opcionais (ambiente do host):
#   REPO_URL     repositorio a clonar            (padrao: repositorio do projeto)
#   REPO_BRANCH  branch a clonar                 (padrao: main)
#   HOST_PORT    porta do host para a aplicacao  (padrao: 8080)
#   PRIVATE_IP   IP de rede privada opcional     (ex.: 192.168.56.10)
# Exemplo (PowerShell):  $env:REPO_BRANCH="infra"; vagrant up

REPO_URL    = ENV.fetch("REPO_URL", "https://github.com/Navas1000/trabalho2-computacao-nuvem.git")
REPO_BRANCH = ENV.fetch("REPO_BRANCH", "main")
HOST_PORT   = ENV.fetch("HOST_PORT", "8080").to_i
PRIVATE_IP  = ENV["PRIVATE_IP"]

# Bootstrap minimo: instala o Git, baixa o projeto e entrega o controle ao provision.sh do repositorio.
BOOTSTRAP = <<~'SCRIPT'
  set -euo pipefail
  export DEBIAN_FRONTEND=noninteractive
  echo "==> Instalando Git e dependencias basicas"
  apt-get update -y
  apt-get install -y --no-install-recommends git ca-certificates curl
  DEST=/opt/compnuvem/src
  mkdir -p /opt/compnuvem
  if [ -d "$DEST/.git" ]; then
    echo "==> Atualizando $DEST (branch $REPO_BRANCH)"
    git -C "$DEST" fetch --depth 1 origin "$REPO_BRANCH"
    git -C "$DEST" checkout -B "$REPO_BRANCH" FETCH_HEAD
  else
    echo "==> Clonando $REPO_URL (branch $REPO_BRANCH)"
    git clone --depth 1 --branch "$REPO_BRANCH" "$REPO_URL" "$DEST"
  fi
  exec bash "$DEST/infrastructure/provision/provision.sh"
SCRIPT

Vagrant.configure("2") do |config|
  config.vm.box = "ubuntu/jammy64"          # Ubuntu 22.04 LTS (cgroups v2 por padrao)
  config.vm.box_check_update = false
  config.vm.hostname = "compnuvem"
  config.vm.boot_timeout = 600

  # O projeto vem do GitHub, nao do computador do desenvolvedor.
  config.vm.synced_folder ".", "/vagrant", disabled: true

  # Rede: NAT (internet para apt/npm/maven/git) + porta da aplicacao encaminhada para o host.
  config.vm.network "forwarded_port", guest: 80, host: HOST_PORT, host_ip: "127.0.0.1", auto_correct: true
  config.vm.network "private_network", ip: PRIVATE_IP if PRIVATE_IP

  config.vm.provider "virtualbox" do |vb|
    vb.name   = "compnuvem-projeto2"
    vb.cpus   = 1
    vb.memory = 4096
    vb.customize ["modifyvm", :id, "--natdnshostresolver1", "on"]  # evita falhas de DNS em redes de laboratorio
    vb.customize ["modifyvm", :id, "--natdnsproxy1", "on"]
  end

  config.vm.provision "shell", name: "provisionamento-github", privileged: true,
                      env: { "REPO_URL" => REPO_URL, "REPO_BRANCH" => REPO_BRANCH, "HOST_PORT" => HOST_PORT.to_s },
                      inline: BOOTSTRAP
end
