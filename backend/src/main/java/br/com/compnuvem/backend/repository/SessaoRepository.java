package br.com.compnuvem.backend.repository;

import br.com.compnuvem.backend.entity.Sessao;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDateTime;
import java.util.Optional;

public interface SessaoRepository extends JpaRepository<Sessao, Long> {
    @EntityGraph(attributePaths = "usuario")
    Optional<Sessao> findByTokenHashAndExpiraEmAfter(String tokenHash, LocalDateTime agora);

    void deleteByTokenHashAndUsuarioId(String tokenHash, long usuarioId);
}
