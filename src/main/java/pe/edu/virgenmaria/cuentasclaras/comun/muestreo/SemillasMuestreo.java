package pe.edu.virgenmaria.cuentasclaras.comun.muestreo;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.MuestraAlAzar;

import java.time.LocalDate;

/**
 * La semilla secreta del muestreo por colegio, ámbito y fecha (sprint 5, tanda 3; G21). La primera consulta la crea con
 * {@code SecureRandom}; si dos la crean a la vez, la segunda choca con {@code uk_semilla_muestreo} y vuelve a leer la
 * ganadora. Así la muestra es estable y nadie puede calcularla desde la fecha.
 * <p>
 * Sprint 7, tanda 2 (H4):
 * <ul>
 *   <li>la crea y la lee SOLO {@code sistema.muestreo}, con la conexión de {@code cc_sistema} (con {@code cc_app}, 1142),
 *       en una transacción PROPIA aunque quien la pide esté en otra ({@link EjecucionComoSistema}); en MySQL
 *       {@code trg_semilla_muestreo_registro} solo admite la de hoy (CAJA) o la del lunes en curso (LLAMADA_CONTROL):
 *       nadie planta la de la semana que viene eligiéndola;</li>
 *   <li>devuelve la semilla EFECTIVA, derivada con HMAC y la clave del servidor ({@link DerivadorSecreto}): leer la fila
 *       guardada no basta para reproducir la muestra (E12).</li>
 * </ul>
 */
@Component
public class SemillasMuestreo {

	private final SemillaMuestreoRepository semillas;

	private final DerivadorSecreto derivador;

	private final TransactionTemplate transaccion;

	public SemillasMuestreo(SemillaMuestreoRepository semillas, DerivadorSecreto derivador,
			PlatformTransactionManager transacciones) {
		this.semillas = semillas;
		this.derivador = derivador;
		this.transaccion = new TransactionTemplate(transacciones);
	}

	/** La semilla efectiva del colegio actual para ese ámbito y fecha. */
	public long de(SemillaMuestreo.Ambito ambito, LocalDate fecha) {
		long colegio = ContextoColegio.actual();
		if (colegio <= 0) {
			throw new IllegalStateException("La semilla del muestreo es de un colegio");
		}
		Long guardada;
		try {
			guardada = EjecucionComoSistema.como(ActorSistema.MUESTREO, colegio, () -> transaccion.execute(t -> semillas
					.findByAmbitoAndFecha(ambito, fecha)
					.orElseGet(() -> semillas.saveAndFlush(SemillaMuestreo.nueva(ambito, fecha, MuestraAlAzar.semilla())))
					.getSemilla()));
		}
		catch (DataIntegrityViolationException carrera) {
			// Otra transacción la creó al mismo tiempo: la nuestra ya se revirtió entera; se lee la ganadora.
			guardada = EjecucionComoSistema.como(ActorSistema.MUESTREO, colegio, () -> transaccion.execute(t -> semillas
					.findByAmbitoAndFecha(ambito, fecha).orElseThrow(() -> carrera).getSemilla()));
		}
		return derivador.semilla(ambito.name(), fecha, guardada);
	}
}
