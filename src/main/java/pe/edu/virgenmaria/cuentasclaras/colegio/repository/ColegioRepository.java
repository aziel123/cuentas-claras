package pe.edu.virgenmaria.cuentasclaras.colegio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;

import java.util.List;
import java.util.Optional;

public interface ColegioRepository extends JpaRepository<Colegio, Long> {

	List<Colegio> findByActivoTrueOrderByIdAsc();

	Optional<Colegio> findFirstByNombreOrderByIdAsc(String nombre);
}
