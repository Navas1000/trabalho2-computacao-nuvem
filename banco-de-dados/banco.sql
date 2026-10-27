-- Banco padrao da infraestrutura: compnuvem. A API permite outra conexao por DB_URL.
-- DB_URL nao renomeia o schema deste arquivo: para outro nome, ajustar CREATE DATABASE/USE.
-- Dados de demonstracao separados em banco-de-dados/dados_teste.sql (importacao opcional).
-- Estrutura completa do projeto. MySQL Server 8.0.16+.
-- Executar em um banco novo. Nao apaga dados nem atualiza estruturas antigas.
-- Cadastros de usuarios e hashes de senha sao gerados pela API.
CREATE DATABASE IF NOT EXISTS compnuvem
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE compnuvem;

CREATE TABLE IF NOT EXISTS usuarios (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    senha_hash VARCHAR(255) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_usuario_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS sessoes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expira_em DATETIME NOT NULL,
    CONSTRAINT uk_sessao_token UNIQUE (token_hash),
    CONSTRAINT fk_sessao_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX ix_sessao_expiracao (expira_em)
);

CREATE TABLE IF NOT EXISTS scripts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    nome VARCHAR(120) NOT NULL,
    conteudo MEDIUMTEXT NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_script_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT uk_script_dono UNIQUE (id, usuario_id),
    INDEX ix_script_usuario (usuario_id, id)
);

CREATE TABLE IF NOT EXISTS ambientes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    nome VARCHAR(120) NOT NULL,
    cpu_millicores INT NOT NULL,
    memoria_mb INT NOT NULL,
    io_bytes_segundo BIGINT NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ambiente_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT uk_ambiente_dono UNIQUE (id, usuario_id),
    CONSTRAINT ck_cpu CHECK (cpu_millicores BETWEEN 1 AND 64000),
    CONSTRAINT ck_memoria CHECK (memoria_mb BETWEEN 16 AND 1048576),
    CONSTRAINT ck_io CHECK (io_bytes_segundo > 0),
    INDEX ix_ambiente_usuario (usuario_id, id)
);

CREATE TABLE IF NOT EXISTS execucoes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    ambiente_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    script_conteudo MEDIUMTEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDENTE',
    codigo_saida INT NULL,
    saida MEDIUMTEXT NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_execucao_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_execucao_ambiente_dono FOREIGN KEY (ambiente_id, usuario_id)
        REFERENCES ambientes(id, usuario_id),
    CONSTRAINT fk_execucao_script_dono FOREIGN KEY (script_id, usuario_id)
        REFERENCES scripts(id, usuario_id),
    CONSTRAINT ck_status CHECK (status IN (
        'PENDENTE', 'EM_EXECUCAO', 'PARADA_SOLICITADA', 'TERMINADO', 'ERRO', 'CANCELADO'
    )),
    INDEX ix_fila (status, id),
    INDEX ix_ambiente (ambiente_id),
    INDEX ix_execucao_usuario (usuario_id, id)
);
