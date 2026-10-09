package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Ejecuta una tarea como un actor de sistema en un colegio: fija el colegio ({@link ContextoColegio#en}) y un
 * {@code SecurityContext} nuevo con ese actor, y al terminar restaura el anterior (aunque la tarea falle).
 * <p>
 * Solo lo usan las clases de los paquetes {@code ..proceso..} y la semilla del muestreo (regla ArchUnit): son las únicas
 * que registran lo que entra solo.
 * <p>
 * Sprint 7, tanda 2 (hallazgo H3): con el actor en el contexto, la conexión que se pide es la de {@code cc_sistema}
 * ({@code RutaConexion.SISTEMA}). Por eso <b>nunca se une a la transacción de una persona</b>: si hay una transacción
 * activa en el hilo (por ejemplo, el envío síncrono al OSE justo después del commit de un cobro, o la semilla pedida desde
 * una pantalla), la tarea corre en una transacción PROPIA ({@code REQUIRES_NEW}) del MISMO colegio, con la conexión de
 * {@code cc_sistema}. Cambiar de colegio con una transacción abierta sigue sin permitirse.
 */
public final class EjecucionComoSistema {

	private static volatile PlatformTransactionManager transacciones;

	private EjecucionComoSistema() {
	}

	/** Lo registra {@code TransaccionesDeSistema} al arrancar. */
	public static void usarTransacciones(PlatformTransactionManager manejador) {
		transacciones = manejador;
	}

	public static <T> T como(ActorSistema actor, long colegioId, Supplier<T> tarea) {
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(tarea, "tarea");
		boolean conTransaccion = TransactionSynchronizationManager.isActualTransactionActive();
		if (conTransaccion && !Long.valueOf(colegioId).equals(ContextoColegio.actual())) {
			throw new IllegalStateException(
					"No se puede cambiar de colegio con una transacción abierta: Hibernate ya fijó el colegio de la sesión");
		}
		PrincipalSistema principal = new PrincipalSistema(actor, colegioId);
		SecurityContext anterior = SecurityContextHolder.getContext();
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(new PreAuthenticatedAuthenticationToken(principal, null,
				List.of(new SimpleGrantedAuthority(actor.autoridad()))));
		SecurityContextHolder.setContext(contexto);
		try {
			if (conTransaccion) {
				return enTransaccionPropia(tarea);
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

	/**
	 * H3: la transacción de quien llamó queda suspendida; la del actor se confirma (o revierte) sola. Se abre con el
	 * manejador JPA de la fábrica de {@code EntityManager} de la transacción en curso (así, con varios contextos de Spring
	 * en una misma JVM de pruebas, cada uno usa el suyo) o, si no hay, con el registrado al arrancar.
	 */
	private static <T> T enTransaccionPropia(Supplier<T> tarea) {
		PlatformTransactionManager manejador = TransactionSynchronizationManager.getResourceMap().keySet().stream()
				.filter(EntityManagerFactory.class::isInstance).map(EntityManagerFactory.class::cast).findFirst()
				.<PlatformTransactionManager>map(JpaTransactionManager::new).orElse(transacciones);
		if (manejador == null) {
			throw new IllegalStateException("Un actor de sistema no se une a una transacción abierta y no hay manejador de "
					+ "transacciones para abrir una propia");
		}
		TransactionTemplate propia = new TransactionTemplate(manejador);
		propia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return propia.execute(estado -> tarea.get());
	}
}
