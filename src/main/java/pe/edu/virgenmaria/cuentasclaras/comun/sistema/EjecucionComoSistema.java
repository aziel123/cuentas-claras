package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Ejecuta una tarea como un actor de sistema en un colegio: fija el colegio ({@link ContextoColegio#en}) y un
 * {@code SecurityContext} nuevo con ese actor, y al terminar restaura el anterior (aunque la tarea falle).
 * <p>
 * Solo lo usan las clases de los paquetes {@code ..proceso..} (regla ArchUnit): son las únicas que registran lo que
 * entra solo. Igual que {@link ContextoColegio#en}, no cambia de colegio con una transacción abierta; la única
 * excepción es seguir en el MISMO colegio (por ejemplo, el envío al OSE síncrono justo después del commit de un cobro).
 */
public final class EjecucionComoSistema {

	private EjecucionComoSistema() {
	}

	public static <T> T como(ActorSistema actor, long colegioId, Supplier<T> tarea) {
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(tarea, "tarea");
		PrincipalSistema principal = new PrincipalSistema(actor, colegioId);
		SecurityContext anterior = SecurityContextHolder.getContext();
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(new PreAuthenticatedAuthenticationToken(principal, null,
				List.of(new SimpleGrantedAuthority(actor.autoridad()))));
		SecurityContextHolder.setContext(contexto);
		try {
			if (TransactionSynchronizationManager.isActualTransactionActive()
					&& Long.valueOf(colegioId).equals(ContextoColegio.actual())) {
				return tarea.get();
			}
			return ContextoColegio.en(colegioId, tarea);
		}
		finally {
			SecurityContextHolder.setContext(anterior);
		}
	}

	public static void como(ActorSistema actor, long colegioId, Runnable tarea) {
		Objects.requireNonNull(tarea, "tarea");
		como(actor, colegioId, () -> {
			tarea.run();
			return null;
		});
	}
}
