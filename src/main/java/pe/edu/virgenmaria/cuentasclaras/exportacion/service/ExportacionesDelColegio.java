package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.EstadoExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.ExportacionColegio;
import pe.edu.virgenmaria.cuentasclaras.exportacion.repository.ExportacionColegioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 11, tanda 2 (3.10 y AP2-2): qué exportación del colegio en contexto habilita su CIERRE o la BAJA de un colegio con
 * datos. Es la misma regla que trg_cambio_estado_colegio_nace y trg_colegio_cambio (la base la vuelve a revisar).
 */
@Component
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class ExportacionesDelColegio {

	private final ExportacionColegioRepository exportaciones;

	private final Clock reloj;

	public ExportacionesDelColegio(ExportacionColegioRepository exportaciones, Clock reloj) {
		this.exportaciones = exportaciones;
		this.reloj = reloj;
	}

	/** El cierre: generada después de la suspensión, no FALLIDA; ENTREGADA, o LISTA o VENCIDA con 30 días. */
	public Optional<ExportacionColegio> paraCierre(LocalDateTime suspendidoEn) {
		if (suspendidoEn == null) {
			return Optional.empty();
		}
		LocalDateTime hace30 = LocalDateTime.now(reloj).minusDays(ExportacionColegio.DIAS_VIGENCIA);
		return exportaciones.findAllByOrderByIdDesc().stream()
				.filter(e -> e.getGeneradaEn() != null && !e.getGeneradaEn().isBefore(suspendidoEn))
				.filter(e -> e.getEstado() == EstadoExportacion.ENTREGADA
						|| ((e.getEstado() == EstadoExportacion.LISTA || e.getEstado() == EstadoExportacion.VENCIDA)
								&& !e.getGeneradaEn().isAfter(hace30)))
				.findFirst();
	}

	/** La baja con datos (AP-6, AP2-2): ENTREGADA o VENCIDA, generada en los últimos 30 días. */
	public Optional<ExportacionColegio> paraBaja() {
		LocalDateTime hace30 = LocalDateTime.now(reloj).minusDays(ExportacionColegio.DIAS_VIGENCIA);
		return exportaciones.findAllByOrderByIdDesc().stream()
				.filter(e -> e.getEstado() == EstadoExportacion.ENTREGADA || e.getEstado() == EstadoExportacion.VENCIDA)
				.filter(e -> e.getGeneradaEn() != null && !e.getGeneradaEn().isBefore(hace30))
				.findFirst();
	}

	public List<ExportacionColegio> todas() {
		return exportaciones.findAllByOrderByIdDesc();
	}
}
