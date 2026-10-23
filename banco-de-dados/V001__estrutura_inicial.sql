-- V001: estrutura inicial reconstruida a partir do enunciado.
-- Requer MySQL Server 8.0.16+ (Workbench e apenas o cliente).
CREATE DATABASE IF NOT EXISTS compnuvem
 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE compnuvem;

CREATE TABLE IF NOT EXISTS scripts (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 nome VARCHAR(120) NOT NULL,
 conteudo MEDIUMTEXT NOT NULL,
 criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ambientes (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 nome VARCHAR(120) NOT NULL,
 cpu_millicores INT NOT NULL,
 memoria_mb INT NOT NULL,
 io_bytes_segundo BIGINT NOT NULL,
 criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_cpu CHECK (cpu_millicores BETWEEN 1 AND 64000),
 CONSTRAINT ck_memoria CHECK (memoria_mb BETWEEN 16 AND 1048576),
 CONSTRAINT ck_io CHECK (io_bytes_segundo > 0)
);

CREATE TABLE IF NOT EXISTS execucoes (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 ambiente_id BIGINT NOT NULL,
 script_id BIGINT NOT NULL,
 script_conteudo MEDIUMTEXT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDENTE',
 codigo_saida INT NULL,
 saida MEDIUMTEXT NOT NULL,
 criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CONSTRAINT fk_execucao_ambiente FOREIGN KEY (ambiente_id) REFERENCES ambientes(id),
 CONSTRAINT fk_execucao_script FOREIGN KEY (script_id) REFERENCES scripts(id),
 CONSTRAINT ck_status CHECK (status IN ('PENDENTE','EM_EXECUCAO','PARADA_SOLICITADA','TERMINADO','ERRO','CANCELADO')),
 INDEX ix_fila (status,id),
 INDEX ix_ambiente (ambiente_id)
);
