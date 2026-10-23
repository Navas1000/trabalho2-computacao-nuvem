package br.com.compnuvem.backend.repository;

import br.com.compnuvem.backend.entity.Execucao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExecucaoRepository extends JpaRepository<Execucao, Long> {
    List<Execucao> findTop500ByUsuarioIdOrderByIdDesc(long usuarioId);
    Optional<Execucao> findByIdAndUsuarioId(long id, long usuarioId);
    List<Execucao> findTop100ByStatusInOrderByIdAsc(Collection<String> status);
    List<Execucao> findTop500ByStatusInOrderByIdAsc(Collection<String> status);
    boolean existsByAmbienteIdAndStatusIn(long ambienteId, Collection<String> status);
    boolean existsByScriptId(long scriptId);
    void deleteByAmbienteId(long ambienteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Execucao e where e.id = :id and e.usuario.id = :usuarioId")
    Optional<Execucao> buscarDoUsuarioComBloqueio(@Param("id") long id, @Param("usuarioId") long usuarioId);

    // Acesso global reservado ao executor, depois de validar X-Runner-Token.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Execucao e where e.id = :id")
    Optional<Execucao> buscarComBloqueio(@Param("id") long id);
}
