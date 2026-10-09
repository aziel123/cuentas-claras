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
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.ReglasSegregacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aplica un CAMBIO_ROLES aprobado por otra persona (sprint 7, tanda 2; H1 y decisión 84): da o quita PROMOTOR o DIRECTOR.
 * Corre en la ruta de identidad ({@code cc_sistema}) dentro de la transacción de la bandeja, con la solicitud ya APROBADA
 * y firmada (trg_usuario_rol_alta y trg_usuario_rol_baja exigen las dos cosas). Ni quien lo pidió ni el titular lo
 * aprueban ({@link #involucrados}). Si los roles cambiaron desde que se pidió, no se aplica. Aplica solo la diferencia,
 * enlaza la solicitud en la cuenta ({@code roles_solicitud_id}), cierra las sesiones del titular y queda resaltado.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorCambioRoles implements ManejadorSolicitud {

	/** Datos de la solicitud: el conjunto final de roles y el de antes (por nombre, separados por comas). */
	static final String ROLES = "roles";

	static final String ROLES_ANTERIORES = "rolesAnteriores";

	private final UsuarioRepository usuarios;

	private final AuditoriaService auditoria;

	private final SesionesUsuario sesionesHttp;

	private final SesionesFirmadas sesiones;

	public ManejadorCambioRoles(UsuarioRepository usuarios, AuditoriaService auditoria, SesionesUsuario sesionesHttp,
			SesionesFirmadas sesiones) {
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.sesionesHttp = sesionesHttp;
		this.sesiones = sesiones;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.CAMBIO_ROLES;
	}

	/** El titular no aprueba sus propios roles (y la bandeja suma a quien pidió y a quienes prepararon su cuenta). */
	@Override
	public Set<String> involucrados(SolicitudCambio solicitud) {
		return usuarios.findById(solicitud.getEntidadId()).map(u -> Set.of(u.getNombreUsuario())).orElse(Set.of());
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		return List.of("Antes: " + texto(leer(datos.get(ROLES_ANTERIORES))), "Después: " + texto(leer(datos.get(ROLES))));
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "Promotoría y Dirección aprueban anulaciones, descuentos, cierres y cambios de contacto. Apruébalo solo si "
				+ "confirmaste en persona que corresponde. Sus sesiones abiertas se cerrarán.";
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Usuario usuario = usuarios.bloquearPorId(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("La persona de la solicitud no existe."));
		if (!usuario.isActivo() || usuario.getApoderadoId() != null) {
			throw new ReglaNegocioException("La cuenta está desactivada: ya no se cambian sus roles.");
		}
		Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
		Set<Rol> anteriores = leer(datos.get(ROLES_ANTERIORES));
		Set<Rol> nuevos = leer(datos.get(ROLES));
		if (!usuario.getRoles().equals(anteriores)) {
			throw new ReglaNegocioException("Los roles de " + usuario.getNombreCompleto() + " cambiaron desde que se pidió: "
					+ "rechaza esta solicitud y que se pida de nuevo.");
		}
		ReglasSegregacion.validarCuenta(nuevos, null);
		if (anteriores.contains(Rol.PROMOTOR) && !nuevos.contains(Rol.PROMOTOR)
				&& usuarios.bloquearActivosConRol(Rol.PROMOTOR).size() <= 1) {
			throw new ReglaNegocioException("No se le puede quitar Promotoría al último usuario de Promotoría activo.");
		}
		usuario.aplicarRolesAprobados(nuevos, solicitud.getId());
		usuarios.saveAndFlush(usuario);
		auditoria.registrar(AccionAuditoria.ROLES_CAMBIADOS, "usuario", usuario.getId().toString(), nombres(anteriores),
				nombres(nuevos), "Usuario " + usuario.getNombreUsuario() + ": roles cambiados con la solicitud "
						+ solicitud.getId() + ", pedida por " + solicitud.getSolicitadoPor() + " y aprobada por " + aprobador
						+ ". Motivo: " + solicitud.getMotivo());
		sesiones.cerrarDe(usuario.getId(), MotivoCierreSesion.CUENTA_CAMBIADA);
		sesionesHttp.expirar(usuario.getId());
	}

	/** Los datos de la solicitud para un cambio de roles. */
	static Map<String, String> datos(Set<Rol> anteriores, Set<Rol> nuevos) {
		return Map.of(ROLES_ANTERIORES, nombres(anteriores), ROLES, nombres(nuevos));
	}

	static String nombres(Set<Rol> roles) {
		return roles.stream().sorted().map(Rol::name).collect(Collectors.joining(","));
	}

	private static Set<Rol> leer(String nombres) {
		Set<Rol> roles = EnumSet.noneOf(Rol.class);
		if (nombres != null && !nombres.isBlank()) {
			Arrays.stream(nombres.split(",")).map(String::strip).map(Rol::valueOf).forEach(roles::add);
		}
		return roles;
	}

	private static String texto(Set<Rol> roles) {
		return roles.isEmpty() ? "sin roles" : roles.stream().sorted().map(Rol::etiqueta).collect(Collectors.joining(" · "));
	}
}
