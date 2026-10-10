package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aplica un ESTADO_CUENTA aprobado por otra persona (correcciones del sprint 7, observación de QA): desactiva o reactiva
 * una cuenta con Promotoría o Dirección. Corre en la ruta de identidad ({@code cc_sistema}) dentro de la transacción de
 * la bandeja, con la solicitud ya APROBADA y firmada (trg_usuario_identidad exige las dos cosas en MySQL). Ni quien lo
 * pidió ni el titular lo aprueban. El colegio nunca queda sin Promotoría activa. Si el estado cambió desde que se pidió,
 * no se aplica.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorEstadoCuenta implements ManejadorSolicitud {

	/** Datos de la solicitud: el estado final de la cuenta («true» activa, «false» desactivada). */
	static final String ACTIVO = "activo";

	private final UsuarioRepository usuarios;

	private final AuditoriaService auditoria;

	private final SesionesUsuario sesionesHttp;

	private final SesionesFirmadas sesiones;

	private final Clock reloj;

	public ManejadorEstadoCuenta(UsuarioRepository usuarios, AuditoriaService auditoria, SesionesUsuario sesionesHttp,
			SesionesFirmadas sesiones, Clock reloj) {
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.sesionesHttp = sesionesHttp;
		this.sesiones = sesiones;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.ESTADO_CUENTA;
	}

	/** El titular no aprueba su propia desactivación o reactivación. */
	@Override
	public Set<String> involucrados(SolicitudCambio solicitud) {
		return usuarios.findById(solicitud.getEntidadId()).map(u -> Set.of(u.getNombreUsuario())).orElse(Set.of());
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		return List.of(activar(solicitud) ? "Reactivar la cuenta: vuelve a poder ingresar y aprobar."
				: "Desactivar la cuenta: ya no podrá ingresar ni aprobar; sus sesiones se cierran.");
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "Promotoría y Dirección aprueban anulaciones, descuentos, cierres y cambios de contacto. Apruébalo solo si "
				+ "confirmaste en persona que corresponde.";
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Usuario usuario = usuarios.bloquearPorId(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("La persona de la solicitud no existe."));
		boolean activar = activar(solicitud);
		if (usuario.isActivo() == activar) {
			throw new ReglaNegocioException("La cuenta de " + usuario.getNombreCompleto() + " ya está "
					+ (activar ? "activa" : "desactivada") + ": rechaza esta solicitud.");
		}
		if (!activar && usuario.getRoles().contains(Rol.PROMOTOR)
				&& usuarios.bloquearActivosConRol(Rol.PROMOTOR).size() <= 1) {
			throw new ReglaNegocioException("No se puede desactivar al último usuario de Promotoría activo: el colegio nunca "
					+ "queda sin Promotoría.");
		}
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		usuario.aplicarEstadoAprobado(activar, solicitud.getId(), aprobador, ahora);
		usuarios.saveAndFlush(usuario);
		String detalle = "Usuario " + usuario.getNombreUsuario() + " (" + usuario.getRoles().stream().sorted()
				.map(Rol::etiqueta).reduce((a, b) -> a + " · " + b).orElse("sin roles") + "), con la solicitud "
				+ solicitud.getId() + " pedida por " + solicitud.getSolicitadoPor() + " y aprobada por " + aprobador
				+ ". Motivo: " + solicitud.getMotivo();
		if (activar) {
			auditoria.registrar(AccionAuditoria.USUARIO_REACTIVADO, "usuario", usuario.getId().toString(), "inactivo",
					"activo", detalle);
		}
		else {
			auditoria.registrar(AccionAuditoria.USUARIO_DESACTIVADO, "usuario", usuario.getId().toString(), "activo",
					"inactivo", detalle);
			sesiones.cerrarDe(usuario.getId(), MotivoCierreSesion.CUENTA_CAMBIADA);
			sesionesHttp.expirar(usuario.getId());
		}
	}

	/** Los datos de la solicitud. */
	static Map<String, String> datos(boolean activar) {
		return Map.of(ACTIVO, Boolean.toString(activar));
	}

	private static boolean activar(SolicitudCambio solicitud) {
		return Boolean.parseBoolean(DatosSolicitud.leer(solicitud.getDatos()).get(ACTIVO));
	}
}
