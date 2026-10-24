package pe.edu.virgenmaria.cuentasclaras.alumnos.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.HistorialMatricula;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Correcciones del sprint 10 (V38): la historia de la matrícula, SOLO lectura (la escribe trg_matricula_estado). */
public interface HistorialMatriculaRepository extends Repository<HistorialMatricula, Long> {

	/** Los cambios posteriores a un momento, del más antiguo al más nuevo (el primero dice cómo estaba en ese momento). */
	List<HistorialMatricula> findByMatriculaIdInAndCambiadoBdGreaterThanOrderByCambiadoBdAscIdAsc(
			Collection<Long> matriculas, LocalDateTime desde);
}
