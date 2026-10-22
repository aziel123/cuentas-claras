package pe.edu.virgenmaria.cuentasclaras.comunicados.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.Comunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EstadoComunicado;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 9, tanda 3: comunicados del colegio actual (los filtra {@code @TenantId}). Sin {@code delete*} ni
 * {@code @Modifying}: un comunicado no se borra (se retira) ni se edita (se corrige con una fe de erratas).
 */
public interface ComunicadoRepository extends Repository<Comunicado, Long> {

	Comunicado saveAndFlush(Comunicado comunicado);

	Optional<Comunicado> findById(Long id);

	List<Comunicado> findByIdIn(Collection<Long> ids);

	/** Todos los del colegio, del más reciente al más antiguo (Dirección y Promotoría). */
	List<Comunicado> findAllByOrderByIdDesc(Limit limite);

	/** Los de un emisor (el docente ve solo los suyos). */
	List<Comunicado> findByEmisorUsuarioIdOrderByIdDesc(Long emisorUsuarioId, Limit limite);

	Optional<Comunicado> findBySolicitudId(Long solicitudId);

	boolean existsByHuellaContenido(String huellaContenido);

	List<Comunicado> findByCorrigeIdOrderByIdAsc(Long corrigeId);

	/** Los enviados desde una hora (los avisos del día y el contador de «nuevos de docentes»). */
	List<Comunicado> findByEstadoAndEnviadoEnGreaterThanEqualOrderByIdAsc(EstadoComunicado estado, LocalDateTime desde);

	List<Comunicado> findByEstadoInOrderByIdDesc(Collection<EstadoComunicado> estados);
}
