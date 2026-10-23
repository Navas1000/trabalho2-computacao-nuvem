package br.com.compnuvem.backend.repository;

import br.com.compnuvem.backend.entity.Ambiente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AmbienteRepository extends JpaRepository<Ambiente, Long> {
    List<Ambiente> findTop500ByUsuarioIdOrderByIdDesc(long usuarioId);
    Optional<Ambiente> findByIdAndUsuarioId(long id, long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Ambiente a where a.id = :id and a.usuario.id = :usuarioId")
    Optional<Ambiente> buscarComBloqueio(@Param("id") long id, @Param("usuarioId") long usuarioId);
}
