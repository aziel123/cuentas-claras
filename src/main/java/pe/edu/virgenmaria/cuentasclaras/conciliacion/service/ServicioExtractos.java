package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.ArchivoCargado;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.RegistroArchivos;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.TipoArchivo;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.ValidadorArchivoPlano;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.PrincipalConColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.config.PropiedadesConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.ConfirmacionExtractoVista;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.PropuestaVista;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaPreviaExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.formato.ErrorExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.formato.FilaExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.formato.LecturaExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.formato.LectoresExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CuentaBancaria;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ExtractoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.MovimientoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ReglaPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.CuentaBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.ExtractoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.MovimientoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.PartidaConciliacionRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.MovimientoAbierto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.Propuesta;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Extracto bancario (sprint 4, tanda 3): el estado de cuenta que da el banco, cargado una vez al día y confirmado a
 * ciegas por otra persona.
 * <ol>
 *   <li>{@link #previsualizar} (Administración): lee el archivo y comprueba que es de una cuenta registrada, que los días
 *       están completos (hasta ayer), que el saldo cuadra fila por fila, que continúa al último extracto (mismo saldo y
 *       día siguiente) y que los días ya cargados que traiga son IDÉNTICOS a lo guardado (el banco no cambia el pasado:
 *       si no lo son, se rechaza, se audita y Promotoría ve una alerta crítica). Muestra las parejas que saldrían. No
 *       guarda nada más.</li>
 *   <li>{@link #registrar} (Administración): todo o nada; el archivo original con su SHA-256, el extracto CARGADO, solo
 *       los movimientos de los días nuevos y las partidas PROPUESTA.</li>
 *   <li>{@link #confirmar} (Promotoría o Dirección, NUNCA quien subió): escribe a ciegas el saldo final que ve en su app
 *       del banco al cierre del último día pendiente; si coincide, se confirma la cadena en orden («el lunes sella el fin
 *       de semana») y el sistema concilia después del commit. Si no, suma un intento; al llegar al máximo, RECHAZADO.</li>
 * </ol>
 * Ni la revisión, ni la bitácora, ni la pantalla de confirmación muestran el saldo final mientras está por confirmar.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioExtractos {

	private static final Logger LOG = LoggerFactory.getLogger(ServicioExtractos.class);

	private static final SecureRandom AZAR = new SecureRandom();

	private final LectoresExtracto lectores;

	private final RegistroArchivos archivos;

	private final CuentaBancariaRepository cuentas;

	private final ExtractoBancarioRepository extractos;

	private final MovimientoBancarioRepository movimientos;

	private final PartidaConciliacionRepository partidas;

	private final ObjetosConciliables objetos;

	private final Emparejador emparejador;

	private final ControlParticipantes participantes;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final PropiedadesConciliacion propiedades;

	private final Clock reloj;

	public ServicioExtractos(LectoresExtracto lectores, RegistroArchivos archivos, CuentaBancariaRepository cuentas,
			ExtractoBancarioRepository extractos, MovimientoBancarioRepository movimientos,
			PartidaConciliacionRepository partidas, ObjetosConciliables objetos, Emparejador emparejador,
			ControlParticipantes participantes, AuditoriaService auditoria, ApplicationEventPublisher eventos,
			PropiedadesConciliacion propiedades, Clock reloj) {
		this.lectores = lectores;
		this.archivos = archivos;
		this.cuentas = cuentas;
		this.extractos = extractos;
		this.movimientos = movimientos;
		this.partidas = partidas;
		this.objetos = objetos;
		this.emparejador = emparejador;
		this.participantes = participantes;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	/** Lo leído y comprobado de un archivo (sin guardar nada). */
	private record Plan(LecturaExtracto lectura, String sha256, CuentaBancaria cuenta, ExtractoBancario anterior,
			List<FilaExtracto> nuevas, int repetidas, LocalDate desde, LocalDate hasta, BigDecimal saldoInicial,
			List<ErrorExtracto> errores, String problema, String continuidad, List<Propuesta> propuestas, String huella) {
	}

	/**
	 * Paso 2: lee y comprueba el archivo. No guarda nada; solo si los días repetidos no coinciden con lo guardado deja el
	 * aviso en la bitácora (EXTRACTO_DISCONTINUO) y lo rechaza.
	 *
	 * @param tamanoReal el tamaño completo del archivo subido (si se leyó solo el comienzo porque pasaba el máximo)
	 */
	@Transactional(noRollbackFor = ExtractoDiscontinuoException.class)
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public VistaPreviaExtracto previsualizar(String nombreArchivo, byte[] contenido, long tamanoReal) {
		PrincipalConColegio actor = actor();
		ValidadorArchivoPlano.exigirTamano(tamanoReal);
		String nombre = nombreSeguro(nombreArchivo);
		Plan plan = planificar(nombre, contenido, false);
		LOG.info("Vista previa de extracto: sha256={}, bytes={}, movimientos nuevos={}, errores={}", plan.sha256(),
				contenido.length, plan.nuevas().size(), plan.errores().size());
		List<PropuestaVista> propuestas = plan.propuestas().stream().map(ServicioExtractos::vista).toList();
		long exactas = plan.propuestas().stream().filter(p -> p.regla() == ReglaPartida.EXACTA).count();
		long sugeridas = plan.propuestas().size() - exactas;
		long abonos = plan.nuevas().stream().filter(f -> f.tipo() == TipoMovimiento.ABONO).count();
		return new VistaPreviaExtracto(UUID.randomUUID(), actor.colegioId(), actor.usuarioId(), nombre, plan.sha256(),
				contenido.length, contenido.clone(), plan.cuenta().getId(), plan.cuenta().descripcion(),
				plan.lectura().formato(), plan.desde(), plan.hasta(), plan.nuevas().size(), (int) abonos,
				plan.nuevas().size() - (int) abonos, plan.repetidas(), plan.continuidad(), plan.errores(), propuestas,
				(int) exactas, (int) sugeridas, (int) Math.max(0, abonos - plan.propuestas().stream()
						.filter(p -> p.movimiento().tipo() == TipoMovimiento.ABONO).count()),
				plan.huella(), plan.problema());
	}

	/**
	 * Paso 3: registra el extracto CARGADO con el archivo original, los movimientos de los días nuevos y sus partidas
	 * PROPUESTA, en UNA transacción. Vuelve a leer el archivo con la cuenta bloqueada y compara la huella.
	 *
	 * @return el id del extracto
	 */
	@Transactional(noRollbackFor = ExtractoDiscontinuoException.class)
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public Long registrar(VistaPreviaExtracto previa, UUID token) {
		if (previa == null) {
			throw new ReglaNegocioException("No hay una revisión pendiente. Sube el extracto otra vez.");
		}
		PrincipalConColegio actor = actor();
		if (!previa.colegioId().equals(actor.colegioId()) || !previa.usuarioId().equals(actor.usuarioId())
				|| token == null || !token.equals(previa.token())) {
			throw new ReglaNegocioException("Esta revisión no es válida: es de otra sesión o ya se usó. Sube el extracto "
					+ "otra vez.");
		}
		Plan plan = planificar(previa.archivoNombre(), previa.contenido(), true);
		if (!plan.errores().isEmpty()) {
			throw new ReglaNegocioException("El extracto tiene errores: no se registra nada mientras los tenga. Descárgalo "
					+ "otra vez del banco.");
		}
		if (plan.problema() != null) {
			throw new ReglaNegocioException(plan.problema());
		}
		if (!plan.huella().equals(previa.huella())) {
			throw new ReglaNegocioException("Los datos cambiaron desde la revisión (alguien cargó otro extracto o registró "
					+ "un pago). Sube el extracto otra vez para revisarlo antes de registrarlo.");
		}
		ArchivoCargado archivo = archivos.guardar(TipoArchivo.EXTRACTO, previa.archivoNombre(), previa.contenido());
		BigDecimal abonos = Dinero.sumar(plan.nuevas().stream().filter(f -> f.tipo() == TipoMovimiento.ABONO)
				.map(FilaExtracto::monto).toList());
		BigDecimal cargos = Dinero.sumar(plan.nuevas().stream().filter(f -> f.tipo() == TipoMovimiento.CARGO)
				.map(FilaExtracto::monto).toList());
		ExtractoBancario extracto = extractos.saveAndFlush(ExtractoBancario.registrar(plan.cuenta(), plan.anterior(),
				archivo.getId(), archivo.getSha256(), plan.lectura().formato(), plan.desde(), plan.hasta(),
				plan.saldoInicial(), abonos, cargos, plan.nuevas().size()));
		List<MovimientoBancario> guardados = new ArrayList<>();
		int numero = 0;
		for (FilaExtracto fila : plan.nuevas()) {
			guardados.add(movimientos.save(MovimientoBancario.nuevo(extracto, ++numero, fila.fecha(), fila.tipo(),
					fila.monto(), fila.saldo(), fila.descripcion(), fila.operacion(), fila.referencia())));
		}
		int propuestas = emparejador.proponer(guardados);
		// Sin el saldo final: la bitácora la ve Promotoría, que debe escribirlo a ciegas desde su app del banco.
		auditoria.registrar(AccionAuditoria.EXTRACTO_CARGADO, "extracto_bancario", extracto.getId().toString(), null,
				EstadoExtracto.CARGADO.name() + " · " + extracto.getMovimientos() + " movimientos del "
						+ Calendario.formatear(extracto.getDesde()) + " al " + Calendario.formatear(extracto.getHasta()),
				"Extracto N.° " + extracto.getSecuencia() + " de la cuenta " + plan.cuenta().descripcion() + ": archivo «"
						+ archivo.getNombre() + "» (" + archivo.getBytes() + " bytes, SHA-256 " + archivo.getSha256()
						+ "). " + plan.continuidad() + ". " + plan.repetidas() + " movimiento(s) de días ya cargados, "
						+ "idénticos, se omitieron. " + propuestas + " pareja(s) propuesta(s). Falta que otra persona de "
						+ "Promotoría o Dirección escriba a ciegas el saldo final que ve en su app del banco.");
		LOG.info("Extracto registrado: id={}, cuenta={}, secuencia={}, movimientos={}", extracto.getId(),
				plan.cuenta().getId(), extracto.getSecuencia(), extracto.getMovimientos());
		return extracto.getId();
	}

	/** Quien subió el extracto lo descarta mientras nadie lo confirmó y si no tiene uno siguiente. */
	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void descartar(Long extractoId, String motivo) {
		ExtractoBancario buscado = extractos.findById(extractoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Extracto no encontrado"));
		cuentas.bloquear(buscado.getCuenta().getId());
		ExtractoBancario extracto = extractos.findById(extractoId).orElseThrow();
		String usuario = usuario();
		if (!usuario.equals(extracto.getCreadoPor())) {
			throw new ReglaNegocioException("Solo quien subió el extracto lo descarta (lo subió " + extracto.getCreadoPor()
					+ ").");
		}
		if (extractos.existsByAnteriorIdAndSecuenciaVigenteIsNotNull(extractoId)) {
			throw new ReglaNegocioException("Primero descarta el extracto siguiente: este ya tiene uno que lo continúa.");
		}
		extracto.descartar(usuario, motivo, ahora());
		extractos.saveAndFlush(extracto);
		int liberadas = liberarPropuestas(extracto, usuario);
		auditoria.registrar(AccionAuditoria.EXTRACTO_DESCARTADO, "extracto_bancario", extractoId.toString(),
				EstadoExtracto.CARGADO.name(), EstadoExtracto.DESCARTADO.name(), "Descartó el extracto del "
						+ Calendario.formatear(extracto.getDesde()) + " al " + Calendario.formatear(extracto.getHasta())
						+ " (" + extracto.getMovimientos() + " movimientos) antes de que se confirmara; se liberaron "
						+ liberadas + " pareja(s) propuesta(s). Motivo: " + extracto.getMotivoRechazo());
	}

	/**
	 * Lo que ve quien confirma: los extractos pendientes de la cuenta, el día cuyo saldo debe escribir y
	 * {@code muestreo-confirmacion} movimientos al azar (con un azar que quien subió no puede predecir) para buscarlos en
	 * su app del banco. Nunca el saldo ni los totales.
	 */
	@Transactional(readOnly = true)
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public ConfirmacionExtractoVista paraConfirmar(Long cuentaId) {
		CuentaBancaria cuenta = cuentas.findById(cuentaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada"));
		List<ExtractoBancario> pendientes = extractos.findByCuentaIdAndEstadoOrderBySecuenciaAsc(cuentaId,
				EstadoExtracto.CARGADO);
		if (pendientes.isEmpty()) {
			return new ConfirmacionExtractoVista(cuentaId, cuenta.descripcion(), List.of(), null, null, null, 0, false,
					List.of());
		}
		ExtractoBancario ultimo = pendientes.getLast();
		boolean participo = participantes.ampliar(pendientes.stream().map(ExtractoBancario::getCreadoPor)
				.collect(Collectors.toSet())).contains(usuario());
		List<MovimientoBancario> todos = new ArrayList<>();
		pendientes.forEach(e -> todos.addAll(movimientos.findByExtractoIdOrderByNumeroAsc(e.getId())));
		Collections.shuffle(todos, AZAR);
		List<ConfirmacionExtractoVista.Muestra> muestra = todos.stream().limit(propiedades.muestreoConfirmacion())
				.sorted(Comparator.comparing(MovimientoBancario::getFecha).thenComparing(MovimientoBancario::getId))
				.map(m -> new ConfirmacionExtractoVista.Muestra(m.getFecha(), m.getTipo().etiqueta(), m.getMonto(),
						m.getDescripcion(), m.getNumeroOperacion()))
				.toList();
		return new ConfirmacionExtractoVista(cuentaId, cuenta.descripcion(), pendientes.stream()
				.map(e -> new ConfirmacionExtractoVista.Pendiente(e.getId(), e.getSecuencia(), e.getDesde(), e.getHasta(),
						e.getMovimientos(), e.getCreadoPor(), e.getCreadoEn()))
				.toList(), ultimo.getId(), ultimo.getVersion(), ultimo.getHasta(),
				ultimo.intentosRestantes(propiedades.intentosConfirmacion()), participo, muestra);
	}

	/**
	 * Confirma a ciegas la cadena pendiente de la cuenta: {@code saldoVisto} es el saldo que quien confirma ve en su app
	 * del banco al cierre del último día pendiente. Quien subió alguno de los pendientes (o preparó la cuenta de quien los
	 * subió) no confirma: el intento queda en la bitácora. Un saldo distinto se audita resaltado y suma un intento; al
	 * llegar al máximo, el extracto queda RECHAZADO y no se concilia.
	 */
	@Transactional(noRollbackFor = { AutoaprobacionException.class, SaldoNoCoincideException.class })
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public void confirmar(Long cuentaId, Long extractoId, Long version, BigDecimal saldoVisto) {
		CuentaBancaria cuenta = cuentas.bloquear(cuentaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada"));
		List<ExtractoBancario> pendientes = extractos.findByCuentaIdAndEstadoOrderBySecuenciaAsc(cuentaId,
				EstadoExtracto.CARGADO);
		if (pendientes.isEmpty()) {
			throw new ReglaNegocioException("No hay extractos por confirmar en esta cuenta.");
		}
		ExtractoBancario ultimo = pendientes.getLast();
		if (extractoId == null || !extractoId.equals(ultimo.getId()) || version == null
				|| !version.equals(ultimo.getVersion())) {
			throw new ReglaNegocioException("Los extractos por confirmar cambiaron desde que abriste la pantalla (se subió "
					+ "otro o alguien escribió un saldo). Ábrela de nuevo.");
		}
		String usuario = usuario();
		Set<String> autores = pendientes.stream().map(ExtractoBancario::getCreadoPor).collect(Collectors.toSet());
		if (participantes.ampliar(autores).contains(usuario)) {
			auditoria.registrar(AccionAuditoria.AUTOAPROBACION_RECHAZADA, "extracto_bancario", ultimo.getId().toString(),
					null, "Extracto del " + Calendario.formatear(ultimo.getDesde()) + " al "
							+ Calendario.formatear(ultimo.getHasta()),
					"Intentó confirmar un extracto que subió (o subió una cuenta que preparó). Se rechazó.");
			throw new AutoaprobacionException("No puedes confirmar un extracto que tú subiste: debe confirmarlo otra persona "
					+ "de Promotoría o Dirección, mirando su app del banco.");
		}
		if (saldoVisto == null) {
			throw new ReglaNegocioException("Escribe el saldo que ves en tu app del banco.");
		}
		BigDecimal escrito = Dinero.normalizar(saldoVisto);
		LocalDateTime ahora = ahora();
		if (!Dinero.iguales(escrito, ultimo.getSaldoFinal())) {
			boolean rechazado = ultimo.intentoFallido(propiedades.intentosConfirmacion(), usuario, ahora);
			extractos.saveAndFlush(ultimo);
			int quedan = ultimo.intentosRestantes(propiedades.intentosConfirmacion());
			auditoria.registrar(AccionAuditoria.EXTRACTO_SALDO_NO_COINCIDE, "extracto_bancario", ultimo.getId().toString(),
					null, "Saldo escrito a ciegas: " + Dinero.formatear(escrito), "El saldo que escribió " + usuario
							+ " al cierre del " + Calendario.formatear(ultimo.getHasta()) + " no coincide con el del extracto "
							+ "(intento " + ultimo.getIntentosConfirmacion() + " de " + propiedades.intentosConfirmacion()
							+ "). Extracto subido por " + ultimo.getCreadoPor() + ".");
			if (rechazado) {
				int liberadas = liberarPropuestas(ultimo, usuario);
				auditoria.registrar(AccionAuditoria.EXTRACTO_RECHAZADO, "extracto_bancario", ultimo.getId().toString(),
						EstadoExtracto.CARGADO.name(), EstadoExtracto.RECHAZADO.name(), ultimo.getMotivoRechazo()
								+ " Se liberaron " + liberadas + " pareja(s) propuesta(s). Extracto subido por "
								+ ultimo.getCreadoPor() + ": revisa con el banco y con quien lo subió.");
				throw new SaldoNoCoincideException("No coincide otra vez. El extracto quedó RECHAZADO y no se concilia; "
						+ "Promotoría recibe una alerta. Revisa con el banco y con quien lo subió.");
			}
			throw new SaldoNoCoincideException("No coincide. Revisa el saldo en tu app del banco al cierre del "
					+ Calendario.formatear(ultimo.getHasta()) + ". Te queda" + (quedan == 1 ? " 1 intento."
							: "n " + quedan + " intentos."));
		}
		// El saldo a ciegas va primero (el trigger lo lee al confirmar los anteriores), luego la cadena en orden.
		ultimo.escribirSaldoCiego(escrito);
		extractos.saveAndFlush(ultimo);
		for (ExtractoBancario e : pendientes) {
			if (!e.getId().equals(ultimo.getId())) {
				e.confirmar(usuario, ultimo.getId(), ahora);
				extractos.saveAndFlush(e);
			}
		}
		ultimo.confirmar(usuario, ultimo.getId(), ahora);
		extractos.saveAndFlush(ultimo);
		ExtractoBancario primero = pendientes.getFirst();
		auditoria.registrar(AccionAuditoria.EXTRACTO_CONFIRMADO, "extracto_bancario", ultimo.getId().toString(),
				EstadoExtracto.CARGADO.name(), EstadoExtracto.CONFIRMADO.name() + " · " + pendientes.size()
						+ " extracto(s) del " + Calendario.formatear(primero.getDesde()) + " al "
						+ Calendario.formatear(ultimo.getHasta()) + " · saldo " + Dinero.formatear(ultimo.getSaldoFinal()),
				usuario + " escribió a ciegas el saldo que ve en su app del banco al cierre del "
						+ Calendario.formatear(ultimo.getHasta()) + " y coincide con el extracto de " + cuenta.descripcion()
						+ ". Se confirmaron " + pendientes.size() + " extracto(s) en orden. El sistema concilia.");
		eventos.publishEvent(new ExtractosConfirmados(cuenta.getColegioId(), cuentaId,
				pendientes.stream().map(ExtractoBancario::getId).toList()));
	}

	/** Al descartar o rechazar un extracto, sus parejas propuestas se descartan y liberan sus objetos. */
	private int liberarPropuestas(ExtractoBancario extracto, String por) {
		LocalDateTime ahora = ahora();
		List<PartidaConciliacion> propuestas = partidas.propuestasDelExtracto(extracto.getId());
		for (PartidaConciliacion p : propuestas) {
			p.descartar(por, ahora);
			partidas.save(p);
		}
		return propuestas.size();
	}

	// --- Lectura y comprobación (vista previa y registro) ---

	private Plan planificar(String nombre, byte[] contenido, boolean bloquear) {
		LocalDate hoy = LocalDate.now(reloj);
		LecturaExtracto lectura = lectores.para(nombre).leer(nombre, contenido);
		String sha256 = RegistroArchivos.sha256(contenido);
		String digitos = CuentaBancaria.soloDigitos(lectura.cuenta());
		CuentaBancaria cuenta = cuentas.findByActivaTrueOrderByIdAsc().stream()
				.filter(c -> !digitos.isEmpty() && c.digitos().equals(digitos)).findFirst()
				.orElseThrow(() -> new ReglaNegocioException("El extracto es de la cuenta «" + recortar(lectura.cuenta())
						+ "», que no está registrada o está desactivada. Promotoría registra las cuentas del colegio en "
						+ "Conciliación › Cuentas."));
		if (bloquear) {
			cuenta = cuentas.bloquear(cuenta.getId()).orElseThrow();
		}
		List<ErrorExtracto> errores = new ArrayList<>(lectura.errores());
		List<FilaExtracto> filas = lectura.filas();
		filas.stream().filter(f -> !f.fecha().isBefore(hoy)).findFirst().ifPresent(f -> errores.add(new ErrorExtracto(
				f.linea(), "trae movimientos de hoy (" + Calendario.formatear(f.fecha()) + "): sube solo días completos, "
						+ "hasta ayer, para que el saldo final sea el del cierre del día")));
		if (!errores.isEmpty() || filas.isEmpty()) {
			return new Plan(lectura, sha256, cuenta, null, List.of(), 0, null, null, null, List.copyOf(errores), null,
					null, List.of(), sha256);
		}
		Optional<ExtractoBancario> ultimo = extractos.findFirstByCuentaIdAndSecuenciaVigenteIsNotNullOrderBySecuenciaDesc(
				cuenta.getId());
		List<FilaExtracto> nuevas;
		int repetidas = 0;
		String continuidad;
		String problema = null;
		BigDecimal saldoInicial;
		LocalDate desde;
		if (ultimo.isPresent()) {
			ExtractoBancario anterior = ultimo.get();
			List<ExtractoBancario> cadena = extractos.findByCuentaIdAndSecuenciaVigenteIsNotNullOrderBySecuenciaAsc(
					cuenta.getId());
			LocalDate inicioCadena = cadena.getFirst().getDesde();
			// Los días que ya están en la cadena deben ser IDÉNTICOS a lo guardado; los anteriores a la cadena se ignoran.
			List<FilaExtracto> yaCargadas = filas.stream().filter(f -> !f.fecha().isBefore(inicioCadena)
					&& !f.fecha().isAfter(anterior.getHasta())).toList();
			if (!yaCargadas.isEmpty()) {
				LocalDate primerDia = yaCargadas.getFirst().fecha();
				List<String> guardadas = movimientos.vigentesEntre(cuenta.getId(), primerDia, anterior.getHasta()).stream()
						.map(m -> new FilaExtracto(0, m.getFecha(), m.getDescripcion(), m.getNumeroOperacion(),
								m.getReferencia(), m.getTipo(), m.getMonto(), m.getSaldo()).huella())
						.toList();
				List<String> delArchivo = yaCargadas.stream().map(FilaExtracto::huella).toList();
				if (!guardadas.equals(delArchivo)) {
					discontinuo(cuenta, anterior, primerDia, sha256, guardadas.size(), delArchivo.size());
				}
			}
			repetidas = (int) filas.stream().filter(f -> !f.fecha().isAfter(anterior.getHasta())).count();
			nuevas = filas.stream().filter(f -> f.fecha().isAfter(anterior.getHasta())).toList();
			desde = anterior.getHasta().plusDays(1);
			saldoInicial = nuevas.isEmpty() ? anterior.getSaldoFinal()
					: nuevas.getFirst().saldo().subtract(nuevas.getFirst().efecto());
			continuidad = "Continúa al extracto N.° " + anterior.getSecuencia() + " (hasta el "
					+ Calendario.formatear(anterior.getHasta()) + ", " + anterior.getEstado().etiqueta()
							.toLowerCase(Locale.ROOT) + ")";
			if (nuevas.isEmpty()) {
				problema = "El extracto no trae días nuevos: la cuenta ya está cargada hasta el "
						+ Calendario.formatear(anterior.getHasta()) + ".";
			}
			else if (!Dinero.iguales(saldoInicial, anterior.getSaldoFinal())) {
				problema = "El saldo no continúa al extracto anterior: el primer día nuevo ("
						+ Calendario.formatear(nuevas.getFirst().fecha()) + ") no empieza con el saldo final del "
						+ Calendario.formatear(anterior.getHasta()) + ". Falta un movimiento o el archivo está alterado: "
						+ "descárgalo otra vez del banco.";
			}
		}
		else {
			nuevas = filas;
			desde = filas.getFirst().fecha();
			saldoInicial = filas.getFirst().saldo().subtract(filas.getFirst().efecto());
			continuidad = "Primer extracto de la cuenta: desde aquí empieza la cadena de saldos";
		}
		LocalDate hasta = nuevas.isEmpty() ? null : nuevas.getLast().fecha();
		List<Propuesta> propuestas = List.of();
		if (problema == null && !nuevas.isEmpty()) {
			propuestas = ReglasEmparejamiento.proponer(nuevas.stream().map(f -> new MovimientoAbierto(null, f.fecha(),
					f.tipo(), f.monto(), f.operacion(), f.descripcion(), f.referencia())).toList(),
					objetos.abiertos(desde.minusDays(Emparejador.MARGEN_DIAS), hasta.plusDays(Emparejador.MARGEN_DIAS)),
					emparejador.parametros(Set.of())).propuestas();
		}
		StringBuilder huella = new StringBuilder(sha256).append('|').append(cuenta.getId()).append('|')
				.append(ultimo.map(e -> e.getId() + ":" + e.getVersion()).orElse("-")).append('|').append(nuevas.size());
		propuestas.forEach(p -> huella.append('|').append(p.regla()).append(':').append(p.objeto().clave()));
		return new Plan(lectura, sha256, cuenta, ultimo.orElse(null), nuevas, repetidas, desde, hasta, saldoInicial,
				List.copyOf(errores), problema, continuidad, propuestas, RegistroArchivos.sha256(huella.toString()));
	}

	/** Los días repetidos no coinciden con lo guardado: se audita (resaltado) y se rechaza el archivo. */
	private void discontinuo(CuentaBancaria cuenta, ExtractoBancario anterior, LocalDate primerDia, String sha256,
			int guardados, int delArchivo) {
		auditoria.registrar(AccionAuditoria.EXTRACTO_DISCONTINUO, "cuenta_bancaria", cuenta.getId().toString(), null,
				"Archivo SHA-256 " + sha256, "Un extracto de " + cuenta.descripcion() + " trae los días del "
						+ Calendario.formatear(primerDia) + " al " + Calendario.formatear(anterior.getHasta())
						+ ", ya cargados, con movimientos distintos de los guardados (" + guardados + " guardados, "
						+ delArchivo + " en el archivo). El banco no cambia el pasado: el archivo está alterado o es de "
						+ "otra cuenta. No se registró nada.");
		throw new ExtractoDiscontinuoException("Este archivo trae días ya cargados (del " + Calendario.formatear(primerDia)
				+ " al " + Calendario.formatear(anterior.getHasta()) + ") con movimientos DISTINTOS de los guardados. El "
				+ "banco no cambia el pasado: no se registra nada y Promotoría recibe una alerta. Descarga el extracto otra "
				+ "vez del banco, sin abrirlo ni cambiarlo.");
	}

	private static PropuestaVista vista(Propuesta p) {
		return new PropuestaVista(p.movimiento().fecha(), p.movimiento().descripcion(), p.movimiento().monto(),
				p.objeto().detalle(), p.regla().etiqueta(), p.regla() == ReglaPartida.EXACTA ? "exito"
						: p.parecido() ? "peligro" : "alerta", p.avisos(), p.parecido());
	}

	/** Solo el nombre (sin carpetas), limpio y de hasta 150 caracteres. */
	static String nombreSeguro(String nombre) {
		String base = nombre == null ? "" : nombre.replace('\\', '/');
		base = base.substring(base.lastIndexOf('/') + 1);
		String limpio = Normalizador.limpiar(base);
		if (limpio == null) {
			return "extracto.csv";
		}
		return limpio.length() <= 150 ? limpio : limpio.substring(limpio.length() - 150);
	}

	private static String recortar(String texto) {
		String t = texto == null ? "" : texto;
		return t.length() <= 30 ? t : t.substring(0, 30) + "…";
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
