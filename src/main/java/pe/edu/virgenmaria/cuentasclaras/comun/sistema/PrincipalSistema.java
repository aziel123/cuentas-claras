package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.PrincipalConColegio;

import java.security.Principal;
import java.util.Objects;

/**
 * Principal de un actor de sistema en un colegio: así {@code creado_por}, la bitácora y {@code @PreAuthorize}
 * funcionan igual que con una persona. No tiene usuario en la base ({@code usuarioId} es null).
 */
public record PrincipalSistema(ActorSistema actor, Long colegioId) implements PrincipalConColegio, Principal {

	public PrincipalSistema {
		Objects.requireNonNull(actor, "actor");
		if (colegioId == null || colegioId <= 0) {
			throw new IllegalArgumentException("Un actor de sistema siempre trabaja en un colegio");
		}
	}

	@Override
	public Long usuarioId() {
		return null;
	}

	@Override
	public String rolesParaAuditoria() {
		return actor.rol();
	}

	@Override
	public String getName() {
		return actor.usuario();
	}

	@Override
	public String toString() {
		return actor.usuario() + "@" + colegioId;
	}
}
