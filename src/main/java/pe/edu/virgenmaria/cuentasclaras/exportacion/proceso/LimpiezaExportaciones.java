package pe.edu.virgenmaria.cuentasclaras.exportacion.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.Exportador;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.VencimientoExportaciones;

import java.util.List;

/**
 * Sprint 11, tanda 2 (3.9 y AP2-13): cada día a las 03:30, en cada colegio, {@code sistema.exportacion} borra el archivo
 * de las exportaciones con 30 días y las deja VENCIDA (con aviso al correo de control).
 */
@Component
public class LimpiezaExportaciones {

	private static final Logger LOG = LoggerFactory.getLogger(LimpiezaExportaciones.class);

	private final ColegioRepository colegios;

	private final VencimientoExportaciones vencimiento;

	private final Exportador exportador;

	private final TransactionTemplate lectura;

	public LimpiezaExportaciones(ColegioRepository colegios, VencimientoExportaciones vencimiento, Exportador exportador,
			PlatformTransactionManager transacciones) {
		this.colegios = colegios;
		this.vencimiento = vencimiento;
		this.exportador = exportador;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
	}

	@Scheduled(cron = "${cuentasclaras.exportacion.limpieza-cron:0 30 3 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		List<Long> ids = lectura.execute(t -> colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO).stream()
				.map(Colegio::getId).toList());
		for (Long colegio : ids) {
			limpiar(colegio);
		}
	}

	/** @return cuántas venció en ese colegio */
	public int limpiar(long colegioId) {
		try {
			return EjecucionComoSistema.como(ActorSistema.EXPORTACION, colegioId,
					() -> vencimiento.vencer(exportador.directorio()));
		}
		catch (RuntimeException e) {
			LOG.error("No se pudieron vencer las exportaciones del colegio {}: {}", colegioId,
					e.getClass().getSimpleName());
			return 0;
		}
	}
}
