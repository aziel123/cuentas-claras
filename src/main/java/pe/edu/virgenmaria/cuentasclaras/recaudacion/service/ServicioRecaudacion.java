package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TotalNoCoincideException;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.ArchivoCargado;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.RegistroArchivos;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.TipoArchivo;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.ValidadorArchivoPlano;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.PrincipalConColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.config.PropiedadesRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.ConfirmacionRecaudacionVista;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LineaMuestra;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LineaPrevia;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.VistaPreviaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.formato.ErrorFila;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.formato.FilaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.formato.LecturaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLote;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LineaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LoteRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.ModalidadRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.MotivoExcepcion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LineaRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LoteRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ReglasRecaudacion.Deuda;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ReglasRecaudacion.Resolucion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ControlParticipantes;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recaudación bancaria (sprint 4, tanda 2): el archivo del banco con los pagos que las familias hicieron con el código
 * del alumno.
 * <ol>
 *   <li>{@link #previsualizar} (Administración): valida y lee el archivo con el adaptador del banco, y muestra cómo
 *       quedaría cada línea (se aplicará a…, por revisar por…, ya registrada). No guarda nada ni audita: la revisión vive
 *       en la sesión.</li>
 *   <li>{@link #registrar} (Administración): vuelve a leer el archivo y compara la huella; guarda el archivo original con
 *       su SHA-256, el lote CARGADO y sus líneas PENDIENTE. Todo o nada. El mismo archivo no se carga dos veces.</li>
 *   <li>{@link #confirmar} (Promotoría o Dirección, NUNCA quien lo subió): escribe a ciegas el total que ve en el portal
 *       del banco. Si coincide, el lote queda CONFIRMADO y el sistema aplica los pagos después del commit; si no, suma un
 *       intento y al llegar al máximo queda RECHAZADO con alerta crítica.</li>
 * </ol>
 * Ni la revisión ni la bitácora ni el detalle del lote muestran el total mientras está por confirmar.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioRecaudacion {

	private static final Logger LOG = LoggerFactory.getLogger(ServicioRecaudacion.class);

	private static final SecureRandom AZAR = new SecureRandom();

	private final LectoresRecaudacion lectores;

	private final RegistroArchivos archivos;

	private final LoteRecaudacionRepository lotes;

	private final LineaRecaudacionRepository lineas;

	private final AlumnoRepository alumnos;

	private final CuotaRepository cuotas;

	private final PagoRepository pagos;

	private final List<ExportadorBaseDeudas> exportadores;

	private final ControlParticipantes participantes;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final PropiedadesRecaudacion propiedades;

	private final Clock reloj;

	public ServicioRecaudacion(LectoresRecaudacion lectores, RegistroArchivos archivos, LoteRecaudacionRepository lotes,
			LineaRecaudacionRepository lineas, AlumnoRepository alumnos, CuotaRepository cuotas, PagoRepository pagos,
			List<ExportadorBaseDeudas> exportadores, ControlParticipantes participantes, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, PropiedadesRecaudacion propiedades, Clock reloj) {
		this.lectores = lectores;
		this.archivos = archivos;
		this.lotes = lotes;
		this.lineas = lineas;
		this.alumnos = alumnos;
		this.cuotas = cuotas;
		this.pagos = pagos;
		this.exportadores = List.copyOf(exportadores);
		this.participantes = participantes;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	/** Lo leído y clasificado de un archivo (sin guardar nada). */
	private record Plan(LecturaRecaudacion lectura, String sha256, List<ErrorFila> errores, List<LineaPrevia> detalle,
			Map<Integer, Alumno> alumnoDeFila, Map<Integer, Cuota> cuotaDeFila, String huella, String yaCargado) {

		long contar(String resultado) {
			return detalle.stream().filter(l -> l.resultado().equals(resultado)).count();
		}
	}

	/**
	 * Paso 2: valida, lee y clasifica el archivo. Solo lectura: no guarda nada ni audita.
	 *
	 * @param tamanoReal el tamaño completo del archivo subido (si se leyó solo el comienzo porque pasaba el máximo)
	 */
	@Transactional(readOnly = true)
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public VistaPreviaRecaudacion previsualizar(String nombreArchivo, byte[] contenido, long tamanoReal) {
		PrincipalConColegio actor = actor();
		ValidadorArchivoPlano.exigirTamano(tamanoReal);
		String nombre = nombreSeguro(nombreArchivo);
		Plan plan = planificar(nombre, contenido);
		LecturaRecaudacion lectura = plan.lectura();
		LOG.info("Vista previa de recaudación: sha256={}, bytes={}, líneas={}, errores={}", plan.sha256(),
				contenido.length, lectura.filas().size(), plan.errores().size());
		return new VistaPreviaRecaudacion(UUID.randomUUID(), actor.colegioId(), actor.usuarioId(), nombre, plan.sha256(),
				contenido.length, contenido.clone(), propiedades.banco().etiqueta(), lectura.formato(), desde(lectura),
				hasta(lectura), lectura.filas().size(), plan.detalle(), plan.errores(),
				(int) plan.contar(LineaPrevia.APLICAR), (int) plan.contar(LineaPrevia.EXCEPCION),
				(int) plan.contar(LineaPrevia.YA_REGISTRADA), plan.huella(), plan.yaCargado());
	}

	/**
	 * Paso 3: registra el lote CARGADO con el archivo original y sus líneas, en UNA transacción (todo o nada). Vuelve a
	 * leer el archivo y compara la huella de la revisión: si algo cambió (una cuota se pagó en caja, otro archivo trae la
	 * misma operación), pide revisar de nuevo.
	 *
	 * @return el id del lote
	 */
	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public Long registrar(VistaPreviaRecaudacion previa, UUID token) {
		if (previa == null) {
			throw new ReglaNegocioException("No hay una revisión pendiente. Sube el archivo otra vez.");
		}
		PrincipalConColegio actor = actor();
		if (!previa.colegioId().equals(actor.colegioId()) || !previa.usuarioId().equals(actor.usuarioId())
				|| token == null || !token.equals(previa.token())) {
			throw new ReglaNegocioException("Esta revisión no es válida: es de otra sesión o ya se usó. Sube el archivo "
					+ "otra vez.");
		}
		Plan plan = planificar(previa.archivoNombre(), previa.contenido());
		if (!plan.errores().isEmpty()) {
			throw new ReglaNegocioException("El archivo tiene errores: no se registra nada mientras los tenga. Descárgalo "
					+ "otra vez del portal del banco.");
		}
		if (plan.yaCargado() != null) {
			throw new ReglaNegocioException(plan.yaCargado());
		}
		if (!plan.huella().equals(previa.huella())) {
			throw new ReglaNegocioException("Los datos cambiaron desde la revisión (alguien registró un pago o se cargó otro "
					+ "archivo con las mismas operaciones). Sube el archivo otra vez para revisarlo antes de registrarlo.");
		}
		LecturaRecaudacion lectura = plan.lectura();
		BigDecimal total = Dinero.sumar(lectura.filas().stream().map(FilaRecaudacion::monto).toList());
		ArchivoCargado archivo = archivos.guardar(TipoArchivo.RECAUDACION, previa.archivoNombre(), previa.contenido());
		LoteRecaudacion lote = lotes.save(LoteRecaudacion.registrar(archivo.getId(), archivo.getSha256(),
				propiedades.banco(), lectura.formato(), lectura.fechaProceso(), desde(lectura), hasta(lectura),
				lectura.filas().size(), total, lectura.totalDeclarado(), propiedades.muestreo()));
		for (FilaRecaudacion fila : lectura.filas()) {
			lineas.save(LineaRecaudacion.nueva(lote, fila.numero(), fila.fechaPago(), fila.codigo(),
					plan.alumnoDeFila().get(fila.numero()), plan.cuotaDeFila().get(fila.numero()), fila.monto(),
					fila.moneda(), fila.operacion()));
		}
		// Sin el total: la bitácora la ve Promotoría, que debe escribirlo a ciegas desde el portal del banco.
		auditoria.registrar(AccionAuditoria.RECAUDACION_CARGADA, "lote_recaudacion", lote.getId().toString(), null,
				EstadoLote.CARGADO.name() + " · " + lote.getLineas() + " pagos del " + Calendario.formatear(lote.getDesde())
						+ " al " + Calendario.formatear(lote.getHasta()),
				"Archivo «" + archivo.getNombre() + "» (" + archivo.getBytes() + " bytes, SHA-256 " + archivo.getSha256()
						+ "), " + lote.getBanco().etiqueta() + ", formato " + lote.getFormato() + ". Revisión: "
						+ plan.contar(LineaPrevia.APLICAR) + " se aplicarían, " + plan.contar(LineaPrevia.EXCEPCION)
						+ " por revisar y " + plan.contar(LineaPrevia.YA_REGISTRADA) + " ya registradas. Falta que otra "
						+ "persona de Promotoría o Dirección escriba a ciegas el total que ve en el banco.");
		LOG.info("Recaudación registrada: lote={}, sha256={}, líneas={}", lote.getId(), archivo.getSha256(),
				lote.getLineas());
		return lote.getId();
	}

	/** Quien subió el archivo lo descarta mientras nadie lo confirmó (por ejemplo, subió el del día equivocado). */
	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void descartar(Long loteId, String motivo) {
		LoteRecaudacion lote = lotes.bloquear(loteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Lote no encontrado"));
		String usuario = usuario();
		if (!usuario.equals(lote.getCreadoPor())) {
			throw new ReglaNegocioException("Solo quien subió el archivo lo descarta (lo subió " + lote.getCreadoPor()
					+ ").");
		}
		lote.descartar(usuario, motivo, ahora());
		auditoria.registrar(AccionAuditoria.RECAUDACION_DESCARTADA, "lote_recaudacion", loteId.toString(),
				EstadoLote.CARGADO.name(), EstadoLote.DESCARTADO.name(), "Descartó el archivo de recaudación del "
						+ Calendario.formatear(lote.getDesde()) + " al " + Calendario.formatear(lote.getHasta()) + " ("
						+ lote.getLineas() + " pagos) antes de que se confirmara. Motivo: " + lote.getMotivoRechazo());
	}

	/**
	 * Lo que ve quien confirma: banco, fechas, cuántos pagos y la muestra FIJA de líneas elegida al registrar el archivo
	 * (con un azar que quien lo subió no puede predecir; nunca todas), para buscarlas en el portal del banco. Nunca el
	 * total ni el monto de ninguna línea (S4-A1: con los montos de la muestra, recargando, se reconstruía el total).
	 */
	@Transactional(readOnly = true)
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public ConfirmacionRecaudacionVista paraConfirmar(Long loteId) {
		LoteRecaudacion lote = lotes.findById(loteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Lote no encontrado"));
		boolean participo = participantes.ampliar(Set.of(lote.getCreadoPor())).contains(usuario());
		Set<Integer> elegidas = Set.copyOf(lote.numerosMuestra());
		List<LineaMuestra> muestra = lineas.findByLoteIdOrderByNumeroAsc(loteId).stream()
				.filter(l -> elegidas.contains(l.getNumero()))
				.map(l -> new LineaMuestra(CodigoPago.legible(l.getCodigo()), l.getFechaPago(), l.getNumeroOperacion()))
				.toList();
		return new ConfirmacionRecaudacionVista(lote.getId(), lote.getVersion(), lote.getBanco().etiqueta(),
				lote.getDesde(), lote.getHasta(), lote.getLineas(), lote.getCreadoPor(), lote.getCreadoEn(),
				lote.intentosRestantes(propiedades.intentosConfirmacion()), participo,
				lote.getEstado() == EstadoLote.CARGADO ? muestra : List.of(), lote.getEstado().name(),
				lote.getEstado().etiqueta());
	}

	/**
	 * Confirma a ciegas: {@code totalVisto} es el total que quien confirma ve en el portal del banco. Quien subió el
	 * archivo (o preparó la cuenta de quien lo subió) no confirma: el intento queda en la bitácora. Un total distinto se
	 * audita resaltado y suma un intento; al llegar al máximo, el lote queda RECHAZADO y no se aplica ningún pago.
	 */
	@Transactional(noRollbackFor = { AutoaprobacionException.class, TotalNoCoincideException.class })
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public void confirmar(Long loteId, Long version, BigDecimal totalVisto) {
		LoteRecaudacion lote = lotes.bloquear(loteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Lote no encontrado"));
		String usuario = usuario();
		if (lote.getEstado() != EstadoLote.CARGADO) {
			throw new ReglaNegocioException("Este archivo ya no está por confirmar (está "
					+ lote.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT) + ").");
		}
		if (version == null || !version.equals(lote.getVersion())) {
			throw new ReglaNegocioException("El lote cambió desde que lo abriste; ábrelo de nuevo.");
		}
		if (participantes.ampliar(Set.of(lote.getCreadoPor())).contains(usuario)) {
			auditoria.registrar(AccionAuditoria.AUTOAPROBACION_RECHAZADA, "lote_recaudacion", loteId.toString(), null,
					"Recaudación del " + Calendario.formatear(lote.getDesde()) + " al "
							+ Calendario.formatear(lote.getHasta()),
					"Intentó confirmar un archivo de recaudación que subió (o subió una cuenta que preparó). Se rechazó.");
			throw new AutoaprobacionException("No puedes confirmar un archivo que tú subiste: debe confirmarlo otra persona "
					+ "de Promotoría o Dirección, mirando el portal del banco.");
		}
		if (totalVisto == null) {
			throw new ReglaNegocioException("Escribe el total recaudado que ves en el portal del banco.");
		}
		BigDecimal escrito = Dinero.normalizar(totalVisto);
		LocalDateTime ahora = ahora();
		if (!Dinero.iguales(escrito, lote.getTotal())) {
			boolean rechazado = lote.intentoFallido(propiedades.intentosConfirmacion(), usuario, ahora);
			int quedan = lote.intentosRestantes(propiedades.intentosConfirmacion());
			auditoria.registrar(AccionAuditoria.RECAUDACION_TOTAL_NO_COINCIDE, "lote_recaudacion", loteId.toString(), null,
					"Total escrito a ciegas: " + Dinero.formatear(escrito), "El total que escribió " + usuario
							+ " no coincide con el del archivo (intento " + lote.getIntentosConfirmacion() + " de "
							+ propiedades.intentosConfirmacion() + "). Archivo subido por " + lote.getCreadoPor() + ".");
			if (rechazado) {
				auditoria.registrar(AccionAuditoria.RECAUDACION_RECHAZADA, "lote_recaudacion", loteId.toString(),
						EstadoLote.CARGADO.name(), EstadoLote.RECHAZADO.name(), lote.getMotivoRechazo() + " Archivo "
								+ "subido por " + lote.getCreadoPor() + ": revisa con el banco y con quien lo subió.");
				throw new TotalNoCoincideException("No coincide otra vez. El archivo quedó RECHAZADO y no se aplicará "
						+ "ningún pago; Promotoría recibe una alerta. Revisa con el banco y con quien lo subió.");
			}
			throw new TotalNoCoincideException("No coincide. Revisa el total en el portal del banco. Te queda" + (quedan == 1
					? " 1 intento." : "n " + quedan + " intentos."));
		}
		lote.confirmar(usuario, escrito, ahora);
		lotes.saveAndFlush(lote);
		auditoria.registrar(AccionAuditoria.RECAUDACION_CONFIRMADA, "lote_recaudacion", loteId.toString(),
				EstadoLote.CARGADO.name(), EstadoLote.CONFIRMADO.name() + " · " + lote.getLineas() + " pagos · "
						+ Dinero.formatear(lote.getTotal()),
				usuario + " escribió a ciegas el total que ve en el banco y coincide con el archivo subido por "
						+ lote.getCreadoPor() + ". El sistema aplica los pagos.");
		eventos.publishEvent(new LoteConfirmado(loteId, lote.getColegioId()));
	}

	/** La base de deudas para el banco (cuotas por pagar con su código), en el formato del banco configurado. */
	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public ArchivoExportado exportarBaseDeudas() {
		ExportadorBaseDeudas exportador = exportadores.stream().filter(e -> e.banco() == propiedades.banco()).findFirst()
				.or(() -> exportadores.stream().filter(e -> e.banco() == pe.edu.virgenmaria.cuentasclaras.recaudacion
						.model.BancoRecaudacion.GENERICO).findFirst())
				.orElseThrow(() -> new IllegalStateException("Falta el exportador de la base de deudas"));
		List<Cuota> porPagar = cuotas.porPagar().stream().filter(c -> c.admiteCobro() && !c.anulacionPendiente()
				&& c.saldo().signum() > 0).toList();
		byte[] contenido = exportador.exportar(porPagar);
		auditoria.registrar(AccionAuditoria.BASE_DEUDAS_EXPORTADA, "cuota", "base-de-deudas", null,
				porPagar.size() + " cuotas por pagar", "Exportó la base de deudas para el banco (" + exportador.banco()
						.etiqueta() + "): " + porPagar.size() + " cuotas con el código del alumno y el de la cuota. Lleva "
						+ "nombres de alumnos: se entrega solo al banco (encargado de datos).");
		return new ArchivoExportado(exportador.nombreArchivo(), contenido);
	}

	/** Un archivo para descargar. */
	public record ArchivoExportado(String nombre, byte[] contenido) {
	}

	// --- Lectura y clasificación (vista previa y registro) ---

	private Plan planificar(String nombre, byte[] contenido) {
		LocalDate hoy = LocalDate.now(reloj);
		LecturaRecaudacion lectura = lectores.para(nombre).leer(nombre, contenido, hoy);
		String sha256 = RegistroArchivos.sha256(contenido);
		List<ErrorFila> errores = new ArrayList<>(lectura.errores());
		List<LineaPrevia> detalle = new ArrayList<>();
		Map<Integer, Alumno> alumnoDeFila = new HashMap<>();
		Map<Integer, Cuota> cuotaDeFila = new HashMap<>();
		Map<Long, BigDecimal> saldos = new HashMap<>();
		Set<String> operacionesDelArchivo = new HashSet<>();
		StringBuilder huella = new StringBuilder(sha256);
		for (FilaRecaudacion fila : lectura.filas()) {
			Alumno alumno = CodigoPago.alumnoDe(fila.codigo()).flatMap(alumnos::findById).orElse(null);
			Cuota referida = null;
			if (fila.referenciaDeuda() != null && propiedades.modalidad() == ModalidadRecaudacion.CON_BASE_DE_DEUDAS) {
				referida = CodigoPago.cuotaDe(fila.referenciaDeuda()).flatMap(cuotas::findById).orElse(null);
				if (referida == null) {
					errores.add(new ErrorFila(fila.linea(), "La referencia de deuda " + fila.referenciaDeuda()
							+ " no es ninguna cuota del colegio (no es de nuestra base de deudas o está alterada)."));
					continue;
				}
			}
			if (alumno != null) {
				alumnoDeFila.put(fila.numero(), alumno);
			}
			if (referida != null) {
				cuotaDeFila.put(fila.numero(), referida);
			}
			boolean enOtroPago = pagos.existsByOperacionVigente(fila.operacion())
					|| lineas.pendientesConOperacion(fila.operacion()) > 0;
			boolean repetida = !operacionesDelArchivo.add(fila.operacion());
			List<Deuda> delAlumno = alumno == null || referida != null ? List.of()
					: cuotas.findByAlumnoIdOrderByFechaVencimientoAscIdAsc(alumno.getId()).stream()
							.map(c -> deuda(c, saldos)).toList();
			Resolucion resolucion = ReglasRecaudacion.resolver(fila.monto(), fila.moneda(), enOtroPago || repetida,
					alumno == null ? null : alumno.getId(), referida == null ? null : deuda(referida, saldos), delAlumno,
					propiedades.aceptarParciales());
			LineaPrevia previa = previa(fila, alumno, resolucion, enOtroPago, repetida, saldos);
			detalle.add(previa);
			huella.append('|').append(fila.numero()).append(':').append(previa.resultado()).append(':')
					.append(resolucion.aplicable() ? resolucion.cuotas() : resolucion.motivo());
		}
		String yaCargado = lotes.findByShaVigente(sha256).map(l -> "Este archivo ya se cargó en el lote " + l.getId()
				+ " (" + l.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT) + "): el mismo archivo no se carga "
				+ "dos veces.").orElse(null);
		return new Plan(lectura, sha256, List.copyOf(errores), List.copyOf(detalle), alumnoDeFila, cuotaDeFila,
				RegistroArchivos.sha256(huella.toString()), yaCargado);
	}

	private static Deuda deuda(Cuota cuota, Map<Long, BigDecimal> saldos) {
		return new Deuda(cuota.getId(), cuota.getAlumno().getId(), cuota.getFechaVencimiento(),
				saldos.computeIfAbsent(cuota.getId(), id -> cuota.saldo()),
				cuota.admiteCobro() && !cuota.anulacionPendiente(), cuota.getDescripcion());
	}

	private LineaPrevia previa(FilaRecaudacion fila, Alumno alumno, Resolucion resolucion, boolean enOtroPago,
			boolean repetida, Map<Long, BigDecimal> saldos) {
		String nombre = alumno == null ? null : alumno.nombreCompleto();
		if (resolucion.aplicable()) {
			Map<Long, Cuota> porId = resolucion.cuotas().stream().map(id -> cuotas.findById(id).orElseThrow())
					.collect(Collectors.toMap(Cuota::getId, Function.identity()));
			List<String> partes = new ArrayList<>();
			resolucion.imputaciones().forEach(i -> {
				saldos.merge(i.cuotaId(), i.monto(), BigDecimal::subtract);
				partes.add(porId.get(i.cuotaId()).getDescripcion() + " (" + Dinero.formatear(i.monto()) + ")");
			});
			return new LineaPrevia(fila.numero(), fila.linea(), fila.fechaPago(), CodigoPago.legible(fila.codigo()),
					nombre, fila.monto(), fila.moneda(), fila.operacion(), LineaPrevia.APLICAR, "Se aplicará a "
							+ String.join(", ", partes) + (resolucion.aCuenta() ? ". Pago a cuenta (parcial)." : "."));
		}
		MotivoExcepcion motivo = resolucion.motivo();
		String resultado = motivo == MotivoExcepcion.OPERACION_DUPLICADA ? LineaPrevia.YA_REGISTRADA : LineaPrevia.EXCEPCION;
		String texto = motivo == MotivoExcepcion.OPERACION_DUPLICADA
				? (repetida && !enOtroPago ? "La operación está dos veces en este archivo: la segunda queda por revisar."
						: "La operación ya está registrada en otro pago o en otro archivo por aplicar: queda por revisar.")
				: motivo.descripcion() + ". " + resolucion.detalle();
		return new LineaPrevia(fila.numero(), fila.linea(), fila.fechaPago(), CodigoPago.legible(fila.codigo()), nombre,
				fila.monto(), fila.moneda(), fila.operacion(), resultado, texto);
	}

	private static LocalDate desde(LecturaRecaudacion lectura) {
		return lectura.filas().stream().map(FilaRecaudacion::fechaPago).min(LocalDate::compareTo)
				.orElse(lectura.fechaProceso());
	}

	private static LocalDate hasta(LecturaRecaudacion lectura) {
		return lectura.filas().stream().map(FilaRecaudacion::fechaPago).max(LocalDate::compareTo)
				.orElse(lectura.fechaProceso());
	}

	/** Solo el nombre (sin carpetas), limpio y de hasta 150 caracteres. */
	static String nombreSeguro(String nombre) {
		String base = nombre == null ? "" : nombre.replace('\\', '/');
		base = base.substring(base.lastIndexOf('/') + 1);
		String limpio = Normalizador.limpiar(base);
		if (limpio == null) {
			return "recaudacion.csv";
		}
		return limpio.length() <= 150 ? limpio : limpio.substring(limpio.length() - 150);
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	private static String usuario() {
		return actorAutenticado().getName();
	}

	private static Authentication actorAutenticado() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null) {
			throw new AccessDeniedException("Se requiere un usuario en sesión");
		}
		return autenticacion;
	}

	private static PrincipalConColegio actor() {
		if (actorAutenticado().getPrincipal() instanceof PrincipalConColegio principal && principal.usuarioId() != null
				&& principal.colegioId() != null) {
			return principal;
		}
		throw new AccessDeniedException("Se requiere un usuario en sesión");
	}
}
