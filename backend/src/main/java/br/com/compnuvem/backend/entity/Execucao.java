package br.com.compnuvem.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "execucoes")
public class Execucao {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ambiente_id", nullable = false)
    private Ambiente ambiente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "script_id", nullable = false)
    private Script script;

    @Column(name = "script_conteudo", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String scriptConteudo;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "codigo_saida", nullable = true)
    private Integer codigoSaida;

    @Column(name = "saida", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String saida;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void antesDeSalvar() {
        criadoEm = LocalDateTime.now(ZoneOffset.UTC);
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    void antesDeAtualizar() {
        atualizadoEm = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Ambiente getAmbiente() {
        return ambiente;
    }

    public void setAmbiente(Ambiente ambiente) {
        this.ambiente = ambiente;
    }

    public Script getScript() {
        return script;
    }

    public void setScript(Script script) {
        this.script = script;
    }

    public String getScriptConteudo() {
        return scriptConteudo;
    }

    public void setScriptConteudo(String scriptConteudo) {
        this.scriptConteudo = scriptConteudo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCodigoSaida() {
        return codigoSaida;
    }

    public void setCodigoSaida(Integer codigoSaida) {
        this.codigoSaida = codigoSaida;
    }

    public String getSaida() {
        return saida;
    }

    public void setSaida(String saida) {
        this.saida = saida;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
