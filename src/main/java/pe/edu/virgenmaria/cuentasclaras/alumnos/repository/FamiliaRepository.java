package pe.edu.virgenmaria.cuentasclaras.alumnos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;

/** Familias del colegio actual (las filtra {@code @TenantId}). */
public interface FamiliaRepository extends JpaRepository<Familia, Long> {
}
