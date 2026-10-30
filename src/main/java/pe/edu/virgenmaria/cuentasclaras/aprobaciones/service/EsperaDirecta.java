package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 11, tanda 1c (A1c-2 y A1c-3, decisión 223): las directas que esperan 24 horas antes de aplicarse (con freno):
 * dar PROMOTOR en PRODUCCION, quitar PROMOTOR y desactivar la cuenta de una Promotoría. La misma regla que
 * trg_solicitud_cambio_resuelta (que pone {@code aplica_desde} en MySQL); en H2 la espera se calcula aquí, desde la
 * resolución.
 */
@Component
public class EsperaDirecta {

	/** La espera (la base la lee de configuracion_bd, {@code espera_directa_horas}: 24 en prod). */
	public static final Duration ESPERA = Duration.ofHours(24);

	private final UsuarioRepository usuarios;

	private final ColegioRepository colegios;

	public EsperaDirecta(UsuarioRepository usuarios, ColegioRepository colegios) {
		this.usuarios = usuarios;
		this.colegios = colegios;
	}

	/** Si esta solicitud, resuelta directo, debe esperar (con la cuenta como está ahora: todavía sin el cambio). */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public boolean requiere(SolicitudCambio solicitud) {
		if (!"usuario".equals(solicitud.getEntidad())) {
			return false;
		}
		Optional<Usuario> cuenta = usuarios.findById(solicitud.getEntidadId());
		if (cuenta.isEmpty()) {
			return false;
		}
		boolean esPromotor = cuenta.get().getRoles().contains(Rol.PROMOTOR);
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		if (solicitud.getTipo() == TipoSolicitud.CAMBIO_ROLES) {
			boolean quedaPromotor = Arrays.asList(datos.getOrDefault("roles", "").split(",")).contains(Rol.PROMOTOR.name());
			boolean produccion = colegios.findById(ContextoColegio.actual())
					.map(c -> c.getEstado() == EstadoColegio.PRODUCCION).orElse(false);
			return (esPromotor && !quedaPromotor) || (!esPromotor && quedaPromotor && produccion);
		}
		if (solicitud.getTipo() == TipoSolicitud.ESTADO_CUENTA) {
			return esPromotor && "false".equals(datos.get("activo"));
		}
		return false;
	}

	/**
	 * Hasta cuándo espera una directa resuelta: la fecha que puso la base (MySQL) o, en H2, la resolución más 24 horas.
	 * {@code null} si no espera.
	 */
	public LocalDateTime vence(SolicitudCambio solicitud, boolean requiere) {
		if (solicitud.getAplicaDesde() != null) {
			return solicitud.getAplicaDesde();
		}
		return requiere && solicitud.isDirecta() && solicitud.getResueltoEn() != null
				? solicitud.getResueltoEn().plus(ESPERA) : null;
	}
}
