package pe.edu.virgenmaria.cuentasclaras.aprobaciones.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Sprint 11, tanda 1b (F-D5): puerto que implementa CADA módulo dueño de un flujo que se aprueba fuera de la bandeja
 * (cobranza, recaudación, conciliación, colegio, puesta en marcha y operación). {@code aprobaciones} no depende de ellos:
 * recibe la lista de beans. Cada implementación filtra por el colegio de la sesión (como sus pantallas) y devuelve solo lo
 * que el rol puede abrir ({@link ModuloApp#puedeEntrar}): «Revisar» nunca termina en 403.
 * <p>
 * El cierre de caja no tiene puerto: ya es una solicitud de la bandeja ({@code CIERRE_CAJA}).
 */
public interface PendientesExternos {

	DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

	/** Lo pendiente que ve quien está en sesión (incluido lo que pidió, con {@code puedeResolver = false}). */
	List<PendienteExterno> pendientes(String usuario);

	/** Lo que esa persona puede resolver (el contador del menú). */
	default long contarPorResolver(String usuario) {
		return pendientes(usuario).stream().filter(PendienteExterno::puedeResolver).count();
	}

	/**
	 * Sprint 11, tanda 2 (F-D5): cuántos pendientes hay en el colegio en contexto, sin filtro de rol ni de participantes.
	 * Solo lo llama el resumen diario ({@code sistema.panel}).
	 */
	long contarPendientesDelColegio();

	/** Los roles de quien está en sesión. */
	static Set<Rol> rolesActuales() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? Set.of() : ModuloApp.rolesDe(autenticacion.getAuthorities());
	}

	/** Un pendiente con «Revisar» solo si el rol puede abrir la pantalla; si no, no se muestra. */
	static boolean puedeAbrir(String enlace) {
		return ModuloApp.puedeEntrar(rolesActuales(), enlace);
	}

	/** «Enviado por adm.lopez el 24/10». */
	static String detalle(String verbo, String quien, LocalDateTime cuando) {
		return verbo + " por " + quien + (cuando == null ? "" : " el " + cuando.format(DIA));
	}
}
