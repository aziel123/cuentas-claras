package pe.edu.virgenmaria.cuentasclaras.comun.muestreo;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.MuestraAlAzar;

import java.time.LocalDate;

/**
 * La semilla secreta del día por colegio y ámbito (sprint 5, tanda 3; G21). La primera consulta del día la crea con
 * {@code SecureRandom} en una transacción PROPIA (aunque quien la pide esté en una de solo lectura); si dos la crean a la
 * vez, la segunda choca con {@code uk_semilla_muestreo} y vuelve a leer la ganadora. Así la muestra es estable durante el
 * día y nadie puede calcularla desde la fecha.
 */
@Component
public class SemillasMuestreo {

	private final SemillaMuestreoRepository semillas;

	private final TransactionTemplate nueva;

	public SemillasMuestreo(SemillaMuestreoRepository semillas, PlatformTransactionManager transacciones) {
		this.semillas = semillas;
		this.nueva = new TransactionTemplate(transacciones);
		this.nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public long de(SemillaMuestreo.Ambito ambito, LocalDate fecha) {
		try {
			Long semilla = nueva.execute(t -> semillas.findByAmbitoAndFecha(ambito, fecha)
					.orElseGet(() -> semillas.saveAndFlush(SemillaMuestreo.nueva(ambito, fecha, MuestraAlAzar.semilla())))
					.getSemilla());
			return semilla;
		}
		catch (DataIntegrityViolationException carrera) {
			Long semilla = nueva.execute(t -> semillas.findByAmbitoAndFecha(ambito, fecha)
					.orElseThrow(() -> carrera).getSemilla());
			return semilla;
		}
	}
}
