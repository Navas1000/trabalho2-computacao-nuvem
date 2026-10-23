package br.com.compnuvem.backend.repository;

import br.com.compnuvem.backend.entity.Script;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScriptRepository extends JpaRepository<Script, Long> {
    List<Script> findTop500ByUsuarioIdOrderByIdDesc(long usuarioId);
    Optional<Script> findByIdAndUsuarioId(long id, long usuarioId);
}
