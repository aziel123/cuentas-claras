package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.TextoSeguro;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.DevolucionLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.NumeroOperacion;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.AplicacionLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.CuotaDestino;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.LineaExcepcionVista;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLinea;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LineaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.MotivoExcepcion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LineaRecaudacionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Una línea del banco que no se pudo aplicar sola (EXCEPCION): Administración pide APLICARLA a cuotas de una familia (la
 * del código o, si el código estaba errado, la del código correcto, con llamada a ambas) o DEVOLVERLA por transferencia.
 * Lo aprueba otra persona de Promotoría o Dirección (quien pide no aprueba: la bandeja lo impide); el pago de una
 * aplicación aprobada lo registra el sistema, y la devolución aprobada la ejecuta Administración, que no puede ser quien
 * la aprobó (también lo exige el trigger).
 */
@Service
@Transactional
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioExcepcionesRecaudacion {

	static final String ENTIDAD = "linea_recaudacion";

	static final String DATO_FAMILIA = "familiaId";

	static final String DATO_BANCO = "banco";

	static final String DATO_CUENTA = "cuenta";

	static final String DATO_TITULAR = "titular";

	static final String DATO_CUOTAS = "cuotas";

	private final LineaRecaudacionRepository lineas;

	private final AlumnoRepository alumnos;

	private final FamiliaRepository familias;

	private final ApoderadoRepository apoderados;

	private final CuotaRepository cuotas;

	private final PagoRepository pagos;

	private final SolicitudCambioRepository pendientes;

	private final RegistroSolicitudes solicitudes;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ServicioExcepcionesRecaudacion(LineaRecaudacionRepository lineas, AlumnoRepository alumnos,
			FamiliaRepository familias, ApoderadoRepository apoderados, CuotaRepository cuotas, PagoRepository pagos,
			SolicitudCambioRepository pendientes, RegistroSolicitudes solicitudes, AuditoriaService auditoria,
			Clock reloj) {
		this.lineas = lineas;
		this.alumnos = alumnos;
		this.familias = familias;
		this.apoderados = apoderados;
		this.cuotas = cuotas;
		this.pagos = pagos;
		this.pendientes = pendientes;
		this.solicitudes = solicitudes;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/**
	 * La línea con lo necesario para resolverla.
	 *
	 * @param codigoDestino código de pago del alumno correcto (si el del banco estaba errado) o {@code null}
	 */
	@Transactional(readOnly = true)
	public LineaExcepcionVista detalle(Long lineaId, String codigoDestino) {
		LineaRecaudacion linea = lineas.findById(lineaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Línea no encontrada"));
		Familia origen = linea.getAlumno() == null ? null : linea.getAlumno().getFamilia();
		Alumno corregido = codigoDestino == null || codigoDestino.isBlank() ? null
				: CodigoPago.alumnoDe(codigoDestino).flatMap(alumnos::findById).orElse(null);
		Familia destino = corregido != null ? corregido.getFamilia() : origen;
		List<CuotaDestino> porPagar = destino == null ? List.of()
				: cuotas.porPagarDeFamilia(destino.getId()).stream().filter(c -> c.admiteCobro() && !c.anulacionPendiente())
						.map(c -> new CuotaDestino(c.getId(), c.getDescripcion(), c.getAlumno().nombreCompleto(),
								c.getFechaVencimiento(), c.saldo()))
						.toList();
		boolean enExcepcion = linea.getEstado() == EstadoLinea.EXCEPCION;
		boolean sinPendiente = pendientes.findByEntidadAndEntidadIdAndEstadoOrderByIdAsc(ENTIDAD, lineaId,
				EstadoSolicitud.PENDIENTE).isEmpty();
		java.util.Optional<SolicitudCambio> aprobada = enExcepcion
				? pendientes.findFirstByTipoAndEntidadAndEntidadIdAndEstadoOrderByIdDesc(TipoSolicitud.DEVOLVER_INGRESO,
						ENTIDAD, lineaId, EstadoSolicitud.APROBADA)
				: java.util.Optional.empty();
		boolean devolucionAprobada = aprobada.isPresent();
		String destinoDevolucion = aprobada.map(s -> DatosSolicitud.leer(s.getDatos()))
				.filter(d -> d.get(DATO_CUENTA) != null)
				.map(d -> d.get(DATO_BANCO) + " " + d.get(DATO_CUENTA) + " de " + d.get(DATO_TITULAR)).orElse(null);
		String comprobante = pagos.findByLineaRecaudacionId(lineaId).map(p -> p.getComprobante().numeroCompleto())
				.orElse(null);
		MotivoExcepcion motivo = linea.getMotivoExcepcion();
		return new LineaExcepcionVista(linea.getId(), linea.getLote().getId(), linea.getLote().getEstado().name(),
				linea.getNumero(), linea.getFechaPago(), CodigoPago.legible(linea.getCodigo()),
				linea.getAlumno() == null ? null : linea.getAlumno().nombreCompleto(),
				origen == null ? null : origen.getNombre(), linea.getMonto(), linea.getMoneda(), linea.getNumeroOperacion(),
				linea.getEstado().name(), linea.getEstado().etiqueta(), linea.getEstado().variante(),
				motivo == null ? null : motivo.descripcion(), linea.getDetalle(), motivo != null && motivo.critico(),
				origen == null ? null : contactos(origen.getId()), destino == null ? null : destino.getId(),
				destino == null ? null : destino.getNombre(),
				corregido == null ? null : CodigoPago.deAlumno(corregido.getId()), porPagar,
				solicitudes.pendientesDe(ENTIDAD, lineaId), enExcepcion && sinPendiente && !devolucionAprobada,
				enExcepcion && linea.enPesos() && !pagos.existsByOperacionVigente(linea.getNumeroOperacion()),
				devolucionAprobada,
				linea.getDevolucionOperacion() == null ? null : "Transferencia " + linea.getDevolucionOperacion() + " a "
						+ linea.getDevolucionBanco() + " " + linea.getDevolucionCuenta() + " de " + linea.getDevolucionTitular()
						+ " por " + linea.getDevueltoPor(),
				comprobante, destinoDevolucion);
	}

	/** Pide aplicar la línea a esas cuotas (deben sumar al menos el pago; si suman más, queda a cuenta). */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void solicitarAplicacion(Long lineaId, AplicacionLineaRequest pedido) {
		LineaRecaudacion linea = enExcepcion(lineaId);
		String motivo = Motivo.exigir(pedido.motivo());
		if (!linea.enPesos()) {
			throw new ReglaNegocioException("El banco recibió el pago en " + linea.getMoneda() + ": no se aplica a cuotas en "
					+ "soles, solo se devuelve.");
		}
		if (pagos.existsByOperacionVigente(linea.getNumeroOperacion())) {
			throw new ReglaNegocioException("La operación " + linea.getNumeroOperacion() + " ya está en otro pago vigente: "
					+ "no se aplica dos veces. Revisa ese pago con el banco o pide la devolución.");
		}
		Familia destino = familias.findById(pedido.familiaId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Familia no encontrada"));
		List<Cuota> elegidas = cuotasDestino(destino.getId(), pedido.cuotaIds());
		BigDecimal debe = Dinero.sumar(elegidas.stream().map(Cuota::saldo).toList());
		if (debe.compareTo(linea.getMonto()) < 0) {
			throw new ReglaNegocioException("Las cuotas elegidas suman " + Dinero.formatear(debe) + " y el pago es de "
					+ Dinero.formatear(linea.getMonto()) + ": elige cuotas que sumen al menos el pago o pide devolverlo.");
		}
		Long origen = linea.getAlumno() == null ? null : linea.getAlumno().getFamilia().getId();
		boolean otraFamilia = !Objects.equals(destino.getId(), origen);
		String resumen = "Aplicar el pago por banco del " + linea.getFechaPago() + " por "
				+ Dinero.formatear(linea.getMonto()) + " (código " + CodigoPago.legible(linea.getCodigo()) + ") a "
				+ (otraFamilia ? "OTRA familia (" + destino.getNombre() + ")" : "cuotas de " + destino.getNombre()) + ": "
				+ String.join("; ", elegidas.stream().map(c -> c.getDescripcion() + " de " + c.getAlumno().nombreCompleto())
						.toList());
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put(DATO_FAMILIA, destino.getId().toString());
		datos.put(DATO_CUOTAS, elegidas.stream().map(c -> c.getId().toString()).collect(Collectors.joining(",")));
		exigirSinOtraPendiente(lineaId);
		solicitudes.crear(TipoSolicitud.APLICAR_INGRESO, ENTIDAD, lineaId, recortar(resumen), datos, motivo);
		auditoria.registrar(AccionAuditoria.INGRESO_APLICACION_SOLICITADA, ENTIDAD, lineaId.toString(),
				EstadoLinea.EXCEPCION.name(), "Aplicación pendiente de aprobación", resumen + ". Motivo: " + motivo);
	}

	/**
	 * Pide devolver el pago por transferencia a quien lo hizo, A UNA CUENTA DE DESTINO que queda en la solicitud (banco,
	 * número y titular: S4-A4) y que ve quien aprueba. La aprueba otra persona y la ejecuta Administración, nunca quien la
	 * pidió ni quien la aprobó.
	 */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void solicitarDevolucion(Long lineaId, DevolucionLineaRequest pedido) {
		LineaRecaudacion linea = enExcepcion(lineaId);
		String texto = Motivo.exigir(pedido == null ? null : pedido.motivo());
		String banco = TextoSeguro.exigir(Normalizador.limpiar(pedido.banco()), "el banco de destino");
		String cuenta = Normalizador.sinEspacios(pedido.cuenta());
		if (cuenta == null || !cuenta.matches("[0-9-]{6,30}")) {
			throw new ReglaNegocioException("Escribe la cuenta de destino (o el CCI) con dígitos y guiones.");
		}
		String titular = TextoSeguro.exigir(Normalizador.limpiar(pedido.titular()), "el titular de la cuenta de destino");
		if (banco.length() > 20 || titular.length() < 5 || titular.length() > 120) {
			throw new ReglaNegocioException("Revisa el banco (hasta 20 caracteres) y el titular (de 5 a 120).");
		}
		exigirSinOtraPendiente(lineaId);
		String resumen = "Devolver el pago por banco del " + linea.getFechaPago() + " por " + linea.getMoneda() + " "
				+ linea.getMonto().toPlainString() + " (código " + CodigoPago.legible(linea.getCodigo()) + ", operación "
				+ linea.getNumeroOperacion() + ") a la cuenta " + banco + " " + cuenta + " de " + titular;
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put(DATO_BANCO, banco);
		datos.put(DATO_CUENTA, cuenta);
		datos.put(DATO_TITULAR, titular);
		solicitudes.crear(TipoSolicitud.DEVOLVER_INGRESO, ENTIDAD, lineaId, recortar(resumen), datos, texto);
		auditoria.registrar(AccionAuditoria.INGRESO_DEVOLUCION_SOLICITADA, ENTIDAD, lineaId.toString(),
				EstadoLinea.EXCEPCION.name(), "Devolución pendiente de aprobación", resumen + ". Motivo: " + texto);
	}

	/**
	 * Registra la devolución YA aprobada: el número de la transferencia con que se devolvió a la cuenta aprobada. La
	 * registra Administración y nunca quien la pidió ni quien la aprobó (S4-A4; también lo exige el trigger). Queda como
	 * un cargo que la conciliación espera ver en el extracto.
	 */
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void registrarDevolucion(Long lineaId, String numeroOperacion) {
		LineaRecaudacion linea = enExcepcion(lineaId);
		SolicitudCambio aprobada = pendientes.findFirstByTipoAndEntidadAndEntidadIdAndEstadoOrderByIdDesc(
				TipoSolicitud.DEVOLVER_INGRESO, ENTIDAD, lineaId, EstadoSolicitud.APROBADA)
				.orElseThrow(() -> new ReglaNegocioException("La devolución todavía no está aprobada por Promotoría o "
						+ "Dirección."));
		String usuario = SecurityContextHolder.getContext().getAuthentication().getName();
		if (usuario.equals(aprobada.getResueltoPor())) {
			throw new ReglaNegocioException("Aprobaste esta devolución: la registra otra persona de Administración.");
		}
		if (usuario.equals(aprobada.getSolicitadoPor())) {
			throw new ReglaNegocioException("Pediste esta devolución: la ejecuta otra persona de Administración.");
		}
		Map<String, String> datos = DatosSolicitud.leer(aprobada.getDatos());
		String banco = datos.get(DATO_BANCO);
		String cuenta = datos.get(DATO_CUENTA);
		String titular = datos.get(DATO_TITULAR);
		if (banco == null || cuenta == null || titular == null) {
			throw new ReglaNegocioException("La solicitud aprobada no tiene la cuenta de destino: pide la devolución otra vez.");
		}
		String operacion = NumeroOperacion.normalizar(numeroOperacion);
		linea.marcarDevuelta(operacion, banco, cuenta, titular, usuario,
				LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		auditoria.registrar(AccionAuditoria.INGRESO_DEVUELTO, ENTIDAD, lineaId.toString(), EstadoLinea.EXCEPCION.name(),
				EstadoLinea.DEVUELTA.name(), "Se devolvió el pago por banco de la línea " + linea.getNumero() + " del lote "
						+ linea.getLote().getId() + " por " + linea.getMoneda() + " " + linea.getMonto().toPlainString()
						+ " con la transferencia " + operacion + " a la cuenta " + banco + " " + cuenta + " de " + titular
						+ ". Pedido por " + aprobada.getSolicitadoPor() + ", aprobado por " + aprobada.getResueltoPor()
						+ ". La conciliación espera ver ese cargo en el extracto.");
	}

	private LineaRecaudacion enExcepcion(Long lineaId) {
		LineaRecaudacion linea = lineas.bloquear(lineaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Línea no encontrada"));
		if (linea.getEstado() != EstadoLinea.EXCEPCION) {
			throw new ReglaNegocioException("Este pago por banco no está por revisar (está "
					+ linea.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT) + ").");
		}
		return linea;
	}

	private void exigirSinOtraPendiente(Long lineaId) {
		if (!pendientes.findByEntidadAndEntidadIdAndEstadoOrderByIdAsc(ENTIDAD, lineaId, EstadoSolicitud.PENDIENTE)
				.isEmpty()) {
			throw new ReglaNegocioException("Ya hay una solicitud pendiente para este pago: espera a que Promotoría o "
					+ "Dirección la resuelva.");
		}
	}

	private List<Cuota> cuotasDestino(Long familiaId, List<Long> ids) {
		List<Long> orden = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
		List<Cuota> elegidas = orden.stream().map(id -> cuotas.findById(id).orElse(null)).filter(Objects::nonNull).toList();
		if (orden.isEmpty() || elegidas.size() != orden.size()
				|| elegidas.stream().anyMatch(c -> !Objects.equals(c.getAlumno().getFamilia().getId(), familiaId))) {
			throw new ReglaNegocioException("Elige cuotas por pagar de la familia de destino.");
		}
		for (Cuota c : elegidas) {
			if (!c.admiteCobro() || c.anulacionPendiente() || c.saldo().signum() <= 0) {
				throw new ReglaNegocioException("La cuota «" + c.getDescripcion() + "» de " + c.getAlumno().nombreCompleto()
						+ " ya no está por pagar.");
			}
		}
		return elegidas;
	}

	/** Celulares de los apoderados de la familia (para llamar antes de decidir). */
	String contactos(Long familiaId) {
		List<String> lista = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familiaId).stream()
				.filter(a -> a.getTelefonoWhatsapp() != null)
				.map(a -> a.nombreCompleto() + ": " + Telefono.formatear(a.getTelefonoWhatsapp())).toList();
		return lista.isEmpty() ? null : String.join(" · ", lista);
	}

	/** Las cuotas destino guardadas en la solicitud. */
	static List<Long> cuotasDe(Map<String, String> datos) {
		return Arrays.stream(datos.getOrDefault(DATO_CUOTAS, "").split(",")).filter(s -> !s.isBlank()).map(Long::valueOf)
				.toList();
	}

	private static String recortar(String texto) {
		return texto.length() <= 480 ? texto : texto.substring(0, 479) + "…";
	}
}
