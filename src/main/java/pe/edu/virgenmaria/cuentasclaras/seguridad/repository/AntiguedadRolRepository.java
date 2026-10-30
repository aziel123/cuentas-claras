package pe.edu.virgenmaria.cuentasclaras.seguridad.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.AntiguedadRol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.Optional;

/** Sprint 11, tanda 1c (A1c-2): solo lectura de {@code usuario_rol.desde}. Sin escrituras ni borrados. */
public interface AntiguedadRolRepository extends Repository<AntiguedadRol, AntiguedadRol.Clave> {

	Optional<AntiguedadRol> findByUsuarioIdAndRol(Long usuarioId, Rol rol);
}
