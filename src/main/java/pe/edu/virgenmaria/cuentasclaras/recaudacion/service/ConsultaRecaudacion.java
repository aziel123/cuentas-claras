package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.ArchivoCargado;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.ArchivoCargadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.config.PropiedadesRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LineaExcepcionResumen;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LineaVista;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LoteDetalle;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LoteResumen;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.RecaudacionLista;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLinea;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLote;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LineaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LoteRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LineaRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LoteRecaudacionRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lo que ve el personal de la recaudación: los lotes (los por confirmar primero), sus líneas y las que quedaron por
 * revisar. Mientras un lote está por confirmar NO muestra su total ni los montos de sus líneas, y su archivo original no
 * se descarga: quien confirma debe escribir lo que ve en el banco, no lo que dice el archivo.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ConsultaRecaudacion {

	private final LoteRecaudacionRepository lotes;

	private final LineaRecaudacionRepository lineas;

	private final ArchivoCargadoRepository archivos;

	private final PagoRepository pagos;

	private final SolicitudCambioRepository solicitudes;

	private final AuditoriaService auditoria;

	private final PropiedadesRecaudacion propiedades;

	public ConsultaRecaudacion(LoteRecaudacionRepository lotes, LineaRecaudacionRepository lineas,
			ArchivoCargadoRepository archivos, PagoRepository pagos, SolicitudCambioRepository solicitudes,
			AuditoriaService auditoria, PropiedadesRecaudacion propiedades) {
		this.lotes = lotes;
		this.lineas = lineas;
		this.archivos = archivos;
		this.pagos = pagos;
		this.solicitudes = solicitudes;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
	}

	public RecaudacionLista lista() {
		List<LoteResumen> recientes = lotes.findTop50ByOrderByIdDesc().stream().map(ConsultaRecaudacion::resumen).toList();
		List<LineaExcepcionResumen> excepciones = lineas.findByEstadoOrderByIdAsc(EstadoLinea.EXCEPCION).stream()
				.map(l -> new LineaExcepcionResumen(l.getId(), l.getLote().getId(), l.getNumero(), l.getFechaPago(),
						CodigoPago.legible(l.getCodigo()), l.getAlumno() == null ? null : l.getAlumno().nombreCompleto(),
						l.getMonto(), l.getMoneda(), l.getMotivoExcepcion().descripcion(), l.getMotivoExcepcion().critico(),
						pendiente(l.getId())))
				.toList();
		return new RecaudacionLista(recientes.stream().filter(l -> l.estado().equals(EstadoLote.CARGADO.name())).toList(),
				recientes.stream().filter(l -> !l.estado().equals(EstadoLote.CARGADO.name())).toList(), excepciones,
				propiedades.banco().etiqueta(), propiedades.modalidad() == pe.edu.virgenmaria.cuentasclaras.recaudacion
						.model.ModalidadRecaudacion.CON_BASE_DE_DEUDAS ? "Con base de deudas" : "Solo con el código del "
								+ "alumno");
	}

	public LoteDetalle detalle(Long loteId) {
		LoteRecaudacion lote = lotes.findById(loteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Lote no encontrado"));
		boolean visibles = montosVisibles(lote);
		List<LineaRecaudacion> todas = lineas.findByLoteIdOrderByNumeroAsc(loteId);
		Map<Long, Pago> pagoDeLinea = pagos.findByLineaRecaudacionIdIn(todas.stream().map(LineaRecaudacion::getId)
				.toList()).stream().collect(Collectors.toMap(Pago::getLineaRecaudacionId, Function.identity()));
		List<LineaVista> detalle = todas.stream().map(l -> new LineaVista(l.getId(), l.getNumero(), l.getFechaPago(),
				CodigoPago.legible(l.getCodigo()), l.getAlumno() == null ? null : l.getAlumno().nombreCompleto(),
				visibles ? l.getMonto() : null, l.getMoneda(), l.getNumeroOperacion(), l.getEstado().name(),
				l.getEstado().etiqueta(), l.getEstado().variante(),
				l.getMotivoExcepcion() == null ? null : l.getMotivoExcepcion().descripcion(), l.getDetalle(),
				l.getMotivoExcepcion() != null && l.getMotivoExcepcion().critico(),
				pagoDeLinea.containsKey(l.getId()) ? pagoDeLinea.get(l.getId()).getComprobante().numeroCompleto() : null))
				.toList();
		ArchivoCargado archivo = archivos.findById(lote.getArchivoId()).orElseThrow();
		String usuario = SecurityContextHolder.getContext().getAuthentication().getName();
		return new LoteDetalle(lote.getId(), lote.getVersion(), lote.getBanco().etiqueta(), lote.getFormato(),
				archivo.getNombre(), lote.getArchivoSha256(), lote.getFechaProceso(), lote.getDesde(), lote.getHasta(),
				lote.getLineas(), visibles ? lote.getTotal() : null, lote.getEstado().name(), lote.getEstado().etiqueta(),
				lote.getEstado().variante(), lote.getCreadoPor(), lote.getCreadoEn(), lote.getConfirmadoPor(),
				lote.getConfirmadoEn(), lote.getIntentosConfirmacion(), lote.getRechazadoPor(), lote.getMotivoRechazo(),
				lote.getLineasAplicadas(), lote.getLineasExcepcion(), lote.getMontoAplicado(), lote.getMontoExcepcion(),
				visibles, archivoDescargable(lote), lote.getEstado() == EstadoLote.CARGADO
						&& usuario.equals(lote.getCreadoPor()), detalle);
	}

	/**
	 * El archivo original del banco (evidencia). Lleva datos de familias: su descarga se audita. No se descarga mientras
	 * el lote está por confirmar (quien confirma no debe ver su total).
	 */
	@Transactional
	public ArchivoOriginal archivo(Long loteId) {
		LoteRecaudacion lote = lotes.findById(loteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Lote no encontrado"));
		if (!archivoDescargable(lote)) {
			throw new ReglaNegocioException("El archivo original no se descarga mientras haya un lote de esos días por "
					+ "confirmar: quien confirma debe mirar el banco, no el archivo.");
		}
		ArchivoCargado archivo = archivos.findById(lote.getArchivoId()).orElseThrow();
		auditoria.registrar(AccionAuditoria.ARCHIVO_BANCO_DESCARGADO, "archivo_cargado", archivo.getId().toString(), null,
				archivo.getNombre(), "Descargó el archivo original de la recaudación del lote " + loteId + " (SHA-256 "
						+ archivo.getSha256() + "). Lleva datos de familias (Ley 29733).");
		return new ArchivoOriginal(nombreDescarga(loteId, archivo.getNombre()), archivo.contenido(), archivo.getSha256());
	}

	/**
	 * Sprint 7, tanda 3 (A03 de OWASP): el nombre con el que se descarga es FIJO ({@code recaudacion-lote-<id>.<ext>}), nunca
	 * el que escribió quien subió el archivo (podría traer «../», comillas o un nombre engañoso). El original queda en la
	 * base y en la bitácora; la extensión solo puede ser csv, txt o xlsx.
	 */
	static String nombreDescarga(Long loteId, String original) {
		String minusculas = original == null ? "" : original.strip().toLowerCase(java.util.Locale.ROOT);
		String extension = minusculas.endsWith(".xlsx") ? "xlsx" : minusculas.endsWith(".txt") ? "txt" : "csv";
		return "recaudacion-lote-" + loteId + "." + extension;
	}

	/** El archivo original para descargar. */
	public record ArchivoOriginal(String nombre, byte[] contenido, String sha256) {
	}

	/** Los montos se ven solo cuando otra persona ya confirmó el total a ciegas (CONFIRMADO o APLICADO). */
	private static boolean montosVisibles(LoteRecaudacion lote) {
		return lote.getEstado() == EstadoLote.CONFIRMADO || lote.getEstado() == EstadoLote.APLICADO;
	}

	/**
	 * El archivo se descarga salvo que esté por confirmar, o que OTRO lote de esos mismos días esté por confirmar (S4-A1:
	 * el archivo de un lote descartado o rechazado, con un byte distinto y otro SHA-256, traía el total del que espera la
	 * confirmación a ciegas). Se compara por fechas, no por SHA-256.
	 */
	private boolean archivoDescargable(LoteRecaudacion lote) {
		return lote.getEstado() != EstadoLote.CARGADO && !lotes.existsByEstadoAndDesdeLessThanEqualAndHastaGreaterThanEqual(
				EstadoLote.CARGADO, lote.getHasta(), lote.getDesde());
	}

	private String pendiente(Long lineaId) {
		return solicitudes.findByEntidadAndEntidadIdAndEstadoOrderByIdAsc(ServicioExcepcionesRecaudacion.ENTIDAD, lineaId,
				EstadoSolicitud.PENDIENTE).stream().map(SolicitudCambio::getTipo).map(t -> t.etiqueta())
				.findFirst().orElse(null);
	}

	private static LoteResumen resumen(LoteRecaudacion l) {
		return new LoteResumen(l.getId(), l.getCreadoEn(), l.getCreadoPor(), l.getBanco().etiqueta(), l.getDesde(),
				l.getHasta(), l.getLineas(), montosVisibles(l) ? l.getTotal() : null, l.getEstado().name(),
				l.getEstado().etiqueta(), l.getEstado().variante(), l.getLineasAplicadas(), l.getLineasExcepcion());
	}
}
