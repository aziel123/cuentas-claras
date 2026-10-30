package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.EstadoExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.ExportacionColegio;
import pe.edu.virgenmaria.cuentasclaras.exportacion.repository.ExportacionColegioRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Sprint 11, tanda 2 (3.9 y AP2-13): a los 30 días se borra el archivo de una exportación (la fila queda VENCIDA, sin clave
 * ni enlace, con sus huellas) y se avisa al correo de control. Lo corre {@code sistema.exportacion} en cada colegio.
 */
@Service
@PreAuthorize("hasRole('SISTEMA_EXPORTACION')")
@Transactional
public class VencimientoExportaciones {

	private static final Logger LOG = LoggerFactory.getLogger(VencimientoExportaciones.class);

	private final ExportacionColegioRepository exportaciones;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public VencimientoExportaciones(ExportacionColegioRepository exportaciones, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, Clock reloj) {
		this.exportaciones = exportaciones;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/** @return cuántas venció en el colegio en contexto */
	public int vencer(Path directorio) {
		List<ExportacionColegio> vencidas = exportaciones.findByEstadoInAndVenceEnLessThanEqual(
				EnumSet.of(EstadoExportacion.LISTA, EstadoExportacion.ENTREGADA), LocalDateTime.now(reloj));
		int n = 0;
		for (ExportacionColegio e : vencidas) {
			String antes = e.getEstado().name();
			try {
				if (e.getArchivo() != null) {
					Files.deleteIfExists(directorio.resolve(e.getArchivo()));
				}
			}
			catch (IOException ex) {
				LOG.warn("No se pudo borrar el archivo de la exportación {}: se reintenta mañana", e.getId());
				continue;
			}
			e.vencer();
			exportaciones.saveAndFlush(e);
			auditoria.registrar(AccionAuditoria.EXPORTACION_BORRADA, "exportacion_colegio", e.getId().toString(), antes,
					"VENCIDA", "A los 30 días se borró el archivo de la exportación; la fila queda, con sus huellas.");
			eventos.publishEvent(new AvisoPlataforma(ContextoColegio.actual(), AvisoPlataforma.Tipo.EXPORTACION_MOVIMIENTO,
					List.of("se borró el archivo a los 30 días", "sus huellas quedan"), "exportacion_colegio", e.getId(),
					"BORRADA", null, false, true, false));
			n++;
		}
		return n;
	}
}
