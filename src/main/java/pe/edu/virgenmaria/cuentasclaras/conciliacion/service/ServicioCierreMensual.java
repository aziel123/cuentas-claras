package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.CierreMensualVista;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CierreMensualBanco;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CuentaBancaria;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoCierreMensual;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ExtractoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.CierreMensualBancoRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.CuentaBancariaRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.ExtractoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.MovimientoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ControlParticipantes;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Cierre bancario mensual a ciegas (sprint 5, tanda 3; decisión 60 y G22): cierra los riesgos residuales del sprint 4
 * («extracto de varios días», «cargos de montos distintos» y, en parte, la colusión).
 * <ol>
 *   <li>{@link #crearPendientes} ({@code sistema.conciliacion}, cada mañana): cuando los extractos CONFIRMADOS cubren
 *       todo el mes anterior, crea su cierre con los totales de abonos y cargos y el saldo al cierre del último día.</li>
 *   <li>{@link #registrar} (Promotoría o Dirección): escribe A CIEGAS los tres números del estado de cuenta OFICIAL del
 *       banco. No puede hacerlo quien subió o confirmó extractos de ese mes (ni quien preparó su cuenta): el trigger lo
 *       exige también. Dos intentos que no coinciden dejan el cierre en DISCREPANCIA (alerta CRÍTICA).</li>
 * </ol>
 * Mientras está ABIERTO, ni la pantalla ni la bitácora muestran los totales calculados.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class ServicioCierreMensual {

	private final CierreMensualBancoRepository cierres;

	private final CuentaBancariaRepository cuentas;

	private final ExtractoBancarioRepository extractos;

	private final MovimientoBancarioRepository movimientos;

	private final ControlParticipantes participantes;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	/** Sprint 7, tanda 2: la firma de la sesión de quien resuelve (sección 3.4). */
	private final FirmaSesion firmaSesion;

	public ServicioCierreMensual(CierreMensualBancoRepository cierres, CuentaBancariaRepository cuentas,
			ExtractoBancarioRepository extractos, MovimientoBancarioRepository movimientos,
			ControlParticipantes participantes, AuditoriaService auditoria, Clock reloj, FirmaSesion firmaSesion) {
		this.firmaSesion = firmaSesion;
		this.cierres = cierres;
		this.cuentas = cuentas;
		this.extractos = extractos;
		this.movimientos = movimientos;
		this.participantes = participantes;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** Los totales del mes según los extractos confirmados, o vacío si no cubren todo el mes. */
	public record Totales(BigDecimal abonos, BigDecimal cargos, BigDecimal saldo) {
	}

	/**
	 * Crea el cierre del mes anterior de cada cuenta activa cuyos extractos confirmados cubren el mes completo.
	 *
	 * @return cuántos cierres creó
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	@PreAuthorize("hasRole('SISTEMA_CONCILIACION')")
	public int crearPendientes(LocalDate hoy) {
		YearMonth periodo = YearMonth.from(hoy).minusMonths(1);
		if (periodo.getYear() < 2026) {
			return 0;
		}
		int creados = 0;
		for (CuentaBancaria cuenta : cuentas.findByActivaTrueOrderByIdAsc()) {
			if (cierres.existsByCuentaIdAndAnioAndMes(cuenta.getId(), periodo.getYear(), periodo.getMonthValue())) {
				continue;
			}
			Optional<Totales> totales = totales(cuenta.getId(), periodo);
			if (totales.isEmpty()) {
				continue; // AlertasCierreMensual avisa «mes sin extractos completos»
			}
			CierreMensualBanco cierre = cierres.saveAndFlush(CierreMensualBanco.nuevo(cuenta, periodo,
					totales.get().abonos(), totales.get().cargos(), totales.get().saldo()));
			// Sin los totales: Promotoría y Dirección leen la bitácora y son quienes escriben a ciegas.
			auditoria.registrar(AccionAuditoria.CIERRE_MENSUAL_CREADO, "cierre_mensual_banco", cierre.getId().toString(),
					null, EstadoCierreMensual.ABIERTO.name(), "Cierre de " + nombre(periodo) + " de " + cuenta.descripcion()
							+ ": los extractos confirmados cubren el mes. Promotoría o Dirección (quien no subió ni confirmó "
							+ "extractos del mes) escribe a ciegas los totales del estado de cuenta oficial.");
			creados++;
		}
		return creados;
	}

	/** Los totales de un mes desde los extractos CONFIRMADOS (vacío si no cubren el primer y el último día). */
	@Transactional(readOnly = true)
	@PreAuthorize("hasRole('SISTEMA_CONCILIACION')")
	public Optional<Totales> totales(Long cuentaId, YearMonth periodo) {
		LocalDate desde = periodo.atDay(1);
		LocalDate hasta = periodo.atEndOfMonth();
		if (extractos.findFirstByCuentaIdAndEstadoAndDesdeLessThanEqualAndHastaGreaterThanEqualOrderBySecuenciaAsc(
				cuentaId, EstadoExtracto.CONFIRMADO, desde, desde).isEmpty()) {
			return Optional.empty();
		}
		Optional<ExtractoBancario> ultimo = extractos
				.findFirstByCuentaIdAndEstadoAndDesdeLessThanEqualAndHastaGreaterThanEqualOrderBySecuenciaAsc(cuentaId,
						EstadoExtracto.CONFIRMADO, hasta, hasta);
		if (ultimo.isEmpty()) {
			return Optional.empty();
		}
		BigDecimal abonos = Dinero.normalizar(movimientos.sumaConfirmada(cuentaId, TipoMovimiento.ABONO, desde, hasta));
		BigDecimal cargos = Dinero.normalizar(movimientos.sumaConfirmada(cuentaId, TipoMovimiento.CARGO, desde, hasta));
		ExtractoBancario e = ultimo.get();
		BigDecimal saldo = Dinero.normalizar(e.getSaldoInicial()
				.add(movimientos.sumaDelExtractoHasta(e.getId(), TipoMovimiento.ABONO, hasta))
				.subtract(movimientos.sumaDelExtractoHasta(e.getId(), TipoMovimiento.CARGO, hasta)));
		return Optional.of(new Totales(abonos, cargos, saldo));
	}

	@Transactional(readOnly = true)
	public List<CierreMensualVista> lista() {
		return cierres.findTop24ByOrderByAnioDescMesDescIdDesc().stream().map(this::vista).toList();
	}

	@Transactional(readOnly = true)
	public CierreMensualVista vista(Long id) {
		return vista(cierres.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cierre no encontrado")));
	}

	private CierreMensualVista vista(CierreMensualBanco c) {
		boolean abierto = c.getEstado() == EstadoCierreMensual.ABIERTO;
		boolean participo = abierto && participantes(c).contains(usuario());
		return new CierreMensualVista(c.getId(), c.getVersion(), c.getCuenta().descripcion(), nombre(c.periodo()),
				c.getEstado().etiqueta(), c.getEstado().variante(), abierto, c.intentosRestantes(), abierto && !participo,
				participo ? "Subiste o confirmaste extractos de este mes: el cierre lo hace otra persona de Promotoría o "
						+ "Dirección." : null,
				abierto ? null : c.getTotalAbonos(), abierto ? null : c.getTotalCargos(),
				abierto ? null : c.getSaldoFinal(), c.getRegistradoPor());
	}

	/**
	 * Escribe a ciegas los tres números del estado de cuenta oficial del mes. Un intento que no coincide se guarda (con
	 * quién lo escribió) y suma; al segundo, DISCREPANCIA.
	 */
	@Transactional(noRollbackFor = { CierreNoCoincideException.class, AutoaprobacionException.class })
	public EstadoCierreMensual registrar(Long id, Long version, BigDecimal abonos, BigDecimal cargos, BigDecimal saldo) {
		CierreMensualBanco cierre = cierres.bloquear(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Cierre no encontrado"));
		if (cierre.getEstado() != EstadoCierreMensual.ABIERTO) {
			throw new ReglaNegocioException("Este cierre ya está resuelto (" + cierre.getEstado().etiqueta() + ").");
		}
		if (version == null || !version.equals(cierre.getVersion())) {
			throw new ReglaNegocioException("El cierre cambió desde que abriste la pantalla (alguien escribió un intento). "
					+ "Ábrela de nuevo.");
		}
		String usuario = usuario();
		if (participantes(cierre).contains(usuario)) {
			auditoria.registrar(AccionAuditoria.AUTOAPROBACION_RECHAZADA, "cierre_mensual_banco", id.toString(), null,
					"Cierre de " + nombre(cierre.periodo()), "Intentó hacer el cierre mensual de un mes en el que subió o "
							+ "confirmó extractos (o preparó la cuenta de quien lo hizo). Se rechazó.");
			throw new AutoaprobacionException("Subiste o confirmaste extractos de este mes: el cierre lo hace otra persona "
					+ "de Promotoría o Dirección, con el estado de cuenta oficial.");
		}
		if (abonos == null || cargos == null || saldo == null) {
			throw new ReglaNegocioException("Escribe los tres números del estado de cuenta oficial: total de abonos, total "
					+ "de cargos y saldo final.");
		}
		BigDecimal a = Dinero.normalizar(abonos);
		BigDecimal c = Dinero.normalizar(cargos);
		BigDecimal s = Dinero.normalizar(saldo);
		if (a.signum() < 0 || c.signum() < 0) {
			throw new ReglaNegocioException("Los totales de abonos y de cargos se escriben en positivo.");
		}
		firmaSesion.firmar(ClaveFirma.cierreMensual(cierre.getId(), cierre.getIntentos() + 1));
		EstadoCierreMensual estado = cierre.intentar(a, c, s, usuario,
				LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		cierres.saveAndFlush(cierre);
		String periodo = nombre(cierre.periodo());
		String escrito = "Abonos " + Dinero.formatear(a) + " · cargos " + Dinero.formatear(c) + " · saldo "
				+ Dinero.formatear(s);
		switch (estado) {
			case CUADRADO -> auditoria.registrar(AccionAuditoria.CIERRE_MENSUAL_CUADRADO, "cierre_mensual_banco",
					id.toString(), EstadoCierreMensual.ABIERTO.name(), EstadoCierreMensual.CUADRADO.name(), "Cierre de "
							+ periodo + " de " + cierre.getCuenta().descripcion() + ": " + usuario + " escribió a ciegas el "
							+ "estado de cuenta oficial y coincide con los extractos confirmados. " + escrito + ".");
			case DISCREPANCIA -> {
				auditoria.registrar(AccionAuditoria.CIERRE_MENSUAL_NO_COINCIDE, "cierre_mensual_banco", id.toString(), null,
						escrito, "Intento " + cierre.getIntentos() + " de " + CierreMensualBanco.INTENTOS_MAXIMOS + " de "
								+ usuario + " para " + periodo + ".");
				auditoria.registrar(AccionAuditoria.CIERRE_MENSUAL_DISCREPANCIA, "cierre_mensual_banco", id.toString(),
						EstadoCierreMensual.ABIERTO.name(), EstadoCierreMensual.DISCREPANCIA.name(), "El estado de cuenta "
								+ "oficial de " + periodo + " no coincide con los extractos confirmados (" + escrito
								+ "; según los extractos: abonos " + Dinero.formatear(cierre.getTotalAbonos()) + ", cargos "
								+ Dinero.formatear(cierre.getTotalCargos()) + ", saldo "
								+ Dinero.formatear(cierre.getSaldoFinal()) + "). Revísalo con el contador: puede haber un "
								+ "abono o un cargo que no está en los extractos.");
				throw new CierreNoCoincideException("No coincide otra vez. El cierre de " + periodo + " quedó en "
						+ "DISCREPANCIA y Promotoría recibe una alerta crítica. Revísalo con el contador.");
			}
			default -> {
				auditoria.registrar(AccionAuditoria.CIERRE_MENSUAL_NO_COINCIDE, "cierre_mensual_banco", id.toString(), null,
						escrito, "Intento " + cierre.getIntentos() + " de " + CierreMensualBanco.INTENTOS_MAXIMOS + " de "
								+ usuario + " para " + periodo + ".");
				throw new CierreNoCoincideException("No coincide. Revisa los tres números en el estado de cuenta oficial de "
						+ periodo + ". Te queda 1 intento.");
			}
		}
		return estado;
	}

	/** Quienes subieron o confirmaron extractos que tocan el mes, y quienes prepararon sus cuentas. */
	private Set<String> participantes(CierreMensualBanco c) {
		Set<String> autores = new HashSet<>();
		for (ExtractoBancario e : extractos.findByCuentaIdAndDesdeLessThanEqualAndHastaGreaterThanEqual(
				c.getCuenta().getId(), c.periodo().atEndOfMonth(), c.periodo().atDay(1))) {
			autores.add(e.getCreadoPor());
			if (e.getConfirmadoPor() != null) {
				autores.add(e.getConfirmadoPor());
			}
		}
		return participantes.ampliar(autores);
	}

	static String nombre(YearMonth periodo) {
		return Calendario.nombreMes(periodo.getMonthValue()) + " de " + periodo.getYear();
	}

	private static String usuario() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	/** Lo escrito a ciegas no coincide con el mes de los extractos. */
	public static class CierreNoCoincideException extends ReglaNegocioException {

		public CierreNoCoincideException(String mensaje) {
			super(mensaje);
		}
	}
}
