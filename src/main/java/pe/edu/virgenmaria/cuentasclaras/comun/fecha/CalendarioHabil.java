package pe.edu.virgenmaria.cuentasclaras.comun.fecha;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Calendario hábil del colegio actual (sprint 5, tanda 3; hallazgo 6): lunes a viernes, sin los 16 feriados nacionales
 * ({@link FeriadosNacionales}) ni los días no laborables que registró el colegio ({@link Feriado} vigentes). Lo usan todas
 * las alertas de depósito, de abono y de verificación, las ventanas de la conciliación y los recordatorios.
 * <p>
 * Los feriados extra se guardan en caché por colegio durante {@link #VIGENCIA_CACHE}; registrar o anular uno la invalida al
 * terminar su transacción. Sin colegio en el contexto (o viendo todos), solo cuentan los nacionales.
 */
@Component
public class CalendarioHabil implements DiasHabiles {

	static final Duration VIGENCIA_CACHE = Duration.ofMinutes(10);

	private record Entrada(Set<LocalDate> fechas, long expiraNanos) {
	}

	private final FeriadoRepository feriados;

	private final Map<Long, Entrada> cache = new ConcurrentHashMap<>();

	public CalendarioHabil(FeriadoRepository feriados) {
		this.feriados = feriados;
	}

	@Override
	public boolean esFeriado(LocalDate fecha) {
		return FeriadosNacionales.es(fecha) || delColegio().contains(fecha);
	}

	/** Los días no laborables extra vigentes del colegio actual. */
	public Set<LocalDate> delColegio() {
		Long colegio = ContextoColegio.actual();
		if (colegio == null || colegio <= 0) {
			return Set.of();
		}
		long ahora = System.nanoTime();
		Entrada entrada = cache.get(colegio);
		if (entrada == null || ahora - entrada.expiraNanos() > 0) {
			Set<LocalDate> fechas = feriados.findByVigenteTrue().stream().map(Feriado::getFecha)
					.collect(Collectors.toUnmodifiableSet());
			entrada = new Entrada(fechas, ahora + VIGENCIA_CACHE.toNanos());
			cache.put(colegio, entrada);
		}
		return entrada.fechas();
	}

	/**
	 * Olvida la caché del colegio al terminar la transacción actual (confirmada o revertida), o en el acto si no hay
	 * transacción: el próximo cálculo vuelve a leer la base.
	 */
	public void invalidar(Long colegioId) {
		cache.remove(colegioId);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int estado) {
					cache.remove(colegioId);
				}
			});
		}
	}

	/** Olvida toda la caché (pruebas que limpian la base). */
	public void invalidarTodo() {
		cache.clear();
	}
}
