package pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;

import java.util.List;
import java.util.Optional;

/** Solicitudes del colegio actual. Sin borrados ni {@code @Modifying}. */
public interface SolicitudCambioRepository extends Repository<SolicitudCambio, Long> {

	SolicitudCambio save(SolicitudCambio solicitud);

	SolicitudCambio saveAndFlush(SolicitudCambio solicitud);

	Optional<SolicitudCambio> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SolicitudCambio s where s.id = :id")
	Optional<SolicitudCambio> bloquear(@Param("id") Long id);

	boolean existsByTipoAndEntidadAndEntidadIdAndEstado(TipoSolicitud tipo, String entidad, Long entidadId,
			EstadoSolicitud estado);

	List<SolicitudCambio> findByEntidadAndEntidadIdAndEstadoOrderByIdAsc(String entidad, Long entidadId,
			EstadoSolicitud estado);

	List<SolicitudCambio> findByEstadoOrderByIdAsc(EstadoSolicitud estado);

	List<SolicitudCambio> findTop30ByEstadoNotOrderByResueltoEnDescIdDesc(EstadoSolicitud estado);

	List<SolicitudCambio> findByTipoOrderByIdDesc(TipoSolicitud tipo);

	/** La última solicitud de ese tipo para esa entidad en ese estado (por ejemplo, la devolución APROBADA de una orden). */
	Optional<SolicitudCambio> findFirstByTipoAndEntidadAndEntidadIdAndEstadoOrderByIdDesc(TipoSolicitud tipo, String entidad,
			Long entidadId, EstadoSolicitud estado);
}
