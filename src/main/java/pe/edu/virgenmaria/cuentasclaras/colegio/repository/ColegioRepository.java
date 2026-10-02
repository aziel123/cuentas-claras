package pe.edu.virgenmaria.cuentasclaras.colegio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;

import java.util.List;

public interface ColegioRepository extends JpaRepository<Colegio, Long> {

	List<Colegio> findByActivoTrueOrderByIdAsc();
}
