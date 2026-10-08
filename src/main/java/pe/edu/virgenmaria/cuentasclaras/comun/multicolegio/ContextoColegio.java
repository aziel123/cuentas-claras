package pe.edu.virgenmaria.cuentasclaras.comun.multicolegio;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Colegio con el que trabaja el hilo actual. Lo usa {@link ResolutorColegioActual} para que
 * Hibernate filtre y asigne {@code colegio_id} en toda entidad con {@code @TenantId}.
 * <p>
 * Orden de resolución de {@link #actual()}:
 * <ol>
 *   <li>el colegio fijado en este hilo con {@link #en} o {@link #comoSistema};</li>
 *   <li>el colegio del usuario autenticado ({@link PrincipalConColegio});</li>
 *   <li>{@link #NINGUNO}: no se ve ningún dato y no se puede insertar (falla cerrado).</li>
 * </ol>
 * Hibernate fija el colegio al abrir la sesión, por eso cambiarlo con una transacción abierta
 * no tendría efecto y aquí lanza {@link IllegalStateException}.
 */
public final class ContextoColegio {

	/** Modo "root": ve todos los colegios. Solo para clases autorizadas (regla ArchUnit). */
	public static final Long SISTEMA = -1L;

	/** Sin colegio: no existe en la tabla {@code colegio}, así que no ve ni inserta nada. */
	public static final Long NINGUNO = 0L;

	private static final ThreadLocal<Long> COLEGIO = new ThreadLocal<>();

	private ContextoColegio() {
	}

	public static Long actual() {
		Long fijado = COLEGIO.get();
		if (fijado != null) {
			return fijado;
		}
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.isAuthenticated()
				&& autenticacion.getPrincipal() instanceof PrincipalConColegio principal
				&& principal.colegioId() != null) {
			return principal.colegioId();
		}
		return NINGUNO;
	}

	/** Ejecuta la operación con el colegio indicado (procesos sin usuario: login, arranque, tareas). */
	public static <T> T en(long colegioId, Supplier<T> operacion) {
		if (colegioId <= 0) {
			throw new IllegalArgumentException("El colegio debe ser un id válido; para ver todos usa comoSistema");
		}
		return ejecutar(colegioId, operacion);
	}

	public static void en(long colegioId, Runnable operacion) {
		Objects.requireNonNull(operacion, "operacion");
		en(colegioId, () -> {
			operacion.run();
			return null;
		});
	}

	/** Ejecuta la operación viendo todos los colegios. Solo para clases autorizadas. */
	public static <T> T comoSistema(Supplier<T> operacion) {
		return ejecutar(SISTEMA, operacion);
	}

	private static <T> T ejecutar(Long colegioId, Supplier<T> operacion) {
		Objects.requireNonNull(operacion, "operacion");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(
					"No se puede cambiar de colegio con una transacción abierta: Hibernate ya fijó el colegio de la sesión");
		}
		Long anterior = COLEGIO.get();
		COLEGIO.set(colegioId);
		try {
			return operacion.get();
		}
		finally {
			if (anterior == null) {
				COLEGIO.remove();
			}
			else {
				COLEGIO.set(anterior);
			}
		}
	}
}
