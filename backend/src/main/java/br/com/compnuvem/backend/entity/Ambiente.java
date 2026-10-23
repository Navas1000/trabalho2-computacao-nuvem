package br.com.compnuvem.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "ambientes")
public class Ambiente {
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


    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "cpu_millicores", nullable = false)
    private Integer cpuMillicores;

    @Column(name = "memoria_mb", nullable = false)
    private Integer memoriaMb;

    @Column(name = "io_bytes_segundo", nullable = false)
    private Long ioBytesSegundo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    void antesDeSalvar() {
        criadoEm = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Integer getCpuMillicores() {
        return cpuMillicores;
    }

    public void setCpuMillicores(Integer cpuMillicores) {
        this.cpuMillicores = cpuMillicores;
    }

    public Integer getMemoriaMb() {
        return memoriaMb;
    }

    public void setMemoriaMb(Integer memoriaMb) {
        this.memoriaMb = memoriaMb;
    }

    public Long getIoBytesSegundo() {
        return ioBytesSegundo;
    }

    public void setIoBytesSegundo(Long ioBytesSegundo) {
        this.ioBytesSegundo = ioBytesSegundo;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }
}
