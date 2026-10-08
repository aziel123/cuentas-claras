package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.ConsultaHuellas;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CambiosPosteriores;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoDia;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.ResumenDiarioListo;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.dto.AvisosDelDia;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.dto.EntregaResumen;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ConsultaMensajes;
import pe.edu.virgenmaria.cuentasclaras.panel.config.PropiedadesPanel;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.AvanceLlamadas;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.CifrasResumen;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.ComparacionResumen;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.ResumenVista;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResumenDiario;
import pe.edu.virgenmaria.cuentasclaras.panel.repository.ResumenDiarioRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Resumen diario de Promotoría (sprint 6, tanda 2; decisiones 68, 69 y 81).
 * <ul>
 *   <li>{@link #generar}: {@code sistema.panel} calcula las cifras del día con {@link CifrasDelDia} (las MISMAS del panel),
 *       guarda la foto (en MySQL el trigger la compara con los libros al centavo), publica {@link ResumenDiarioListo} (la
 *       mensajería crea los mensajes en esta misma transacción: sin mensaje no hay foto) y lo deja en la bitácora.</li>
 *   <li>{@link #comparar}: cada foto de los últimos días contra lo que dicen HOY los libros. La diferencia que no explican
 *       las anulaciones aprobadas ni los pagos registrados después del corte es CRÍTICA (P4).</li>
 * </ul>
 * Orden de bloqueos: la foto va ANTES que sus mensajes (trg_mensaje_nace exige que el mensaje apunte a su foto) y la
 * bitácora al final.
 * <p>
 * Correcciones del sprint 6:
 * <ul>
 *   <li>S6-M1: la foto guarda el texto exacto del mensaje (en MySQL, cada RESUMEN_DIARIO debe llevar ESE texto y el texto
 *       debe decir las cifras de la foto), y una foto sin su {@code RESUMEN_DIARIO_GUARDADO} en la bitácora (plantada por
 *       SQL) no se da por buena: queda {@code RESUMEN_DIARIO_SUPLANTADO} y es una alerta CRÍTICA.</li>
 *   <li>S6-M3: el resumen informa los cierres con diferencia desde el resumen anterior, aunque ya estén aprobados.</li>
 *   <li>S6-M2: el resumen lleva los resultados de las llamadas de control de la semana.</li>
 *   <li>S6-B1, QA-S6-1 y QA-S6-5: «el resumen no salió» se revisa desde el último resumen CONFIRMADO, sin ventana.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class ResumenesDiarios {

	/** P18: la huella de la tarde que sale en el resumen (la última por hora hasta esta hora). */
	public static final LocalTime HORA_HUELLA = LocalTime.of(19, 0);

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

	private final ResumenDiarioRepository resumenes;

	private final CifrasDelDia cifras;

	private final CifrasCaja caja;

	private final BandejaAprobaciones bandeja;

	private final ConsultaMensajes mensajes;

	private final ConsultaHuellas huellas;

	private final ObjectProvider<AlertasRevision> alertas;

	private final CalendarioHabil calendario;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final PropiedadesPanel propiedades;

	private final Clock reloj;

	private final LlamadasControl llamadas;

	public ResumenesDiarios(ResumenDiarioRepository resumenes, CifrasDelDia cifras, CifrasCaja caja,
			BandejaAprobaciones bandeja, ConsultaMensajes mensajes, ConsultaHuellas huellas,
			ObjectProvider<AlertasRevision> alertas, CalendarioHabil calendario, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, PropiedadesPanel propiedades, Clock reloj, LlamadasControl llamadas) {
		this.resumenes = resumenes;
		this.cifras = cifras;
		this.caja = caja;
		this.bandeja = bandeja;
		this.mensajes = mensajes;
		this.huellas = huellas;
		this.alertas = alertas;
		this.calendario = calendario;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.propiedades = propiedades;
		this.reloj = reloj;
		this.llamadas = llamadas;
	}

	/**
	 * Guarda la foto del día y la envía (decisión 68: de lunes a sábado; domingo y feriado solo si hubo cobros). Si ya
	 * existe, no hace nada ({@code uk_resumen_diario}); pero si esa foto no la guardó sistema.panel (no tiene su
	 * {@code RESUMEN_DIARIO_GUARDADO}), deja {@code RESUMEN_DIARIO_SUPLANTADO} resaltado (S6-M1).
	 *
	 * @return la foto guardada, o vacío si ya existía o ese día no corresponde
	 */
	@PreAuthorize("hasRole('SISTEMA_PANEL')")
	@Transactional
	public Optional<ResumenDiario> generar(LocalDate fecha) {
		Objects.requireNonNull(fecha, "fecha");
		Optional<ResumenDiario> existente = resumenes.findByFecha(fecha);
		if (existente.isPresent()) {
			registrarSiFueSuplantada(existente.get());
			return Optional.empty();
		}
		LocalDateTime corte = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		CifrasResumen c = cifras.calcular(fecha);
		if (!calendario.admiteMensajes(fecha) && c.dia().cantidad() == 0) {
			return Optional.empty();
		}
		Optional<ConsultaHuellas.HuellaDeLaHora> huella = huellas.ultimaHasta(fecha.atTime(HORA_HUELLA));
		long pendientes = bandeja.contarPendientes();
		long criticas = alertasCriticas();
		AvisosDelDia avisos = mensajes.avisosFinancieros(fecha);
		LocalDateTime desdeElAnterior = resumenes.findFirstByFechaLessThanOrderByFechaDesc(fecha)
				.map(ResumenDiario::getCortadoEn).orElse(fecha.atStartOfDay());
		CifrasCaja.CierresConDiferencia cierres = caja.cierresConDiferencia(desdeElAnterior, corte);
		List<String> parametros = parametros(fecha, c, pendientes, criticas, huella, cierres, llamadas.avanceDe(fecha));
		ResumenDiario foto = resumenes.saveAndFlush(ResumenDiario.de(fecha, corte,
				new ResumenDiario.Cifras(c.dia().total(), c.dia().cantidad(), c.dia().efectivo(), c.dia().pagosEfectivo(),
						c.mes().total(), c.deuda().monto(), c.deuda().familias()),
				new ResumenDiario.Conteos(c.cajas().abiertas(), c.cajas().conDiferencia(), pendientes, criticas,
						avisos.creados(), Math.min(avisos.salieron(), avisos.creados())),
				huella.map(ConsultaHuellas.HuellaDeLaHora::secuencia).orElse(null),
				huella.map(ConsultaHuellas.HuellaDeLaHora::codigo).orElse(null), texto(parametros)));
		eventos.publishEvent(new ResumenDiarioListo(ContextoColegio.actual(), foto.getId(), fecha, parametros));
		auditoria.registrar(AccionAuditoria.RESUMEN_DIARIO_GUARDADO, "resumen_diario", foto.getId().toString(), null,
				"cobrado " + Dinero.formatear(foto.getCobradoTotal()) + " en " + foto.getPagosCantidad() + " pago(s)",
				"Resumen del " + fecha.format(FECHA) + " guardado (comprobado contra los libros) y enviado a Promotoría. "
						+ "Mes: " + Dinero.formatear(foto.getCobradoMes()) + ". Deuda vencida: "
						+ Dinero.formatear(foto.getDeudaVencida()) + " de " + foto.getFamiliasMorosas() + " familia(s). "
						+ "Huella: " + textoHuella(fecha, huella) + ".");
		return Optional.of(foto);
	}

	/** P5: el resumen de un día no salió (el proceso falló aun reintentando). Resaltado en la bitácora. */
	@PreAuthorize("hasRole('SISTEMA_PANEL')")
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void registrarQueNoSalio(LocalDate fecha, String causa) {
		auditoria.registrar(AccionAuditoria.RESUMEN_DIARIO_NO_SALIO, "resumen_diario", null, null, fecha.toString(),
				"El resumen del " + fecha.format(FECHA) + " no se pudo guardar ni enviar (" + causa + "). Revisa el "
						+ "panel: si las cifras de los libros cambiaron mientras se calculaba, se reintenta solo.");
	}

	/**
	 * Los 12 parámetros de cc_resumen_diario: solo cifras, la fecha, la huella y las llamadas (sección 12.2). En MySQL,
	 * trg_resumen_diario_registro comprueba que la fecha, lo cobrado, los pagos, el efectivo, las cajas, el mes, la deuda,
	 * lo pendiente y las alertas del texto sean los de la foto (S6-M1): no cambies su forma sin cambiar el trigger.
	 */
	static List<String> parametros(LocalDate fecha, CifrasResumen c, long pendientes, long criticas,
			Optional<ConsultaHuellas.HuellaDeLaHora> huella, CifrasCaja.CierresConDiferencia cierres,
			AvanceLlamadas llamadas) {
		return List.of(fecha.format(FECHA), Dinero.formatear(c.dia().total()), String.valueOf(c.dia().cantidad()),
				Formato.porcentaje(c.dia().porcentajeDigital()), Dinero.formatear(c.dia().efectivo()),
				c.cajas().cerradas() + " cerrada(s), " + c.cajas().abiertas() + " sin cerrar, " + c.cajas().conDiferencia()
						+ " con diferencia; desde el resumen anterior, " + cierres.cantidad() + " cierre(s) con diferencia"
						+ (cierres.cantidad() == 0 ? "" : " (" + Dinero.formatear(cierres.suma()) + ")"),
				Dinero.formatear(c.mes().total()),
				Dinero.formatear(c.deuda().monto()) + " de " + c.deuda().familias() + " familia(s)",
				String.valueOf(pendientes), String.valueOf(criticas), textoHuella(fecha, huella), textoLlamadas(llamadas));
	}

	/** S6-M2: «1 de 3 hechas: 1 confirma, 0 no confirma, 1 no contesta, 0 reemplazada(s)». */
	static String textoLlamadas(AvanceLlamadas a) {
		if (a.esperadas() == 0 && a.hechas() == 0) {
			return "esta semana no hay a quién llamar";
		}
		return a.hechas() + " de " + a.esperadas() + " hechas: " + a.confirman() + " confirma, " + a.noConfirman()
				+ " no confirma, " + a.noContestan() + " no contesta, " + a.reemplazadas() + " reemplazada(s)";
	}

	/** El texto que se guarda en la foto: como {@code mensaje.parametros} (separados por un salto de línea). */
	static String texto(List<String> parametros) {
		return String.join("\n", parametros.stream().map(v -> v.replace("\n", " ").replace("\r", " ").strip()).toList());
	}

	/** S6-M1: una foto que no guardó sistema.panel (sin su evento GUARDADO) queda resaltada, una vez. */
	private void registrarSiFueSuplantada(ResumenDiario foto) {
		String id = foto.getId().toString();
		if (guardadaPorElSistema(foto) || auditoria.existeSobre(AccionAuditoria.RESUMEN_DIARIO_SUPLANTADO, "resumen_diario",
				id)) {
			return;
		}
		auditoria.registrar(AccionAuditoria.RESUMEN_DIARIO_SUPLANTADO, "resumen_diario", id, null,
				foto.getFecha().toString(), "La foto del resumen del " + foto.getFecha().format(FECHA) + " no la guardó "
						+ "sistema.panel (no tiene su evento en la bitácora): alguien la insertó por fuera de la aplicación. "
						+ "El resumen verdadero de ese día no salió. Revisa los pagos del día y quién tiene acceso a la base.");
	}

	/** S6-M1: la foto la guardó sistema.panel (tiene su RESUMEN_DIARIO_GUARDADO en la bitácora). */
	private boolean guardadaPorElSistema(ResumenDiario foto) {
		return auditoria.existeSobre(AccionAuditoria.RESUMEN_DIARIO_GUARDADO, "resumen_diario", foto.getId().toString());
	}

	/** S6-M1: las fotos de la ventana del recálculo que no guardó sistema.panel (alerta CRÍTICA). */
	public List<ResumenDiario> suplantadas() {
		return fotosDeLaVentana().stream().filter(f -> !guardadaPorElSistema(f)).toList();
	}

	/**
	 * S6-B1, QA-S6-1 y QA-S6-5: los días que debían tener resumen desde el último resumen CONFIRMADO (guardado por
	 * sistema.panel y enviado a todas las personas de Promotoría) hasta {@code hasta}, sin ventana: un sábado, un día antes
	 * de feriados o un colegio que dejó de enviar hace meses siguen alertando. Vacío si el colegio aún no tiene resúmenes
	 * (no hay nada que suprimir).
	 */
	public List<LocalDate> diasSinResumen(LocalDate hasta) {
		List<ResumenDiario> fotos = resumenes.findTop400ByFechaLessThanEqualOrderByFechaDesc(hasta);
		if (fotos.isEmpty()) {
			return List.of();
		}
		LocalDate desde = fotos.getLast().getFecha();
		for (ResumenDiario foto : fotos) {
			if (confirmada(foto)) {
				desde = foto.getFecha().plusDays(1);
				break;
			}
		}
		if (desde.isAfter(hasta)) {
			return List.of();
		}
		Map<LocalDate, CobradoDia> cobros = caja.cobradoPorDia(desde, hasta);
		List<LocalDate> faltan = new ArrayList<>();
		for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
			CobradoDia cobrado = cobros.get(dia);
			if (calendario.admiteMensajes(dia) || (cobrado != null && cobrado.cantidad() > 0)) {
				faltan.add(dia);
			}
		}
		return faltan;
	}

	private boolean confirmada(ResumenDiario foto) {
		return guardadaPorElSistema(foto) && mensajes.entregaDelResumen(foto.getId()).completa();
	}

	private static String textoHuella(LocalDate fecha, Optional<ConsultaHuellas.HuellaDeLaHora> huella) {
		return huella.map(h -> "evento " + h.secuencia() + ", código " + h.codigo() + " ("
				+ (h.momento().toLocalDate().equals(fecha) ? h.momento().format(HORA) : h.momento().format(DIA_HORA)) + ")")
				.orElse("aún no hay huella por hora");
	}

	/** CRÍTICAS de «Para revisar» que se pueden calcular sin una persona en sesión. */
	private long alertasCriticas() {
		return alertas.orderedStream().filter(AlertasRevision::difundible).flatMap(a -> a.alertas().stream())
				.filter(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA).count();
	}

	/** Si el resumen de ese día debía salir: lunes a sábado sin feriado, o cualquier día con cobros. */
	public boolean debiaSalir(LocalDate fecha) {
		return calendario.admiteMensajes(fecha) || caja.cobrado(fecha, fecha).cantidad() > 0;
	}


	/** La foto de un día con su envío y si los libros cambiaron desde entonces. */
	public Optional<ResumenVista> deFecha(LocalDate fecha) {
		return resumenes.findByFecha(fecha).map(r -> vista(r, comparar(List.of(r)).getFirst()));
	}

	/**
	 * Si el resumen de ese día ya salió a todas las personas de Promotoría activas, desde una foto que guardó
	 * sistema.panel (S6-M1: una foto plantada con su mensaje no cuenta).
	 */
	public boolean salioATodos(LocalDate fecha) {
		return resumenes.findByFecha(fecha).map(this::confirmada).orElse(false);
	}

	/** Los resúmenes de la ventana del recálculo, del más reciente al más antiguo, con «cambió desde que se envió». */
	public List<ResumenVista> recientes() {
		List<ResumenDiario> fotos = fotosDeLaVentana();
		List<ComparacionResumen> comparaciones = comparar(fotos);
		List<ResumenVista> vistas = new ArrayList<>();
		for (int i = fotos.size() - 1; i >= 0; i--) {
			vistas.add(vista(fotos.get(i), comparaciones.get(i)));
		}
		return vistas;
	}

	private ResumenVista vista(ResumenDiario r, ComparacionResumen comparacion) {
		EntregaResumen entrega = mensajes.entregaDelResumen(r.getId());
		String envio = entrega.enviadoEn() == null ? entrega.estado()
				: "Enviado " + entrega.enviadoEn().format(HORA) + " · " + entrega.estado();
		return new ResumenVista(r.getId(), r.getFecha().format(FECHA), Dinero.formatear(r.getCobradoTotal()),
				r.getPagosCantidad(), Dinero.formatear(r.getCobradoEfectivo()), Dinero.formatear(r.getCobradoMes()),
				Dinero.formatear(r.getDeudaVencida()), r.getFamiliasMorosas(), r.getAlertasCriticas(),
				r.getHuellaSecuencia() == null ? "Sin huella por hora" : "Evento " + r.getHuellaSecuencia() + ", código "
						+ r.getHuellaCodigo(), envio, entrega.completa(),
				comparacion.cambio() ? comparacion.explicacion() : null, comparacion.sinExplicar());
	}

	/** Las fotos de la ventana del recálculo ({@code cuentasclaras.panel.recalculo-dias}) contra los libros de hoy. */
	public List<ComparacionResumen> comparar() {
		return comparar(fotosDeLaVentana());
	}

	private List<ResumenDiario> fotosDeLaVentana() {
		return resumenes.findByFechaGreaterThanEqualOrderByFechaAsc(LocalDate.now(reloj)
				.minusDays(propiedades.recalculoDias()));
	}

	/**
	 * Recalcula cada foto con los libros de hoy y explica la diferencia: lo registrado después del corte (pagos en línea o
	 * del banco que llegaron tarde) suma, lo anulado después del corte resta. Pocas consultas para todos los días.
	 */
	List<ComparacionResumen> comparar(List<ResumenDiario> fotos) {
		if (fotos.isEmpty()) {
			return List.of();
		}
		LocalDate desde = fotos.stream().map(ResumenDiario::getFecha).min(Comparator.naturalOrder()).orElseThrow()
				.withDayOfMonth(1);
		LocalDate hasta = fotos.stream().map(ResumenDiario::getFecha).max(Comparator.naturalOrder()).orElseThrow();
		LocalDateTime primerCorte = fotos.stream().map(ResumenDiario::getCortadoEn).min(Comparator.naturalOrder())
				.orElseThrow();
		Map<LocalDate, CobradoDia> porDia = caja.cobradoPorDia(desde, hasta);
		CambiosPosteriores cambios = caja.cambiosPosteriores(desde, hasta, primerCorte);
		List<ComparacionResumen> resultado = new ArrayList<>();
		for (ResumenDiario foto : fotos) {
			resultado.add(comparar(foto, porDia, cambios));
		}
		return resultado;
	}

	private static ComparacionResumen comparar(ResumenDiario foto, Map<LocalDate, CobradoDia> porDia,
			CambiosPosteriores cambios) {
		LocalDate dia = foto.getFecha();
		LocalDate inicioMes = dia.withDayOfMonth(1);
		LocalDateTime corte = foto.getCortadoEn();
		CobradoDia hoy = porDia.getOrDefault(dia, new CobradoDia(dia, Dinero.CERO, 0, Dinero.CERO, 0));
		BigDecimal hoyMes = Dinero.sumar(porDia.values().stream().filter(d -> !d.fecha().isBefore(inicioMes)
				&& !d.fecha().isAfter(dia)).map(CobradoDia::total).toList());
		// Registrados después del corte (y que siguen vigentes) suman; anulados después del corte (registrados antes) restan.
		List<CambiosPosteriores.Movimiento> registrados = cambios.registrados().stream()
				.filter(m -> m.registradoEn().isAfter(corte)).toList();
		List<CambiosPosteriores.Movimiento> anulados = cambios.anulados().stream()
				.filter(m -> m.anuladoEn().isAfter(corte) && !m.registradoEn().isAfter(corte)).toList();
		Predicate<CambiosPosteriores.Movimiento> delDia = m -> m.fecha().equals(dia);
		Predicate<CambiosPosteriores.Movimiento> delMes = m -> !m.fecha().isBefore(inicioMes) && !m.fecha().isAfter(dia);
		Predicate<CambiosPosteriores.Movimiento> efectivo = m -> m.medio() == MedioPago.EFECTIVO;

		BigDecimal esperadoTotal = foto.getCobradoTotal().add(suma(registrados, delDia)).subtract(suma(anulados, delDia));
		long esperadoPagos = foto.getPagosCantidad() + cuenta(registrados, delDia) - cuenta(anulados, delDia);
		BigDecimal esperadoEfectivo = foto.getCobradoEfectivo().add(suma(registrados, delDia.and(efectivo)))
				.subtract(suma(anulados, delDia.and(efectivo)));
		long esperadoPagosEfectivo = foto.getPagosEfectivo() + cuenta(registrados, delDia.and(efectivo))
				- cuenta(anulados, delDia.and(efectivo));
		BigDecimal esperadoMes = foto.getCobradoMes().add(suma(registrados, delMes)).subtract(suma(anulados, delMes));

		boolean cambio = hoy.total().compareTo(foto.getCobradoTotal()) != 0 || hoy.cantidad() != foto.getPagosCantidad()
				|| hoy.efectivo().compareTo(foto.getCobradoEfectivo()) != 0 || hoy.pagosEfectivo() != foto.getPagosEfectivo()
				|| hoyMes.compareTo(foto.getCobradoMes()) != 0;
		boolean explicado = hoy.total().compareTo(esperadoTotal) == 0 && hoy.cantidad() == esperadoPagos
				&& hoy.efectivo().compareTo(esperadoEfectivo) == 0 && hoy.pagosEfectivo() == esperadoPagosEfectivo
				&& hoyMes.compareTo(esperadoMes) == 0;
		String explicacion;
		if (!cambio) {
			explicacion = null;
		}
		else if (explicado) {
			explicacion = "Después del envío se registraron " + cuenta(registrados, delMes) + " pago(s) por "
					+ Dinero.formatear(suma(registrados, delMes)) + " y se anularon " + cuenta(anulados, delMes)
					+ " por " + Dinero.formatear(suma(anulados, delMes)) + " (anulaciones aprobadas y pagos tardíos).";
		}
		else {
			explicacion = "La foto dijo " + Dinero.formatear(foto.getCobradoTotal()) + " en " + foto.getPagosCantidad()
					+ " pago(s) (mes " + Dinero.formatear(foto.getCobradoMes()) + "); hoy los libros dicen "
					+ Dinero.formatear(hoy.total()) + " en " + hoy.cantidad() + " pago(s) (mes " + Dinero.formatear(hoyMes)
					+ "). Las anulaciones aprobadas y los pagos tardíos solo explican " + Dinero.formatear(esperadoTotal)
					+ " del día y " + Dinero.formatear(esperadoMes) + " del mes: falta o sobra "
					+ Dinero.formatear(hoyMes.subtract(esperadoMes)) + " sin explicación.";
		}
		return new ComparacionResumen(foto.getId(), dia, corte, foto.getCobradoTotal(), foto.getPagosCantidad(),
				hoy.total(), hoy.cantidad(), foto.getCobradoMes(), hoyMes, cambio, explicado, explicacion);
	}

	private static BigDecimal suma(List<CambiosPosteriores.Movimiento> movimientos,
			Predicate<CambiosPosteriores.Movimiento> filtro) {
		return Dinero.sumar(movimientos.stream().filter(filtro).map(CambiosPosteriores.Movimiento::total).toList());
	}

	private static long cuenta(List<CambiosPosteriores.Movimiento> movimientos,
			Predicate<CambiosPosteriores.Movimiento> filtro) {
		return movimientos.stream().filter(filtro).count();
	}

	/**
	 * Recálculo de cada mañana (06:15, {@code sistema.panel}): deja en la bitácora cada foto cuyas cifras cambiaron, una
	 * sola vez por hallazgo (resaltado si nada lo explica).
	 *
	 * @return cuántos hallazgos nuevos quedaron
	 */
	@PreAuthorize("hasRole('SISTEMA_PANEL')")
	@Transactional
	public int registrarCambios() {
		int nuevos = 0;
		for (ComparacionResumen c : comparar()) {
			if (!c.cambio()) {
				continue;
			}
			AccionAuditoria accion = c.explicado() ? AccionAuditoria.RESUMEN_DIARIO_CAMBIO_EXPLICADO
					: AccionAuditoria.RESUMEN_DIARIO_CAMBIO;
			String nuevo = "hoy " + Dinero.formatear(c.hoyTotal()) + " en " + c.hoyPagos() + " pago(s); mes "
					+ Dinero.formatear(c.hoyMes());
			if (auditoria.existe(accion, "resumen_diario", c.resumenId().toString(), nuevo)) {
				continue;
			}
			auditoria.registrar(accion, "resumen_diario", c.resumenId().toString(), "foto " + Dinero.formatear(c.fotoTotal())
					+ " en " + c.fotoPagos() + " pago(s); mes " + Dinero.formatear(c.fotoMes()), nuevo,
					"Resumen del " + c.fecha().format(FECHA) + ": " + c.explicacion());
			nuevos++;
		}
		return nuevos;
	}
}
