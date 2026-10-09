package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.config.PropiedadesCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReembolsoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.VerificacionRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.VistaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AnulacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.DepositoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.NumeroOperacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.OrigenPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Reembolso;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ReembolsoPasarela;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAnulacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.VerificacionBancaria;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AnulacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.DepositoCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.ReembolsoPasarelaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.ReembolsoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.VerificacionBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.DiasHabiles;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Verificación bancaria A CIEGAS y reembolsos de devoluciones (correcciones del sprint 3: C1, A1, A2, A4, M3 y B3).
 * <ul>
 *   <li>Administración marca cada pago digital y cada depósito: para «Encontrado» ESCRIBE la operación, la fecha y el
 *       monto que ve en el banco, sin ver lo registrado; el sistema compara. Si no coincide, no se guarda nada, se
 *       audita resaltado y se responde sin decir qué campo falló. «No aparece» exige una nota (alerta crítica).</li>
 *   <li>Nunca verifica quien cobró o depositó (también lo exige un trigger en MySQL). Solo pagos digitales VIGENTES.</li>
 *   <li>Los reembolsos de devoluciones los registra Administración (nunca la cajera del pago), por el mismo medio del
 *       pago: digital a la cuenta de origen con su número de operación; efectivo contra la firma de quien lo recibe.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioVerificacionBancaria {

	/** Un pago se busca en el banco hasta 3 días después de cobrado (un Yape del viernes puede verse el lunes). */
	static final int DIAS_TOLERANCIA_BANCO = 3;

	/** Ventana para marcar números de operación parecidos. */
	static final int DIAS_PARECIDOS = 90;

	private final PagoRepository pagos;

	private final DepositoCajaRepository depositos;

	private final VerificacionBancariaRepository verificaciones;

	private final AnulacionPagoRepository anulaciones;

	private final ReembolsoRepository reembolsos;

	private final ReembolsoPasarelaRepository reembolsosPasarela;

	private final ReembolsosEnLinea enLinea;

	private final AuditoriaService auditoria;

	private final NombresUsuarios nombres;

	private final PropiedadesCaja propiedades;

	private final Clock reloj;

	private final CalendarioHabil calendario;

	/** Sprint 7, tanda 2: la firma de la sesión de quien resuelve (sección 3.4). */
	private final FirmaSesion firmaSesion;

	public ServicioVerificacionBancaria(PagoRepository pagos, DepositoCajaRepository depositos,
			VerificacionBancariaRepository verificaciones, AnulacionPagoRepository anulaciones,
			ReembolsoRepository reembolsos, ReembolsoPasarelaRepository reembolsosPasarela, ReembolsosEnLinea enLinea,
			AuditoriaService auditoria, NombresUsuarios nombres, PropiedadesCaja propiedades, Clock reloj, CalendarioHabil calendario, FirmaSesion firmaSesion) {
		this.firmaSesion = firmaSesion;
		this.calendario = calendario;
		this.pagos = pagos;
		this.depositos = depositos;
		this.verificaciones = verificaciones;
		this.anulaciones = anulaciones;
		this.reembolsos = reembolsos;
		this.reembolsosPasarela = reembolsosPasarela;
		this.enLinea = enLinea;
		this.auditoria = auditoria;
		this.nombres = nombres;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	public VistaConciliacion vista() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		LocalDate hoy = ahora.toLocalDate();
		List<Pago> digitales = pagos.digitalesDesde(hoy.minusDays(DIAS_PARECIDOS));
		List<VistaConciliacion.PagoPorVerificar> porVerificar = pagos.digitalesSinVerificar().stream().map(p -> {
			String parecido = digitales.stream().filter(o -> !o.getId().equals(p.getId())
					&& NumeroOperacion.parecidos(o.getNumeroOperacion(), p.getNumeroOperacion()))
					.map(o -> o.getComprobante().numeroCompleto() + " (" + (o.vigente() ? "vigente" : "anulado") + ")")
					.findFirst().orElse(null);
			return new VistaConciliacion.PagoPorVerificar(p.getId(), p.getFecha(), p.getMedio().etiqueta(),
					p.getFamilia().getNombre(), nombres.de(p.getCajero()), p.getComprobante().numeroCompleto(),
					ChronoUnit.DAYS.between(p.getFecha(), hoy), sinVerificarDemasiado(p.getCreadoEn(), ahora),
					criticoSinVerificar(p.getFecha(), ahora), parecido);
		}).toList();
		List<DepositoCaja> recientes = depositos.findByFechaDepositoGreaterThanEqual(hoy.minusDays(DIAS_PARECIDOS));
		List<VistaConciliacion.DepositoPorVerificar> depositosPorVerificar = depositos.sinVerificar().stream().map(d -> {
			String parecido = recientes.stream().filter(o -> !o.getId().equals(d.getId())
					&& NumeroOperacion.parecidos(o.getNumeroOperacion(), d.getNumeroOperacion()))
					.map(o -> "el depósito de la caja del " + Calendario.formatear(o.getCaja().getFecha())).findFirst()
					.orElse(null);
			return new VistaConciliacion.DepositoPorVerificar(d.getId(), d.getCaja().getFecha(), d.getCuenta(),
					nombres.de(d.getCaja().getCajero()), ChronoUnit.DAYS.between(d.getFechaDeposito(), hoy),
					sinVerificarDemasiado(d.getCreadoEn(), ahora), tardio(calendario, d.getCaja().getFecha(), d.getFechaDeposito()),
					parecido);
		}).toList();
		List<VistaConciliacion.DevolucionPorReembolsar> devoluciones = anulaciones.devolucionesSinReembolso().stream()
				.map(a -> new VistaConciliacion.DevolucionPorReembolsar(a.getId(), a.getPago().getComprobante().numeroCompleto(),
						a.getNotaCredito().numeroCompleto(), a.getPago().getMedio().etiqueta(),
						a.getPago().getMedio() == MedioPago.EFECTIVO, a.getMonto(), a.getPago().getFamilia().getNombre(),
						nombres.de(a.getCajeroPago()), a.getCreadoEn().toLocalDate(),
						a.getPago().getOrigen() == OrigenPago.PASARELA))
				.toList();
		List<VistaConciliacion.Verificado> hechas = verificaciones.findTop30ByOrderByIdDesc().stream()
				.map(v -> new VistaConciliacion.Verificado(que(v), v.getResultado().etiqueta()
						+ (v.automatica() ? " · " + v.getOrigen().etiqueta().toLowerCase(java.util.Locale.ROOT) : ""),
						v.getResultado().variante(), v.getNota(), v.getCreadoPor(), v.getCreadoEn()))
				.toList();
		return new VistaConciliacion(hoy, esAdministracion(), propiedades.diasSinVerificar(), porVerificar,
				depositosPorVerificar, devoluciones, hechas);
	}

	@Transactional(noRollbackFor = DatosBancoNoCoincidenException.class)
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void verificarPago(Long pagoId, VerificacionRequest pedido) {
		Pago pago = pagos.bloquear(pagoId).orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado"));
		if (verificaciones.existsByPagoId(pago.getId())) {
			throw new ReglaNegocioException("Ese pago ya se verificó.");
		}
		if (!pago.vigente()) {
			throw new ReglaNegocioException("Ese pago está anulado: no se verifica.");
		}
		VerificacionBancaria.DatosBanco banco = null;
		if (pedido.resultado() == ResultadoVerificacion.ENCONTRADO && pago.getMedio().digital()) {
			banco = datosBanco(pedido);
			boolean coincide = banco.operacion().equals(pago.getNumeroOperacion())
					&& Dinero.iguales(banco.monto(), pago.getTotal()) && !banco.fecha().isBefore(pago.getFecha())
					&& !banco.fecha().isAfter(pago.getFecha().plusDays(DIAS_TOLERANCIA_BANCO));
			if (!coincide) {
				noCoincide("pago", pago.getId(), pago.getMedio().etiqueta() + " del " + Calendario.formatear(pago.getFecha())
						+ " (" + pago.getComprobante().numeroCompleto() + ", cobrado por " + pago.getCajero()
						+ ": operación " + pago.getNumeroOperacion() + ", " + Dinero.formatear(pago.getTotal()) + ")", banco);
			}
		}
		firmaSesion.firmar(ClaveFirma.verificacionPago(pago.getId(), verificaciones.countByPagoId(pago.getId()) + 1));
		VerificacionBancaria v = verificaciones.save(VerificacionBancaria.dePago(pago, pedido.resultado(), pedido.nota(),
				banco, SesionCaja.usuario()));
		boolean encontrado = v.getResultado() == ResultadoVerificacion.ENCONTRADO;
		auditoria.registrar(encontrado ? AccionAuditoria.PAGO_VERIFICADO : AccionAuditoria.PAGO_NO_ENCONTRADO_BANCO,
				"pago", pago.getId().toString(), null, v.getResultado().name(), pago.getMedio().etiqueta() + " "
						+ pago.getNumeroOperacion() + " por " + Dinero.formatear(pago.getTotal()) + " del "
						+ Calendario.formatear(pago.getFecha()) + " (" + pago.getComprobante().numeroCompleto()
						+ ", cobrado por " + pago.getCajero() + "): " + v.getResultado().etiqueta().toLowerCase()
						+ " en el banco." + (encontrado ? " En el banco: operación " + v.getBancoOperacion() + " del "
								+ Calendario.formatear(v.getBancoFecha()) + " por " + Dinero.formatear(v.getBancoMonto()) + "."
								: " Nota: " + v.getNota()));
	}

	@Transactional(noRollbackFor = DatosBancoNoCoincidenException.class)
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void verificarDeposito(Long depositoId, VerificacionRequest pedido) {
		DepositoCaja deposito = depositos.findById(depositoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Depósito no encontrado"));
		if (verificaciones.existsByDepositoId(deposito.getId())) {
			throw new ReglaNegocioException("Ese depósito ya se verificó.");
		}
		VerificacionBancaria.DatosBanco banco = null;
		if (pedido.resultado() == ResultadoVerificacion.ENCONTRADO) {
			banco = datosBanco(pedido);
			boolean coincide = banco.operacion().equals(deposito.getNumeroOperacion())
					&& Dinero.iguales(banco.monto(), deposito.getMonto()) && banco.fecha().equals(deposito.getFechaDeposito());
			if (!coincide) {
				noCoincide("deposito_caja", deposito.getId(), "Depósito de la caja de " + deposito.getCaja().getCajero()
						+ " del " + Calendario.formatear(deposito.getCaja().getFecha()) + ": operación "
						+ deposito.getNumeroOperacion() + " del " + Calendario.formatear(deposito.getFechaDeposito())
						+ " por " + Dinero.formatear(deposito.getMonto()), banco);
			}
		}
		firmaSesion.firmar(ClaveFirma.verificacionDeposito(deposito.getId(),
				verificaciones.countByDepositoId(deposito.getId()) + 1));
		VerificacionBancaria v = verificaciones.save(VerificacionBancaria.deDeposito(deposito, pedido.resultado(),
				pedido.nota(), banco, SesionCaja.usuario()));
		boolean encontrado = v.getResultado() == ResultadoVerificacion.ENCONTRADO;
		auditoria.registrar(encontrado ? AccionAuditoria.DEPOSITO_VERIFICADO : AccionAuditoria.DEPOSITO_NO_ENCONTRADO,
				"deposito_caja", deposito.getId().toString(), null, v.getResultado().name(), "Depósito de la caja de "
						+ deposito.getCaja().getCajero() + " del " + Calendario.formatear(deposito.getCaja().getFecha())
						+ " en " + deposito.getCuenta() + ", operación " + deposito.getNumeroOperacion() + " por "
						+ Dinero.formatear(deposito.getMonto()) + ": " + v.getResultado().etiqueta().toLowerCase()
						+ " en el banco." + (encontrado ? "" : " Nota: " + v.getNota()));
	}

	/**
	 * Registra el reembolso de una devolución aprobada (por el mismo medio del pago). Lo hace Administración, nunca la
	 * cajera del pago; hasta entonces Promotoría tiene una alerta crítica.
	 */
	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void registrarReembolso(Long anulacionId, ReembolsoRequest pedido) {
		AnulacionPago anulacion = anulaciones.findById(anulacionId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Devolución no encontrada"));
		if (reembolsos.existsByAnulacionId(anulacion.getId())) {
			throw new ReglaNegocioException("El reembolso de esta devolución ya está registrado.");
		}
		if (anulacion.getPago().getOrigen() == OrigenPago.PASARELA) {
			// S4-A3: un pago en línea solo se devuelve por la API de su pasarela (al mismo medio de origen), nunca con un
			// número de transferencia escrito a mano (también lo rechaza trg_reembolso_registro).
			throw new ReglaNegocioException("Un pago en línea se devuelve solo por la pasarela, al mismo medio de origen: "
					+ "usa «Reembolsar por la pasarela».");
		}
		boolean efectivo = anulacion.getPago().getMedio() == MedioPago.EFECTIVO;
		if (!efectivo && !pedido.aLaCuentaDeOrigen()) {
			throw new ReglaNegocioException("Un pago digital solo se devuelve a la cuenta de origen: confírmalo.");
		}
		Reembolso r = reembolsos.save(Reembolso.registrar(anulacion, pedido.numeroOperacion(), pedido.recibidoPorNombre(),
				pedido.recibidoPorDocumento(), LocalDate.now(reloj), SesionCaja.usuario()));
		auditoria.registrar(AccionAuditoria.REEMBOLSO_REGISTRADO, "reembolso", r.getId().toString(), null,
				Dinero.formatear(r.getMonto()) + " · " + r.getMedio().etiqueta(), "Reembolso de la devolución de "
						+ anulacion.getPago().getComprobante().numeroCompleto() + " (nota de crédito "
						+ anulacion.getNotaCredito().numeroCompleto() + "): " + Dinero.formatear(r.getMonto()) + " "
						+ (efectivo ? "en efectivo, recibido y firmado por " + r.getRecibidoPorNombre() + " (documento "
								+ enmascarar(r.getRecibidoPorDocumento()) + ")"
								: "por " + r.getMedio().etiqueta() + " a la cuenta de origen, operación " + r.getNumeroOperacion())
						+ ".");
	}

	/**
	 * Correcciones del sprint 4 (S4-A3): la devolución aprobada de un pago EN LÍNEA se ejecuta solo con la API de su
	 * pasarela ({@code PasarelaPagos.reembolsar}), que la devuelve al mismo cargo y medio de origen; queda el id del
	 * reembolso. La ejecuta Administración, nunca quien pidió o aprobó la devolución (trigger). Un contracargo no se
	 * reembolsa.
	 */
	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public String reembolsarEnLinea(Long anulacionId) {
		AnulacionPago anulacion = anulaciones.findById(anulacionId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Devolución no encontrada"));
		Pago pago = anulacion.getPago();
		if (pago.getOrigen() != OrigenPago.PASARELA || pago.getOrdenPagoId() == null) {
			throw new ReglaNegocioException("Solo un pago en línea se reembolsa por la pasarela.");
		}
		if (anulacion.getTipo() != TipoAnulacion.DEVOLUCION) {
			throw new ReglaNegocioException(anulacion.getTipo() == TipoAnulacion.CONTRACARGO
					? "Un contracargo no se reembolsa: el banco ya le devolvió el dinero al apoderado."
					: "Solo una devolución se reembolsa.");
		}
		if (reembolsosPasarela.existsByAnulacionId(anulacionId)) {
			throw new ReglaNegocioException("El reembolso de esta devolución ya se hizo.");
		}
		String usuario = SesionCaja.usuario();
		if (usuario.equals(anulacion.getAprobadoPor()) || usuario.equals(anulacion.getSolicitadoPor())) {
			throw new ReglaNegocioException("Quien pidió o aprobó la devolución no la ejecuta: debe hacerlo otra persona de "
					+ "Administración.");
		}
		ReembolsosEnLinea.Reembolsado hecho = enLinea.reembolsar(pago.getOrdenPagoId(), anulacion.getMonto(),
				"Devolución aprobada del pago " + pago.getComprobante().numeroCompleto());
		ReembolsoPasarela r = reembolsosPasarela.save(ReembolsoPasarela.registrar(anulacion, hecho.cargoId(),
				hecho.reembolsoId(), hecho.monto(), LocalDate.now(reloj), usuario));
		auditoria.registrar(AccionAuditoria.REEMBOLSO_REGISTRADO, "reembolso_pasarela", r.getId().toString(), null,
				Dinero.formatear(r.getMonto()) + " · pasarela", "Reembolso por la pasarela de la devolución de "
						+ pago.getComprobante().numeroCompleto() + " (nota de crédito "
						+ anulacion.getNotaCredito().numeroCompleto() + "): " + Dinero.formatear(r.getMonto())
						+ " al mismo medio de origen (" + pago.getMedio().etiqueta() + "), cargo " + r.getCargoId()
						+ ", reembolso " + r.getReembolsoId() + ".");
		return r.getReembolsoId();
	}

	/** Lo escrito por Administración, ya en forma canónica; todo es obligatorio para «Encontrado». */
	private static VerificacionBancaria.DatosBanco datosBanco(VerificacionRequest pedido) {
		if (pedido.operacion() == null || pedido.operacion().isBlank() || pedido.fecha() == null
				|| pedido.monto() == null) {
			throw new ReglaNegocioException("Escribe la operación, la fecha y el monto que ves en el banco.");
		}
		return new VerificacionBancaria.DatosBanco(NumeroOperacion.normalizar(pedido.operacion()), pedido.fecha(),
				Dinero.normalizar(pedido.monto()));
	}

	/** No se guarda la verificación; queda auditado (resaltado) y se responde sin decir qué campo falló. */
	private void noCoincide(String entidad, Long id, String registrado, VerificacionBancaria.DatosBanco banco) {
		auditoria.registrar(AccionAuditoria.VERIFICACION_NO_COINCIDE, entidad, id.toString(), null, null, "Lo escrito "
				+ "del banco (operación " + banco.operacion() + " del " + Calendario.formatear(banco.fecha()) + " por "
				+ Dinero.formatear(banco.monto()) + ") no coincide con lo registrado: " + registrado + ".");
		throw new DatosBancoNoCoincidenException();
	}

	/** Más tiempo que el límite configurado desde que se registró (en horas, no en fechas de calendario). */
	boolean sinVerificarDemasiado(LocalDateTime registrado, LocalDateTime ahora) {
		int dias = propiedades.diasSinVerificar();
		return dias > 0 && !Duration.between(registrado, ahora).minusDays(dias).isNegative();
	}

	/** Pasó la hora límite del día hábil siguiente al cobro (alerta crítica, A4). */
	boolean criticoSinVerificar(LocalDate fecha, LocalDateTime ahora) {
		return !ahora.isBefore(calendario.siguienteDiaHabil(fecha).atTime(propiedades.horaLimiteCierre()));
	}

	/** El depósito llegó al banco más de un día hábil después de la caja (M3: posible «lapping»). */
	static boolean tardio(DiasHabiles calendario, LocalDate fechaCaja, LocalDate fechaDeposito) {
		return fechaDeposito != null && fechaDeposito.isAfter(calendario.siguienteDiaHabil(fechaCaja));
	}

	private static String enmascarar(String documento) {
		return documento.length() <= 4 ? "****" : "****" + documento.substring(documento.length() - 4);
	}

	private static String que(VerificacionBancaria v) {
		if (v.getPago() != null) {
			Pago p = v.getPago();
			return p.getMedio().etiqueta() + " " + p.getNumeroOperacion() + " · " + Dinero.formatear(p.getTotal());
		}
		DepositoCaja d = v.getDeposito();
		return "Depósito " + d.getNumeroOperacion() + " · " + Dinero.formatear(d.getMonto());
	}

	private static boolean esAdministracion() {
		var autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion != null && autenticacion.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.anyMatch("ROLE_ADMINISTRACION"::equals);
	}

	/** Los datos del banco no coinciden con lo registrado (no dice cuál, para no dejar tantear). */
	public static class DatosBancoNoCoincidenException extends ReglaNegocioException {

		public DatosBancoNoCoincidenException() {
			super("Lo que escribiste no coincide con lo registrado (operación, fecha o monto). Revisa el estado de cuenta; "
					+ "si no aparece, márcalo «No aparece». El intento quedó en la bitácora.");
		}
	}
}
