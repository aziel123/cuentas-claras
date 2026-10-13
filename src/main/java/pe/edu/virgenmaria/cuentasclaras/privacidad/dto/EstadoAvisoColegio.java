package pe.edu.virgenmaria.cuentasclaras.privacidad.dto;

import java.time.LocalDateTime;

/**
 * Sprint 8, tanda 1 (sección 3.7): el estado del aviso de privacidad del colegio en sesión: la versión vigente del aviso
 * (configuración) y, si Dirección ya la aprobó, quién y cuándo.
 *
 * @param versionVigente  la versión del aviso que publica la plataforma ({@code cuentasclaras.privacidad.version-aviso})
 * @param versionAprobada la última versión que aprobó Dirección en este colegio ({@code null} si ninguna)
 * @param aprobadoPor     la cuenta que la aprobó
 * @param aprobadoEn      cuándo
 */
public record EstadoAvisoColegio(String versionVigente, String versionAprobada, String aprobadoPor,
		LocalDateTime aprobadoEn) {

	/** Dirección ya aprobó la versión vigente. */
	public boolean aprobado() {
		return versionVigente.equals(versionAprobada);
	}

	/** Hubo una aprobación, pero de una versión anterior a la vigente. */
	public boolean desactualizado() {
		return versionAprobada != null && !aprobado();
	}
}
