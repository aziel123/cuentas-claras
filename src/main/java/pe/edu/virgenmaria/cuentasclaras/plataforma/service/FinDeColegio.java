package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf.DocumentoTextoPdf;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.ActaSupresion;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.CambioEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ActaSupresionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.CambioEstadoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoOperadores;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.EstadoExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.ExportacionColegio;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.MotivoExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.repository.ExportacionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.FinColegioVista;
import pe.edu.virgenmaria.cuentasclaras.plataforma.proceso.TareasFinDeColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sprint 11, tanda 2 (3.9 a 3.11; AP2-2, AP2-8 y AP2-10): lo que hacen los operadores al final de la vida de un colegio,
 * cada paso con la firma de su sesión y con aviso al colegio (correo institucional y de control) y a los operadores:
 * <ul>
 * <li>pedir por escrito una exportación ({@code exportacion_colegio:{colegio}:{n}}) o la de cierre de un SUSPENDIDO, y
 * volver a mandar el enlace de la entrega;</li>
 * <li>poner la retención legal (uno) y quitarla (OTRO);</li>
 * <li>proponer el acta de supresión (uno), aprobarla (OTRO) y pedir la verificación completa de la cadena antes de que el
 * DBA la ejecute con {@code suprimir.sql}.</li>
 * </ul>
 * La base vuelve a exigir cada regla (V41): aquí se rechaza antes, con un mensaje claro.
 */
@Service
@PreAuthorize("hasRole('OPERADOR_PLATAFORMA')")
public class FinDeColegio {

	private final ColegioRepository colegios;

	private final ExportacionColegioRepository exportaciones;

	private final ActaSupresionRepository actas;

	private final CambioEstadoColegioRepository cambios;

	private final TareasFinDeColegio tareas;

	private final EjecucionPlataforma plataforma;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final DocumentoTextoPdf pdf;

	private final TransactionTemplate transaccion;

	private final TransactionTemplate lectura;

	private final Clock reloj;

	public FinDeColegio(ColegioRepository colegios, ExportacionColegioRepository exportaciones,
			ActaSupresionRepository actas, CambioEstadoColegioRepository cambios, TareasFinDeColegio tareas,
			EjecucionPlataforma plataforma, FirmaSesion firma, AuditoriaService auditoria, ApplicationEventPublisher eventos,
			DocumentoTextoPdf pdf, PlatformTransactionManager transacciones, Clock reloj) {
		this.colegios = colegios;
		this.exportaciones = exportaciones;
		this.actas = actas;
		this.cambios = cambios;
		this.tareas = tareas;
		this.plataforma = plataforma;
		this.firma = firma;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.pdf = pdf;
		this.transaccion = new TransactionTemplate(transacciones);
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
		this.reloj = reloj;
	}

	/** La ficha del fin del colegio. */
	public FinColegioVista ver(long colegioId) {
		UsuarioAutenticado operador = operador();
		Colegio c = colegio(colegioId);
		List<ExportacionColegio> lista = ContextoColegio.en(colegioId, () -> lectura.execute(t -> exportaciones
				.findAllByOrderByIdDesc()));
		Optional<ActaSupresion> acta = acta(colegioId);
		boolean vigente = lista.stream().anyMatch(e -> e.getEstado() == EstadoExportacion.GENERANDO
				|| e.getEstado() == EstadoExportacion.LISTA);
		List<FinColegioVista.Exportacion> vistas = lista.stream().map(e -> new FinColegioVista.Exportacion(e.getId(),
				e.getMotivo().name(), e.getEstado().name(), e.getGeneradaEn(), e.getVenceEn(), e.getFilas(), e.getBytes(),
				e.getSha256Zip(), e.getSha256Manifiesto(), e.getDescargas(), e.getDetalleFallo(),
				e.getEstado() == EstadoExportacion.LISTA)).toList();
		FinColegioVista.Acta vistaActa = acta.map(a -> new FinColegioVista.Acta(a.getId(), a.getTipo().name(),
				a.getEstado().name(), a.getMotivo(), a.getPropuestoPor(), a.getAprobadoPor(), a.getInicioPlazo(),
				a.getRespaldosVencenEl(), a.getCadenaVerificadaEn(), a.getHuellaEventos(), a.getEjecutadoPor(),
				a.getEjecutadaEn(), a.getEventosTachados(),
				a.getEstado() == ActaSupresion.Estado.PROPUESTA && !Objects.equals(a.getPropuestoPor(), operador.getUsername())
						&& !c.conRetencionLegal(),
				a.getEstado() == ActaSupresion.Estado.APROBADA)).orElse(null);
		boolean finDeVida = c.getEstado() == EstadoColegio.CERRADO || c.getEstado() == EstadoColegio.BAJA;
		return new FinColegioVista(c.getId(), c.getNombre(), c.getEstado().name(), c.getConservarHasta(),
				c.conservacionAutorizada(), c.getRetencionLegalMotivo(), c.getRetencionLegalPor(),
				c.getRetencionLegalDesde(), c.conRetencionLegal() && !Objects.equals(c.getRetencionLegalPor(),
						operador.getUsername()), vistas,
				!vigente && EnumSet.of(EstadoColegio.PRODUCCION, EstadoColegio.SUSPENDIDO, EstadoColegio.CERRADO)
						.contains(c.getEstado()),
				!vigente && c.getEstado() == EstadoColegio.SUSPENDIDO, vistaActa,
				acta.isEmpty() && finDeVida && !c.conRetencionLegal());
	}

	/** Un pedido escrito del colegio: el operador la pide con su firma y la genera {@code sistema.exportacion}. */
	public Long pedirExportacion(long colegioId, String referencia) {
		UsuarioAutenticado operador = operador();
		Colegio colegio = colegio(colegioId);
		String ref = referencia == null ? "" : referencia.strip();
		if (ref.length() < 5 || ref.length() > 200) {
			throw new ReglaNegocioException("Escribe la referencia del pedido escrito del colegio (de 5 a 200 caracteres).");
		}
		if (!EnumSet.of(EstadoColegio.PRODUCCION, EstadoColegio.SUSPENDIDO, EstadoColegio.CERRADO)
				.contains(colegio.getEstado())) {
			throw new ReglaNegocioException("La exportación a pedido es de un colegio en producción, suspendido o cerrado.");
		}
		long numero = ContextoColegio.en(colegioId, () -> lectura.execute(t -> {
			exigirSinVigente();
			return exportaciones.count() + 1;
		}));
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.exportacionPedida(colegioId, numero)));
		Long id = ContextoColegio.en(colegioId, () -> plataforma.como(() -> {
			ExportacionColegio e;
			try {
				e = exportaciones.saveAndFlush(ExportacionColegio.nueva(operador.colegioId(), MotivoExportacion.PEDIDO,
						null));
			}
			catch (DataIntegrityViolationException ex) {
				throw new ReglaNegocioException("El colegio ya tiene una exportación en curso o lista: espera a que venza "
						+ "o se entregue.");
			}
			auditoria.registrar(AccionAuditoria.EXPORTACION_PEDIDA, "exportacion_colegio", e.getId().toString(), null,
					"PEDIDO", "El operador " + operador.getUsername() + " pidió la exportación de los datos del colegio "
							+ "por un pedido escrito (" + ref + "). La genera sistema.exportacion.");
			return e.getId();
		}));
		enLaPlataforma(AvisoPlataforma.Tipo.EXPORTACION_MOVIMIENTO, List.of("se pidió una exportación (" + colegio.getNombre()
				+ ")", ref), "exportacion_colegio", id, "PEDIDO", AccionAuditoria.EXPORTACION_PEDIDA,
				"Exportación a pedido del colegio " + colegio.getNombre() + " (" + ref + ").");
		tareas.generarPedido(colegioId, id);
		return id;
	}

	/** La exportación de cierre (3.9): de un colegio SUSPENDIDO, antes de proponer su cierre. */
	public Long exportarParaCierre(long colegioId) {
		UsuarioAutenticado operador = operador();
		Colegio colegio = colegio(colegioId);
		if (colegio.getEstado() != EstadoColegio.SUSPENDIDO) {
			throw new ReglaNegocioException("La exportación de cierre es de un colegio suspendido.");
		}
		ContextoColegio.en(colegioId, () -> lectura.executeWithoutResult(t -> exigirSinVigente()));
		Long id = tareas.exportarParaCierre(colegioId, colegio.getCambioEstadoId());
		enLaPlataforma(AvisoPlataforma.Tipo.EXPORTACION_MOVIMIENTO, List.of("se generó la exportación de cierre ("
				+ colegio.getNombre() + ")", "pedida por " + operador.getUsername()), "exportacion_colegio", id, "CIERRE",
				AccionAuditoria.EXPORTACION_PEDIDA, "Exportación de cierre del colegio " + colegio.getNombre()
						+ ", pedida por " + operador.getUsername() + ".");
		return id;
	}

	/** Vuelve a mandar el enlace de una exportación LISTA (el anterior deja de servir). */
	public void reenviarEntrega(long colegioId, long exportacionId) {
		UsuarioAutenticado operador = operador();
		Colegio colegio = colegio(colegioId);
		if (!tareas.reenviarEntrega(colegioId, exportacionId)) {
			throw new ReglaNegocioException("El titular no tiene correo registrado: el enlace va solo por correo. Corrige "
					+ "su contacto (dos operadores) y vuelve a mandarlo.");
		}
		enLaPlataforma(AvisoPlataforma.Tipo.EXPORTACION_MOVIMIENTO, List.of("se volvió a mandar el enlace ("
				+ colegio.getNombre() + ")", "por " + operador.getUsername()), "exportacion_colegio", exportacionId,
				"REENVIO:" + System.nanoTime(), AccionAuditoria.EXPORTACION_ENTREGA_INICIADA, "Nuevo enlace de la "
						+ "exportación " + exportacionId + " del colegio " + colegio.getNombre() + ".");
	}

	/** AP2-10: un operador pone la retención legal (pedido escrito de una autoridad o un litigio). */
	public void ponerRetencion(long colegioId, String motivo) {
		UsuarioAutenticado operador = operador();
		Colegio leido = colegio(colegioId);
		if (leido.getEstado() == EstadoColegio.SUPRIMIDO) {
			throw new ReglaNegocioException("El colegio ya fue suprimido.");
		}
		// Antes de firmar: el motivo y que no haya otra.
		leido.ponerRetencionLegal(motivo, operador.getUsername(), LocalDateTime.now(reloj));
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.retencionLegal(colegioId)));
		ContextoColegio.en(colegioId, () -> plataforma.ejecutar(() -> {
			Colegio c = colegios.findById(colegioId).orElseThrow();
			c.ponerRetencionLegal(motivo, operador.getUsername(), LocalDateTime.now(reloj));
			colegios.saveAndFlush(c);
			auditoria.registrar(AccionAuditoria.RETENCION_LEGAL_PUESTA, "colegio", c.getId().toString(), null,
					c.getRetencionLegalMotivo(), "El operador " + operador.getUsername() + " puso la retención legal: los "
							+ "datos del colegio no se suprimen mientras esté (DS 016-2024-JUS, arts. 84 y 85).");
			eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CONSERVACION,
					List.of("se puso una retención legal", "los datos no se suprimen mientras esté"), "colegio", colegioId,
					"RETENCION:" + System.nanoTime(), null, true, true, true));
		}));
		enLaPlataforma(AvisoPlataforma.Tipo.CONSERVACION, List.of("se puso una retención legal (" + leido.getNombre() + ")",
				"por " + operador.getUsername()), "colegio", colegioId, "RETENCION:" + System.nanoTime(),
				AccionAuditoria.RETENCION_LEGAL_PUESTA, "Retención legal del colegio " + leido.getNombre() + ".");
	}

	/** AP2-10: OTRO operador quita la retención legal. */
	public void quitarRetencion(long colegioId) {
		UsuarioAutenticado operador = operador();
		Colegio leido = colegio(colegioId);
		leido.quitarRetencionLegal(operador.getUsername());
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.retencionLegalRetirada(colegioId)));
		ContextoColegio.en(colegioId, () -> plataforma.ejecutar(() -> {
			Colegio c = colegios.findById(colegioId).orElseThrow();
			String antes = c.getRetencionLegalMotivo();
			c.quitarRetencionLegal(operador.getUsername());
			colegios.saveAndFlush(c);
			auditoria.registrar(AccionAuditoria.RETENCION_LEGAL_QUITADA, "colegio", c.getId().toString(), antes, null,
					"El operador " + operador.getUsername() + " quitó la retención legal que puso otro operador.");
			eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CONSERVACION,
					List.of("se quitó la retención legal", "vuelven a correr los plazos de supresión"), "colegio",
					colegioId, "RETENCION_QUITADA:" + System.nanoTime(), null, true, true, true));
		}));
		enLaPlataforma(AvisoPlataforma.Tipo.CONSERVACION, List.of("se quitó la retención legal (" + leido.getNombre() + ")",
				"por " + operador.getUsername()), "colegio", colegioId, "RETENCION_QUITADA:" + System.nanoTime(),
				AccionAuditoria.RETENCION_LEGAL_QUITADA, "Fin de la retención legal del colegio " + leido.getNombre() + ".");
	}

	/** 3.11: un operador propone el acta de supresión de un colegio CERRADO o en BAJA. */
	public Long proponerActa(long colegioId, String motivo, LocalDate respaldosVencenEl) {
		UsuarioAutenticado operador = operador();
		Colegio colegio = colegio(colegioId);
		ActaSupresion.Tipo tipo = switch (colegio.getEstado()) {
			case CERRADO -> ActaSupresion.Tipo.CIERRE;
			case BAJA -> ActaSupresion.Tipo.BAJA;
			default -> throw new ReglaNegocioException("El acta de supresión es de un colegio cerrado o dado de baja.");
		};
		if (colegio.conRetencionLegal()) {
			throw new ReglaNegocioException("El colegio tiene una retención legal: no se propone el acta mientras esté.");
		}
		if (acta(colegioId).isPresent()) {
			throw new ReglaNegocioException("El colegio ya tiene su acta de supresión.");
		}
		LocalDateTime inicio = ContextoColegio.en(colegioId, () -> lectura.execute(t -> cambios.findByTipoAndEstadoOrderByIdAsc(
				tipo == ActaSupresion.Tipo.CIERRE ? TipoCambioEstado.SUSPENSION : TipoCambioEstado.BAJA,
				EstadoCambioEstado.APLICADO).stream().map(CambioEstadoColegio::getAplicadoEn).filter(Objects::nonNull)
				.max(Comparator.naturalOrder())))
				.orElseThrow(() -> new ReglaNegocioException("No se encontró la suspensión o la baja aplicada del colegio."));
		// Antes de firmar: el motivo y el plazo de los respaldos.
		ActaSupresion.propuesta(tipo, motivo, operador.colegioId(), operador.usuarioId(), operador.getUsername(), inicio,
				respaldosVencenEl);
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.actaSupresion(colegioId)));
		Long id = ContextoColegio.en(colegioId, () -> plataforma.como(() -> {
			ActaSupresion a;
			try {
				a = actas.saveAndFlush(ActaSupresion.propuesta(tipo, motivo, operador.colegioId(), operador.usuarioId(),
						operador.getUsername(), inicio, respaldosVencenEl));
			}
			catch (DataIntegrityViolationException e) {
				throw new ReglaNegocioException("El colegio ya tiene su acta de supresión.");
			}
			auditoria.registrar(AccionAuditoria.ACTA_SUPRESION_PROPUESTA, "acta_supresion", a.getId().toString(), null,
					"PROPUESTA", "El operador " + operador.getUsername() + " propuso el acta de supresión ("
							+ a.getMotivo() + "). La aprueba otro operador; la ejecuta el DBA con el plazo vencido.");
			eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.ACTA_SUPRESION,
					List.of("se propuso", "acta " + a.getId() + "; respaldos con datos hasta el "
							+ Calendario.formatear(respaldosVencenEl)), "acta_supresion", a.getId(), "P", null, true, true,
					false));
			return a.getId();
		}));
		enLaPlataforma(AvisoPlataforma.Tipo.ACTA_SUPRESION, List.of("se propuso", "acta " + id + " de " + colegio.getNombre()),
				"acta_supresion", id, "P", AccionAuditoria.ACTA_SUPRESION_PROPUESTA, "Acta de supresión del colegio "
						+ colegio.getNombre() + ".");
		return id;
	}

	/** 3.11: OTRO operador aprueba el acta (con su firma). */
	public void aprobarActa(long colegioId, long actaId) {
		UsuarioAutenticado operador = operador();
		Colegio colegio = colegio(colegioId);
		ActaSupresion leida = acta(colegioId).filter(a -> a.getId().equals(actaId))
				.orElseThrow(() -> new RecursoNoEncontradoException("Acta no encontrada"));
		if (colegio.conRetencionLegal()) {
			throw new ReglaNegocioException("El colegio tiene una retención legal: el acta no se aprueba mientras esté.");
		}
		leida.aprobar(operador.getUsername(), operador.usuarioId(), LocalDateTime.now(reloj));
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.actaSupresionAprobada(actaId)));
		ContextoColegio.en(colegioId, () -> plataforma.ejecutar(() -> {
			ActaSupresion a = actas.bloquear(actaId).orElseThrow();
			a.aprobar(operador.getUsername(), operador.usuarioId(), LocalDateTime.now(reloj));
			actas.saveAndFlush(a);
			auditoria.registrar(AccionAuditoria.ACTA_SUPRESION_APROBADA, "acta_supresion", a.getId().toString(),
					"PROPUESTA", "APROBADA", "El operador " + operador.getUsername() + " aprobó el acta que propuso "
							+ a.getPropuestoPor() + ". La ejecuta el DBA con suprimir.sql cuando venza el plazo, con la "
							+ "cadena verificada hace 1 hora o menos.");
			eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.ACTA_SUPRESION,
					List.of("se aprobó", "acta " + a.getId() + ", por " + a.getPropuestoPor() + " y "
							+ operador.getUsername()), "acta_supresion", a.getId(), "A", null, true, true, false));
		}));
		enLaPlataforma(AvisoPlataforma.Tipo.ACTA_SUPRESION, List.of("se aprobó", "acta " + actaId + " de "
				+ colegio.getNombre()), "acta_supresion", actaId, "A", AccionAuditoria.ACTA_SUPRESION_APROBADA,
				"Aprobación del acta de supresión del colegio " + colegio.getNombre() + ".");
	}

	/** AP2-8: la verificación completa de la cadena, justo antes de que el DBA ejecute el acta (1 hora). */
	public void verificarCadena(long colegioId) {
		UsuarioAutenticado operador = operador();
		Colegio colegio = colegio(colegioId);
		ActaSupresion a = acta(colegioId).filter(x -> x.getEstado() == ActaSupresion.Estado.APROBADA)
				.orElseThrow(() -> new ReglaNegocioException("La cadena se verifica con el acta aprobada."));
		tareas.verificarCadena(colegioId, a.getId());
		plataforma.ejecutar(() -> auditoria.registrar(AccionAuditoria.ACTA_CADENA_VERIFICADA, "acta_supresion",
				a.getId().toString(), null, "INTEGRA", "El operador " + operador.getUsername() + " pidió la verificación "
						+ "completa de la cadena para el acta del colegio " + colegio.getNombre() + "."));
	}

	/** El acta para imprimir y firmar (3.11). */
	public byte[] actaPdf(long colegioId) {
		operador();
		Colegio c = colegio(colegioId);
		ActaSupresion a = acta(colegioId).orElseThrow(() -> new RecursoNoEncontradoException("Acta no encontrada"));
		List<String> p = new ArrayList<>();
		p.add("Colegio: " + c.getNombre() + (c.getRuc() == null ? "" : " (RUC " + c.getRuc() + ")") + ". Estado: "
				+ c.getEstado().name() + ".");
		p.add("Acta " + a.getId() + " (" + a.getTipo().name() + "), estado " + a.getEstado().name() + ". Motivo: "
				+ a.getMotivo());
		p.add("Propuesta por " + a.getPropuestoPor() + "; aprobada por " + (a.getAprobadoPor() == null ? "(pendiente)"
				: a.getAprobadoPor()) + ". Inicio del plazo: " + a.getInicioPlazo() + ".");
		p.add("Ejecutada por: " + (a.getEjecutadoPor() == null ? "(pendiente)" : a.getEjecutadoPor()) + " el "
				+ (a.getEjecutadaEn() == null ? "(pendiente)" : a.getEjecutadaEn()) + ". Filas suprimidas por tabla: "
				+ (a.getFilasSuprimidas() == null ? "(pendiente)" : a.getFilasSuprimidas()) + ". Eventos tachados de la "
				+ "bitácora: " + (a.getEventosTachados() == null ? "(pendiente)" : a.getEventosTachados()) + ".");
		p.add("Huella de los eventos del colegio (huella_eventos): " + (a.getHuellaEventos() == null ? "(pendiente)"
				: a.getHuellaEventos()) + ", hasta la secuencia " + (a.getUltimaSecuencia() == null ? "(pendiente)"
						: a.getUltimaSecuencia()) + ". Se calcula así: h = 64 ceros; por cada evento del colegio, en orden de "
				+ "secuencia, h = SHA-256(h + \"|\" + id + \"|\" + hash). Cualquiera puede recalcularla con la bitácora.");
		p.add("El último respaldo que contiene datos del colegio vence el " + Calendario.formatear(a.getRespaldosVencenEl())
				+ " (nunca más de 24 meses desde el inicio del plazo). Los respaldos no se reescriben: al restaurar uno, "
				+ "reaplicar.sh vuelve a suprimir con esta acta.");
		p.add("Firmas: ______________________ (operador que propuso)    ______________________ (operador que aprobó)");
		return pdf.generar("Acta de supresión de los datos del colegio", p);
	}

	private void exigirSinVigente() {
		if (exportaciones.findAllByOrderByIdDesc().stream().anyMatch(e -> e.getEstado() == EstadoExportacion.GENERANDO
				|| e.getEstado() == EstadoExportacion.LISTA)) {
			throw new ReglaNegocioException("El colegio ya tiene una exportación en curso o lista: vuelve a mandar su "
					+ "enlace o espera a que venza.");
		}
	}

	private Optional<ActaSupresion> acta(long colegioId) {
		return ContextoColegio.en(colegioId, () -> lectura.execute(t -> actas.findFirstByIdNotNull()));
	}

	private void enLaPlataforma(AvisoPlataforma.Tipo tipo, List<String> parametros, String entidad, Long entidadId,
			String sufijo, AccionAuditoria accion, String detalle) {
		UsuarioAutenticado operador = operador();
		plataforma.ejecutar(() -> {
			eventos.publishEvent(new AvisoOperadores(operador.colegioId(), tipo, parametros, entidad, entidadId, sufijo));
			auditoria.registrar(accion, entidad, entidadId.toString(), null, null, detalle);
		});
	}

	private Colegio colegio(long colegioId) {
		return lectura.execute(t -> colegios.findById(colegioId).filter(c -> !c.esPlataforma()))
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
	}

	private static UsuarioAutenticado operador() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario)) {
			throw new AccessDeniedException("Se requiere un operador en sesión");
		}
		return usuario;
	}
}
