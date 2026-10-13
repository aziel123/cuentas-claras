package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.SerieComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Correcciones del sprint 8 (QA-S8-6, H9): dos sedes con el mismo RUC comparten la numeración ante SUNAT, así que no usan
 * la misma serie. Antes solo lo detectaba trg_serie_comprobante_nace (MySQL) al abrir la serie, en el primer cobro de la
 * cajera. Ahora la aprobación (y la prueba de conexión) de un OSE revisa las series que ya usan las OTRAS sedes con el
 * mismo RUC: las abiertas ({@code serie_comprobante}) y las de su OSE vigente. Solo devuelve los nombres de las series
 * (nada de otro colegio se muestra). Es una de las clases autorizadas a {@code ContextoColegio.comoSistema}: se llama sin
 * transacción abierta.
 */
@Component
public class SeriesDeOtrasSedes {

	private final ColegioRepository colegios;

	private final SerieComprobanteRepository series;

	private final ConectorColegioRepository conectores;

	private final TransactionTemplate lectura;

	public SeriesDeOtrasSedes(ColegioRepository colegios, SerieComprobanteRepository series,
			ConectorColegioRepository conectores, PlatformTransactionManager transacciones) {
		this.colegios = colegios;
		this.series = series;
		this.conectores = conectores;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	/** Lanza {@link ReglaNegocioException} si alguna de esas series ya la usa otra sede con ese RUC. */
	public void exigirLibres(Long colegioId, String ruc, Collection<String> propias) {
		List<String> ocupadas = ocupadas(colegioId, ruc, propias);
		if (!ocupadas.isEmpty()) {
			throw new ReglaNegocioException("Las series " + ocupadas + " ya las usa otra sede del colegio con el mismo RUC "
					+ "(SUNAT numera por RUC): configura series propias de esta sede (por ejemplo B003, F003, BC03 y FC03).");
		}
	}

	/** Las series de {@code propias} que ya usa otra sede con ese RUC (abiertas o en su OSE vigente). */
	public List<String> ocupadas(Long colegioId, String ruc, Collection<String> propias) {
		if (ruc == null || ruc.isBlank() || propias == null || propias.isEmpty()) {
			return List.of();
		}
		List<Long> otras = lectura.execute(t -> colegios.findByRucAndIdNot(ruc, colegioId).stream().map(Colegio::getId)
				.toList());
		if (otras == null || otras.isEmpty()) {
			return List.of();
		}
		return ContextoColegio.comoSistema(() -> lectura.execute(t -> {
			TreeSet<String> usadas = new TreeSet<>(series.seriesEn(propias, otras));
			for (ConectorColegio vigente : conectores.findByTipoAndEstadoAndColegioIdIn(TipoConector.OSE,
					EstadoConector.VIGENTE, otras)) {
				ConfiguracionConector.Ose suya = (ConfiguracionConector.Ose) ConfiguracionConector.deJson(TipoConector.OSE,
						vigente.getConfiguracion());
				suya.series().stream().filter(propias::contains).forEach(usadas::add);
			}
			return List.copyOf(usadas);
		}));
	}
}
