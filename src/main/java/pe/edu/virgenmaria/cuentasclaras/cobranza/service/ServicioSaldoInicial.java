package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaVista;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteDetalle;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteResumen;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoLote;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.LineaSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.LoteSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.LineaSaldoInicialRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.LoteSaldoInicialRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Saldo inicial (D7) con doble control y total de control:
 * <ul>
 *   <li>Administración crea el lote (referencia al informe del contador y total declarado), agrega y quita líneas
 *       (nunca se borran) y lo envía solo si la suma cuadra exactamente;</li>
 *   <li>Promotoría o Dirección lo confirman o lo devuelven con motivo. No puede confirmar quien lo creó, lo envió o
 *       le agregó líneas: el intento queda auditado ({@code noRollbackFor}). Al confirmar se crean las cuotas
 *       SALDO_INICIAL, todo o nada.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioSaldoInicial {

	private final LoteSaldoInicialRepository lotes;

	private final LineaSaldoInicialRepository lineas;

	private final CuotaRepository cuotas;

	private final AnioEscolarRepository anios;

	private final AlumnoRepository alumnos;

	private final MatriculaRepository matriculas;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ServicioSaldoInicial(LoteSaldoInicialRepository lotes, LineaSaldoInicialRepository lineas,
			CuotaRepository cuotas, AnioEscolarRepository anios,
			AlumnoRepository alumnos, MatriculaRepository matriculas, AuditoriaService auditoria, Clock reloj) {
		this.lotes = lotes;
		this.lineas = lineas;
		this.cuotas = cuotas;
		this.anios = anios;
		this.alumnos = alumnos;
		this.matriculas = matriculas;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	public List<LoteResumen> listar() {
		return lotes.findAllByOrderByCreadoEnDescIdDesc().stream()
				.map(l -> new LoteResumen(l.getId(), l.getAnioEscolar().getAnio(), l.getFechaCorte(),
						l.getDocumentoReferencia(), l.getTotalDeclarado(), l.totalLineas(), l.cuadra(),
						l.lineasVigentes().size(), l.getEstado().name(), l.getEstado().etiqueta(),
						l.getEstado().variante(), l.getCreadoPor(), l.getCreadoEn()))
				.toList();
	}

	/** Solo Administración arma lotes: decide si se muestra el formulario. */
	public boolean puedeArmarLotes() {
		return SesionActual.tieneAlgunRol("ADMINISTRACION");
	}

	/** Años para el formulario del lote (los no cerrados). */
	public List<AnioOpcion> aniosAbiertos() {
		return anios.findAllByOrderByAnioDesc().stream().filter(a -> !a.cerrado()).map(ServicioPlanesPension::opcion)
				.toList();
	}

	public LoteDetalle obtener(Long id) {
		LoteSaldoInicial lote = buscar(id);
		String usuario = SesionActual.usuario();
		boolean administracion = SesionActual.tieneAlgunRol("ADMINISTRACION");
		boolean confirmador = SesionActual.tieneAlgunRol("PROMOTOR", "DIRECTOR");
		boolean participo = lote.participantes().contains(usuario);
		boolean borrador = lote.getEstado() == EstadoLote.BORRADOR;
		boolean enviado = lote.getEstado() == EstadoLote.ENVIADO;
		Map<Long, Long> cuotaPorLinea = lote.getLineas().isEmpty() ? Map.of()
				: cuotas.findByLineaSaldoInicialIdIn(lote.getLineas().stream().map(LineaSaldoInicial::getId).toList())
						.stream().collect(Collectors.toMap(Cuota::getLineaSaldoInicialId, Cuota::getId));
		List<LineaVista> vistas = lote.getLineas().stream()
				.map(l -> new LineaVista(l.getId(), l.getAlumno().getId(), l.getAlumno().nombreCompleto(),
						l.getAlumno().getDocumento().texto(), l.getConcepto().etiqueta(), l.getDescripcion(),
						l.getMonto(), l.getFechaVencimiento(), l.isQuitada(), l.getCreadoPor(),
						cuotaPorLinea.get(l.getId())))
				.toList();
		String aviso = enviado && confirmador && participo
				? "Tú participaste en este lote (lo creaste, lo enviaste o le agregaste líneas): debe confirmarlo otra "
						+ "persona de Promotoría o Dirección." : null;
		BigDecimal suma = lote.totalLineas();
		return new LoteDetalle(lote.getId(), lote.getAnioEscolar().getId(), lote.getAnioEscolar().getAnio(),
				lote.getFechaCorte(), lote.getDocumentoReferencia(), lote.getTotalDeclarado(), suma,
				lote.getTotalDeclarado().subtract(suma), lote.cuadra(), lote.getEstado().name(),
				lote.getEstado().etiqueta(), lote.getEstado().variante(), lote.getCreadoPor(), lote.getCreadoEn(),
				lote.getEnviadoPor(), lote.getEnviadoEn(), lote.getConfirmadoPor(), lote.getConfirmadoEn(),
				lote.getDevueltoPor(), lote.getMotivoDevolucion(), lote.getDescartadoPor(), lote.getMotivoDescarte(),
				vistas, borrador && administracion, borrador && administracion && lote.cuadra()
						&& !lote.lineasVigentes().isEmpty(),
				enviado && confirmador && !participo, enviado && confirmador, borrador && administracion, aviso);
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public Long crearLote(LoteRequest solicitud) {
		AnioEscolar anio = anios.findById(solicitud.anioId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado"));
		if (anio.cerrado()) {
			throw new ReglaNegocioException("El año " + anio.getAnio() + " está cerrado.");
		}
		LoteSaldoInicial lote = lotes.save(LoteSaldoInicial.nuevo(anio, solicitud.fechaCorte(),
				solicitud.documentoReferencia(), solicitud.totalDeclarado(), LocalDate.now(reloj)));
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_LOTE_CREADO, "lote_saldo_inicial", lote.getId().toString(),
				null, cabecera(lote), "Lote " + lote.getId() + " del año " + anio.getAnio() + " (en preparación).");
		return lote.getId();
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public Long agregarLinea(Long loteId, LineaSaldoRequest solicitud) {
		LoteSaldoInicial lote = bloquear(loteId);
		Alumno alumno = alumnoPorDocumento(solicitud.documentoAlumno());
		LineaSaldoInicial linea = lote.agregarLinea(alumno, solicitud.concepto(), solicitud.mes(),
				solicitud.descripcion(), solicitud.monto(), solicitud.vencimiento());
		String obligacion = linea.obligacion();
		if (obligacion != null && !cuotas.findByAlumnoIdAndObligacionIn(alumno.getId(), List.of(obligacion)).isEmpty()) {
			throw new ReglaNegocioException(alumno.nombreCompleto() + " ya tiene la cuota «" + linea.getDescripcion()
					+ "» en su cronograma: no la cargues también como saldo inicial.");
		}
		lineas.saveAndFlush(linea);
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_LINEA_AGREGADA, "lote_saldo_inicial", loteId.toString(), null,
				linea(linea), "Línea " + linea.getId() + " del lote " + loteId + ". Suma de líneas: "
						+ Dinero.formatear(lote.totalLineas()) + " de " + Dinero.formatear(lote.getTotalDeclarado())
						+ " declarados.");
		return linea.getId();
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void quitarLinea(Long loteId, Long lineaId, String motivo) {
		String texto = Motivo.exigir(motivo);
		LoteSaldoInicial lote = bloquear(loteId);
		LineaSaldoInicial linea = lote.quitarLinea(lineaId);
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_LINEA_QUITADA, "lote_saldo_inicial", loteId.toString(),
				linea(linea), null, "Línea " + lineaId + " quitada (no se borra). Motivo: " + texto);
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void enviar(Long loteId) {
		LoteSaldoInicial lote = bloquear(loteId);
		lote.enviar(SesionActual.usuario(), ahora());
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_ENVIADO, "lote_saldo_inicial", loteId.toString(), null,
				cabecera(lote) + " · " + lote.lineasVigentes().size() + " líneas que suman "
						+ Dinero.formatear(lote.totalLineas()),
				"Cuadra con el total declarado. Falta la confirmación de Promotoría o Dirección.");
	}

	/**
	 * Confirma el lote y crea las cuotas SALDO_INICIAL, todo o nada: si alguna deuda ya existe para el alumno (por
	 * ejemplo, la pensión ya se generó), no se confirma nada.
	 */
	@Transactional(noRollbackFor = AutoaprobacionException.class)
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public int confirmar(Long loteId) {
		LoteSaldoInicial lote = bloquear(loteId);
		String usuario = SesionActual.usuario();
		if (lote.getEstado() != EstadoLote.ENVIADO) {
			throw new ReglaNegocioException("Solo se confirma un lote enviado (este está "
					+ lote.getEstado().etiqueta().toLowerCase() + ").");
		}
		if (lote.participantes().contains(usuario)) {
			auditoria.registrar(AccionAuditoria.AUTOAPROBACION_RECHAZADA, "lote_saldo_inicial", loteId.toString(), null,
					cabecera(lote), "Intentó confirmar el lote " + loteId
							+ ", en el que participó (lo creó, lo envió o le agregó líneas). Se rechazó.");
			throw new AutoaprobacionException("No puedes confirmar un lote que tú creaste, enviaste o al que le "
					+ "agregaste líneas: debe confirmarlo otra persona de Promotoría o Dirección.");
		}
		List<LineaSaldoInicial> vigentes = lote.lineasVigentes();
		List<String> conflictos = new ArrayList<>();
		for (LineaSaldoInicial linea : vigentes) {
			String obligacion = linea.obligacion();
			if (cuotas.existsByClave(linea.clave()) || obligacion != null && !cuotas
					.findByAlumnoIdAndObligacionIn(linea.getAlumno().getId(), List.of(obligacion)).isEmpty()) {
				conflictos.add(linea.getAlumno().nombreCompleto() + ": " + linea.getDescripcion());
			}
		}
		if (!conflictos.isEmpty()) {
			throw new ReglaNegocioException("No se confirmó nada: estas deudas ya están en el cronograma de los alumnos. "
					+ "Devuelve el lote para que Administración las quite: " + String.join("; ", conflictos) + ".");
		}
		lote.confirmar(usuario, ahora());
		Long anioId = lote.getAnioEscolar().getId();
		for (LineaSaldoInicial linea : vigentes) {
			cuotas.save(Cuota.deSaldoInicial(linea,
					matriculas.findByAlumnoIdAndAnioEscolarId(linea.getAlumno().getId(), anioId).orElse(null)));
		}
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_CONFIRMADO, "lote_saldo_inicial", loteId.toString(),
				EstadoLote.ENVIADO.name(), cabecera(lote) + " · " + vigentes.size() + " cuotas creadas por "
						+ Dinero.formatear(lote.totalLineas()),
				"Lote creado por " + lote.getCreadoPor() + " y enviado por " + lote.getEnviadoPor() + ".");
		return vigentes.size();
	}

	@Transactional
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public void devolver(Long loteId, String motivo) {
		LoteSaldoInicial lote = bloquear(loteId);
		lote.devolver(SesionActual.usuario(), motivo, ahora());
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_DEVUELTO, "lote_saldo_inicial", loteId.toString(),
				EstadoLote.ENVIADO.name(), EstadoLote.BORRADOR.name(), "Motivo: " + lote.getMotivoDevolucion());
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void descartar(Long loteId, String motivo) {
		LoteSaldoInicial lote = bloquear(loteId);
		lote.descartar(SesionActual.usuario(), motivo, ahora());
		auditoria.registrar(AccionAuditoria.SALDO_INICIAL_DESCARTADO, "lote_saldo_inicial", loteId.toString(),
				cabecera(lote), null, "Motivo: " + lote.getMotivoDescarte());
	}

	private Alumno alumnoPorDocumento(String documento) {
		String numero = Normalizador.sinEspacios(documento);
		if (numero == null) {
			throw new ReglaNegocioException("Escribe el DNI o documento del alumno.");
		}
		List<Alumno> encontrados = alumnos.findByDocumentoNumeroIn(List.of(numero.toUpperCase(Locale.ROOT)));
		if (encontrados.isEmpty()) {
			throw new ReglaNegocioException("No hay un alumno con el documento «" + numero + "». Regístralo o "
					+ "impórtalo primero.");
		}
		if (encontrados.size() > 1) {
			throw new ReglaNegocioException("Hay más de un alumno con el número «" + numero + "» (con distinto tipo de "
					+ "documento). Revisa sus fichas.");
		}
		return encontrados.get(0);
	}

	private LoteSaldoInicial buscar(Long id) {
		return lotes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Lote no encontrado"));
	}

	private LoteSaldoInicial bloquear(Long id) {
		return lotes.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Lote no encontrado"));
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	private static String cabecera(LoteSaldoInicial lote) {
		return "Corte " + Calendario.formatear(lote.getFechaCorte()) + " · Informe: " + lote.getDocumentoReferencia()
				+ " · Total declarado " + Dinero.formatear(lote.getTotalDeclarado());
	}

	private static String linea(LineaSaldoInicial l) {
		return l.getAlumno().nombreCompleto() + " (" + l.getAlumno().getDocumento().enmascarado() + "): "
				+ l.getDescripcion() + " " + Dinero.formatear(l.getMonto()) + ", vence "
				+ Calendario.formatear(l.getFechaVencimiento());
	}
}
