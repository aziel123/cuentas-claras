package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.config.PropiedadesCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.DepositoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.EstadoCierreVista;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReconteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ConteoEfectivo;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Denominacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.DepositoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoConteo;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResumenCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CierreCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.DepositoCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cierre de caja de la cajera: conteo a ciegas con un solo reconteo, depósito del efectivo y pedido de reapertura.
 * <ul>
 *   <li>Se cierra siempre la caja abierta MÁS ANTIGUA de la cajera: si quedó abierta la de ayer, esa primero (y hasta
 *       cerrarla no se cobra hoy). Esa es la salida de una caja olvidada: cerrarla con su conteo.</li>
 *   <li>El esperado (fondo + efectivo VIGENTE) lo calcula {@link LibroCaja}; la cajera no lo ve hasta haber contado.
 *       Primer conteo igual al esperado: cierra. Si no, «vuelve a contar» SIN montos; el reconteo exige explicación y
 *       siempre cierra. El primer conteo queda guardado: no se puede tantear el esperado.</li>
 *   <li>Todo cierre crea una solicitud {@code CIERRE_CAJA} que aprueba otra persona de Promotoría o Dirección (con un
 *       clic si cuadró). Con diferencia (faltante o sobrante) queda resaltado y Promotoría lo ve en su inicio.</li>
 * </ul>
 * Orden de escrituras (los triggers de MySQL lo exigen): conteo en la caja con flush → INSERT del cierre → caja
 * CERRADA con flush → solicitud y auditoría.
 */
@Service
@PreAuthorize("hasRole('CAJA')")
public class ServicioCierreCaja {

	private final CajaDiariaRepository cajas;

	private final CierreCajaRepository cierres;

	private final DepositoCajaRepository depositos;

	private final LibroCaja libro;

	private final RegistroSolicitudes solicitudes;

	private final AuditoriaService auditoria;

	private final NombresUsuarios nombres;

	private final PropiedadesCaja propiedades;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public ServicioCierreCaja(CajaDiariaRepository cajas, CierreCajaRepository cierres, DepositoCajaRepository depositos,
			LibroCaja libro, RegistroSolicitudes solicitudes, AuditoriaService auditoria, NombresUsuarios nombres,
			PropiedadesCaja propiedades, ApplicationEventPublisher eventos, Clock reloj) {
		this.cajas = cajas;
		this.cierres = cierres;
		this.depositos = depositos;
		this.libro = libro;
		this.solicitudes = solicitudes;
		this.auditoria = auditoria;
		this.nombres = nombres;
		this.propiedades = propiedades;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	// ------------------------------------------------------------------ pantalla

	/** El paso en que está la cajera. Mientras haya caja por cerrar, ningún monto de esa caja. */
	@Transactional(readOnly = true)
	public EstadoCierreVista estado() {
		String cajera = SesionCaja.usuario();
		LocalDate hoy = hoy();
		EstadoCierreVista.CajaPorCerrar porCerrar = idPorCerrar(cajera, hoy).flatMap(cajas::findById)
				.map(c -> new EstadoCierreVista.CajaPorCerrar(c.getId(), c.getFecha(), c.getFecha().isBefore(hoy),
						c.getFondoFijo(), c.esperaReconteo()))
				.orElse(null);
		EstadoCierreVista.CierreHecho ultimo = null;
		EstadoCierreVista.ReaperturaVista reapertura = new EstadoCierreVista.ReaperturaVista(false, false);
		if (porCerrar == null) {
			CajaDiaria reciente = cajas.findTop5ByCajeroOrderByFechaDesc(cajera).stream().findFirst().orElse(null);
			if (reciente != null) {
				ultimo = cierres.findFirstByCajaIdOrderByNumeroDesc(reciente.getId()).map(this::cierreHecho).orElse(null);
				boolean deHoy = reciente.getFecha().equals(hoy) && reciente.getEstado() == EstadoCaja.CERRADA;
				boolean pendiente = !solicitudes.pendientesDe("caja_diaria", reciente.getId()).isEmpty();
				reapertura = new EstadoCierreVista.ReaperturaVista(
						deHoy && !pendiente && !depositos.existsByCajaId(reciente.getId()), deHoy && pendiente);
			}
		}
		List<EstadoCierreVista.CajaPorDepositar> porDepositar = cajas.cerradasSinDepositoDe(cajera).stream()
				.map(c -> new EstadoCierreVista.CajaPorDepositar(c.getId(), c.getFecha(), aDepositar(c)))
				.filter(c -> c.esperado().signum() > 0)
				.toList();
		return new EstadoCierreVista(nombres.de(cajera), hoy, porCerrar, ultimo, porDepositar, cuentas(), reapertura,
				Arrays.asList(Denominacion.values()));
	}

	// ------------------------------------------------------------------ conteo a ciegas

	/**
	 * Primer conteo, sin ver el esperado. Si coincide, cierra; si no, pide recontar (sin montos) y deja guardado este
	 * primer conteo (la transacción se confirma: no se puede volver a intentar).
	 */
	@Transactional
	public ResultadoConteo contar(ConteoRequest pedido) {
		String cajera = SesionCaja.usuario();
		CajaDiaria caja = cajaPorCerrar(cajera);
		if (caja.getConteos() > 0) {
			throw new ReglaNegocioException("Ya registraste tu primer conteo: vuelve a contar y explica qué pasó.");
		}
		ConteoEfectivo conteo = ConteoEfectivo.de(pedido.contado(), pedido.denominaciones());
		ResumenCaja resumen = libro.resumen(caja);
		ResultadoConteo resultado = caja.registrarConteo(conteo.total(), resumen.esperado());
		cajas.saveAndFlush(caja);
		if (resultado == ResultadoConteo.COINCIDE) {
			cerrar(caja, resumen, conteo.total(), conteo, null);
		}
		else {
			auditoria.registrar(AccionAuditoria.CAJA_CONTEO_NO_COINCIDE, "caja_diaria", caja.getId().toString(), null,
					"Primer conteo " + Dinero.formatear(conteo.total()), "Caja de " + cajera + " del "
							+ Calendario.formatear(caja.getFecha()) + ": el primer conteo (" + Dinero.formatear(conteo.total())
							+ ") no coincidió con el esperado (" + Dinero.formatear(resumen.esperado())
							+ "). Se le pidió recontar sin mostrarle montos.");
		}
		return resultado;
	}

	/** El único reconteo: con explicación obligatoria. Siempre cierra (con la diferencia que resulte). */
	@Transactional
	public ResultadoConteo recontar(ReconteoRequest pedido) {
		String cajera = SesionCaja.usuario();
		CajaDiaria caja = cajaPorCerrar(cajera);
		if (!caja.esperaReconteo()) {
			throw new ReglaNegocioException("Primero registra tu conteo.");
		}
		String explicacion = Motivo.exigir(pedido.explicacion());
		ConteoEfectivo conteo = ConteoEfectivo.de(pedido.contado(), pedido.denominaciones());
		ResumenCaja resumen = libro.resumen(caja);
		ResultadoConteo resultado = caja.registrarConteo(conteo.total(), resumen.esperado());
		cajas.saveAndFlush(caja);
		cerrar(caja, resumen, caja.getPrimerConteo(), conteo, explicacion);
		return resultado;
	}

	private void cerrar(CajaDiaria caja, ResumenCaja resumen, BigDecimal primerConteo, ConteoEfectivo conteo,
			String explicacion) {
		CierreCaja cierre = cierres.save(CierreCaja.registrar(caja, resumen, primerConteo, conteo.total(),
				conteo.denominaciones(), explicacion));
		caja.cerrar(cierre);
		cajas.saveAndFlush(caja);
		String fecha = Calendario.formatear(caja.getFecha());
		String diferencia = diferenciaTexto(cierre.getDiferencia());
		String resumenSolicitud = "Cierre de la caja de " + nombres.de(caja.getCajero()) + " del " + fecha + ": "
				+ diferencia + " (esperado " + Dinero.formatear(cierre.getEsperado()) + ", contado "
				+ Dinero.formatear(cierre.getContado()) + ")";
		solicitudes.crear(TipoSolicitud.CIERRE_CAJA, "cierre_caja", cierre.getId(), resumenSolicitud,
				Map.of("cajaId", caja.getId().toString()), cierre.getExplicacion() != null ? cierre.getExplicacion()
						: "Cierre de caja del " + fecha + " sin diferencia al primer conteo.");
		String detalle = "Caja de " + caja.getCajero() + " del " + fecha + ", cierre N.° " + cierre.getNumero()
				+ ". Esperado " + Dinero.formatear(cierre.getEsperado()) + " (fondo "
				+ Dinero.formatear(cierre.getFondoFijo()) + " + efectivo " + Dinero.formatear(cierre.getEfectivoCobrado())
				+ " en " + cierre.getPagosEfectivo() + " pago(s)); contado " + Dinero.formatear(cierre.getContado())
				+ (cierre.huboReconteo() ? " al reconteo (primer conteo " + Dinero.formatear(cierre.getPrimerConteo())
						+ ")" : "")
				+ "; " + diferencia + ". Digital (no entra al esperado): " + Dinero.formatear(cierre.getTotalDigital())
				+ " en " + cierre.getPagosDigitales() + " pago(s)."
				+ (cierre.getExplicacion() == null ? "" : " Explicación: " + cierre.getExplicacion());
		auditoria.registrar(cierre.conDiferencia() ? AccionAuditoria.CAJA_CERRADA_CON_DIFERENCIA
				: AccionAuditoria.CAJA_CERRADA, "caja_diaria", caja.getId().toString(), EstadoCaja.ABIERTA.name(),
				EstadoCaja.CERRADA.name() + " · " + diferencia, detalle);
		if (cierre.conDiferencia()) {
			eventos.publishEvent(new CierreConDiferencia(cierre.getId()));
		}
	}

	// ------------------------------------------------------------------ depósito

	/**
	 * Registra el depósito del efectivo de una caja cerrada de la cajera (uno por caja). Lo esperado es lo contado en el
	 * último cierre menos el fondo fijo; si deposita otro monto, explica por qué y Promotoría recibe la alerta.
	 */
	@Transactional
	public Long registrarDeposito(DepositoRequest pedido) {
		String cajera = SesionCaja.usuario();
		CajaDiaria caja = cajas.bloquearPorId(pedido.cajaId()).filter(c -> c.getCajero().equals(cajera))
				.orElseThrow(() -> new RecursoNoEncontradoException("Caja no encontrada"));
		if (caja.getEstado() != EstadoCaja.CERRADA) {
			throw new ReglaNegocioException("Primero cierra la caja: se deposita lo contado en el cierre.");
		}
		if (depositos.existsByCajaId(caja.getId())) {
			throw new ReglaNegocioException("El depósito de esta caja ya está registrado.");
		}
		String cuenta = pedido.cuenta() == null ? null : pedido.cuenta().strip();
		if (!cuentas().contains(cuenta)) {
			throw new ReglaNegocioException("Elige una de las cuentas del colegio.");
		}
		if (pedido.fecha().isBefore(caja.getFecha()) || pedido.fecha().isAfter(hoy())) {
			throw new ReglaNegocioException("La fecha del depósito debe estar entre el día de la caja y hoy.");
		}
		BigDecimal esperado = aDepositar(caja);
		DepositoCaja deposito = depositos.save(DepositoCaja.registrar(caja, cuenta, pedido.numeroOperacion(),
				pedido.fecha(), pedido.monto(), esperado, pedido.explicacion()));
		String detalle = "Depósito de la caja de " + cajera + " del " + Calendario.formatear(caja.getFecha()) + " en "
				+ cuenta + ", operación " + deposito.getNumeroOperacion() + " del "
				+ Calendario.formatear(deposito.getFechaDeposito()) + ": " + Dinero.formatear(deposito.getMonto())
				+ " (lo contado menos el fondo: " + Dinero.formatear(esperado) + ")."
				+ (deposito.getExplicacion() == null ? "" : " Explicación: " + deposito.getExplicacion());
		auditoria.registrar(deposito.distinto() ? AccionAuditoria.DEPOSITO_DIFERENTE : AccionAuditoria.DEPOSITO_REGISTRADO,
				"deposito_caja", deposito.getId().toString(), null, Dinero.formatear(deposito.getMonto()), detalle);
		return deposito.getId();
	}

	// ------------------------------------------------------------------ reapertura

	/**
	 * Pide reabrir la caja de HOY (cerrada y sin depósito), por ejemplo para cobrar en efectivo a una familia que llegó
	 * tarde. La aprueba otra persona; el cierre anterior queda registrado tal cual (con su diferencia, si la tuvo).
	 */
	@Transactional
	public void solicitarReapertura(String motivo) {
		String cajera = SesionCaja.usuario();
		LocalDate hoy = hoy();
		CajaDiaria caja = cajas.findByCajeroAndFecha(cajera, hoy)
				.orElseThrow(() -> new ReglaNegocioException("Hoy no tienes una caja cerrada para reabrir."));
		if (caja.getEstado() != EstadoCaja.CERRADA) {
			throw new ReglaNegocioException("Tu caja de hoy está abierta: no hace falta reabrirla.");
		}
		if (depositos.existsByCajaId(caja.getId())) {
			throw new ReglaNegocioException("Ya depositaste el efectivo de esta caja: no se puede reabrir.");
		}
		String texto = Motivo.exigir(motivo);
		CierreCaja ultimo = cierres.findFirstByCajaIdOrderByNumeroDesc(caja.getId()).orElseThrow();
		solicitudes.crear(TipoSolicitud.REAPERTURA_CAJA, "caja_diaria", caja.getId(), "Reabrir la caja de "
				+ nombres.de(cajera) + " del " + Calendario.formatear(hoy) + " (su cierre N.° " + ultimo.getNumero() + ": "
				+ diferenciaTexto(ultimo.getDiferencia()) + ")", Map.of("cierreId", ultimo.getId().toString()), texto);
		auditoria.registrar(AccionAuditoria.CAJA_REAPERTURA_SOLICITADA, "caja_diaria", caja.getId().toString(),
				EstadoCaja.CERRADA.name(), null, "Pidió reabrir su caja del " + Calendario.formatear(hoy) + ". Motivo: "
						+ texto);
	}

	// ------------------------------------------------------------------ ayudas

	/** «Cuadró», «faltante de S/ 50.00» o «sobrante de S/ 10.00». */
	static String diferenciaTexto(BigDecimal diferencia) {
		if (diferencia.signum() == 0) {
			return "cuadró";
		}
		return (diferencia.signum() < 0 ? "faltante de " : "sobrante de ") + Dinero.formatear(diferencia.abs());
	}

	private EstadoCierreVista.CierreHecho cierreHecho(CierreCaja c) {
		CajaDiaria caja = c.getCaja();
		String deposito = depositos.findByCajaId(caja.getId())
				.map(d -> "Depositado " + Dinero.formatear(d.getMonto()) + " el " + Calendario.formatear(d.getFechaDeposito())
						+ " (operación " + d.getNumeroOperacion() + ")")
				.orElse(null);
		return new EstadoCierreVista.CierreHecho(caja.getId(), caja.getFecha(), c.getNumero(), c.getEsperado(),
				c.getContado(), c.getPrimerConteo(), c.huboReconteo(), c.getDiferencia(), diferenciaTexto(c.getDiferencia()),
				c.conDiferencia() ? "peligro" : "exito", c.getExplicacion(), c.getEstado().etiqueta(),
				c.getEstado().variante(), c.getRevisadoPor(), c.getComentarioRevision(), deposito);
	}

	/** Lo que se deposita: lo contado en el último cierre menos el fondo fijo (el sencillo se queda). Nunca negativo. */
	private BigDecimal aDepositar(CajaDiaria caja) {
		return cierres.findFirstByCajaIdOrderByNumeroDesc(caja.getId())
				.map(c -> c.getContado().subtract(caja.getFondoFijo()).max(Dinero.CERO)).map(Dinero::normalizar)
				.orElse(Dinero.CERO);
	}

	private CajaDiaria cajaPorCerrar(String cajera) {
		Long id = idPorCerrar(cajera, hoy())
				.orElseThrow(() -> new ReglaNegocioException("No tienes una caja abierta para cerrar."));
		return cajas.bloquearPorId(id).orElseThrow();
	}

	private Optional<Long> idPorCerrar(String cajera, LocalDate hoy) {
		return cajas.abiertasHasta(cajera, hoy).stream().findFirst();
	}

	List<String> cuentas() {
		return Arrays.stream(propiedades.cuentasDeposito().split(";")).map(String::strip).filter(s -> !s.isEmpty())
				.toList();
	}

	private LocalDate hoy() {
		return LocalDate.now(reloj);
	}
}
