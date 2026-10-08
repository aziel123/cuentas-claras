package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.PagoExportable;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MorosidadGrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.Celda;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.EscritorXlsxSeguro;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.HojaReporte;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.ArchivoExportado;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Excel para el contador (sprint 6, decisiones 73 a 75 y sección 10). Solo Promotoría y Administración.
 * <ol>
 *   <li>valida el rango (12 meses), el tope diario (20 por persona) y el tamaño (20 000 filas); si no, deja
 *       {@code EXPORTACION_RECHAZADA} en la bitácora (la transacción no se revierte por ese rechazo);</li>
 *   <li>arma las filas con los datos mínimos (Ley 29733: familia por código, sin documentos, nombres ni contactos);</li>
 *   <li>escribe el libro con {@link EscritorXlsxSeguro} (sin fórmulas posibles) con un código de exportación impreso en
 *       la hoja «Control»;</li>
 *   <li>calcula el SHA-256 del archivo y registra {@code REPORTE_EXPORTADO} en la MISMA transacción, antes de devolver
 *       los bytes: si la bitácora falla, no hay archivo (P12).</li>
 * </ol>
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','ADMINISTRACION')")
@Transactional(noRollbackFor = ExportacionRechazadaException.class)
public class ExportacionContador {

	/** Decisión 73: hasta 20 descargas por persona y día. */
	public static final int MAX_DIARIAS = 20;

	static final String AVISO_USO = "Uso exclusivo para la contabilidad del colegio. Contiene datos de familias: no lo "
			+ "reenvíe ni lo suba a otros servicios.";

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final CifrasCaja caja;

	private final CifrasCobranza cobranza;

	private final EscritorXlsxSeguro escritor;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ExportacionContador(CifrasCaja caja, CifrasCobranza cobranza, EscritorXlsxSeguro escritor,
			AuditoriaService auditoria, Clock reloj) {
		this.caja = caja;
		this.cobranza = cobranza;
		this.escritor = escritor;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** Ingresos de un rango de días de caja: hojas «Ingresos», «Por medio de pago» y «Control». */
	public ArchivoExportado exportarIngresos(LocalDate desde, LocalDate hasta) {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		RangoReporte rango;
		try {
			rango = RangoReporte.de(desde, hasta, ahora.toLocalDate());
		}
		catch (ReglaNegocioException e) {
			throw rechazar("INGRESOS", "rango no válido: " + desde + " a " + hasta, e.getMessage());
		}
		exigirTopeDiario("INGRESOS", ahora);
		long filasEsperadas = caja.pagosEnRango(rango.desde(), rango.hasta());
		if (filasEsperadas > EscritorXlsxSeguro.MAX_FILAS) {
			throw rechazar("INGRESOS", "filas=" + filasEsperadas, "El rango tiene más de "
					+ EscritorXlsxSeguro.MAX_FILAS + " pagos: elige un rango más corto.");
		}

		List<PagoExportable> pagos = caja.pagosParaContador(rango.desde(), rango.hasta());
		BigDecimal emitido = Dinero.sumar(pagos.stream().map(PagoExportable::total).toList());
		BigDecimal vigente = Dinero.sumar(pagos.stream().filter(p -> p.estado() == EstadoPago.VIGENTE)
				.map(PagoExportable::total).toList());
		BigDecimal anulado = emitido.subtract(vigente);
		CobradoPeriodo cobrado = caja.cobrado(rango.desde(), rango.hasta());
		if (cobrado.total().compareTo(vigente) != 0) {
			// Las dos lecturas son del mismo libro en la misma transacción: si no coinciden, no se entrega nada.
			throw new IllegalStateException("El detalle del Excel no cuadra con lo cobrado en el rango");
		}

		List<List<Celda>> filas = new ArrayList<>();
		for (PagoExportable p : pagos) {
			filas.add(List.of(Celda.fecha(p.fecha()), Celda.texto(p.comprobante()), Celda.texto(p.tipo().etiqueta()),
					Celda.texto(p.medio().etiqueta()), Celda.texto(p.canal().etiqueta()), Celda.texto(p.numeroOperacion()),
					Celda.dinero(p.total()), Celda.texto(p.estado() == EstadoPago.VIGENTE ? "Vigente" : "Anulado"),
					Celda.fecha(p.fechaNotaCredito()), Celda.texto(p.notaCredito()), Celda.texto(p.conceptos()),
					Celda.entero(p.familiaId()), Celda.texto(p.registradoPor()), Celda.texto(p.ruc())));
		}
		HojaReporte ingresos = new HojaReporte("Ingresos", List.of("Fecha", "Comprobante", "Tipo", "Medio", "Origen",
				"N.° de operación", "Monto", "Estado", "Fecha de la nota de crédito", "Nota de crédito", "Conceptos",
				"Código de familia", "Registrado por", "RUC (solo facturas)"), filas);

		List<List<Celda>> resumen = new ArrayList<>();
		cobrado.porMedio().forEach(m -> resumen.add(List.of(Celda.texto("Medio"), Celda.texto(m.etiqueta()),
				Celda.entero(m.cantidad()), Celda.dinero(m.total()))));
		cobrado.porCanal().forEach(c -> resumen.add(List.of(Celda.texto("Origen"), Celda.texto(c.etiqueta()),
				Celda.entero(c.cantidad()), Celda.dinero(c.total()))));
		long anulados = pagos.stream().filter(p -> p.estado() == EstadoPago.ANULADO).count();
		resumen.add(List.of(Celda.texto("Anulado"), Celda.texto("Pagos del rango anulados (aparte)"),
				Celda.entero(anulados), Celda.dinero(anulado)));
		HojaReporte porMedio = new HojaReporte("Por medio de pago", List.of("Agrupación", "Medio u origen",
				"Pagos vigentes", "Total"), resumen);

		String codigo = UUID.randomUUID().toString();
		String rangoTexto = FECHA.format(rango.desde()) + " al " + FECHA.format(rango.hasta());
		HojaReporte control = control(codigo, ahora, "Ingresos por medio de pago", rangoTexto, pagos.size(), List.of(
				List.of("Total emitido (vigente y anulado)", Dinero.formatear(emitido)),
				List.of("Total vigente", Dinero.formatear(vigente)),
				List.of("Total anulado", Dinero.formatear(anulado)),
				List.of("Comprobación", "Vigente + anulado = emitido: cuadra")));
		String nombre = "ingresos-" + periodo(rango.desde(), rango.hasta()) + ".xlsx";
		return registrar("INGRESOS", rango.desde() + ".." + rango.hasta(), pagos.size(), codigo, nombre,
				escritor.escribir(List.of(ingresos, porMedio, control)));
	}

	/** Morosidad por grado de un año: hojas «Morosidad por grado» y «Control». Nunca por sección ni con nombres. */
	public ArchivoExportado exportarMorosidad(Long anioId) {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		AnioOpcion anio = cobranza.anio(anioId).orElseThrow(() -> new ReglaNegocioException(
				"El colegio aún no tiene años escolares."));
		exigirTopeDiario("MOROSIDAD", ahora);
		LocalDate hoy = ahora.toLocalDate();
		List<MorosidadGrado> grados = cobranza.morosidadPorGrado(anio.id(), hoy);
		List<List<Celda>> filas = new ArrayList<>();
		for (MorosidadGrado g : grados) {
			filas.add(List.of(Celda.texto(g.etiqueta()), Celda.entero(g.matriculados()), Celda.entero(g.conDeuda()),
					Celda.dinero(g.monto()), Celda.entero(g.tramos().hasta30()), Celda.entero(g.tramos().hasta60()),
					Celda.entero(g.tramos().hasta90()), Celda.entero(g.tramos().masDe90())));
		}
		HojaReporte morosidad = new HojaReporte("Morosidad por grado", List.of("Grado", "Alumnos matriculados",
				"Alumnos con deuda vencida", "Monto vencido", "1 a 30 días", "31 a 60 días", "61 a 90 días",
				"Más de 90 días"), filas);
		String codigo = UUID.randomUUID().toString();
		HojaReporte control = control(codigo, ahora, "Morosidad por grado " + anio.anio(),
				"Cuotas del año " + anio.anio() + " vencidas al " + FECHA.format(hoy), grados.size(), List.of(
						List.of("Total vencido", Dinero.formatear(Dinero.sumar(grados.stream().map(MorosidadGrado::monto)
								.toList()))),
						List.of("Alumnos con deuda vencida", String.valueOf(grados.stream()
								.mapToLong(MorosidadGrado::conDeuda).sum()))));
		return registrar("MOROSIDAD", anio.anio() + " al " + hoy, grados.size(), codigo, "morosidad-" + anio.anio()
				+ ".xlsx", escritor.escribir(List.of(morosidad, control)));
	}

	private void exigirTopeDiario(String tipo, LocalDateTime ahora) {
		long hoy = auditoria.contarDesdeDelUsuarioActual(AccionAuditoria.REPORTE_EXPORTADO,
				ahora.toLocalDate().atStartOfDay());
		if (hoy >= MAX_DIARIAS) {
			throw rechazar(tipo, "tope diario de " + MAX_DIARIAS, "Ya descargaste " + MAX_DIARIAS
					+ " reportes hoy. Podrás descargar más mañana.");
		}
	}

	private ExportacionRechazadaException rechazar(String tipo, String causa, String mensaje) {
		auditoria.registrar(AccionAuditoria.EXPORTACION_RECHAZADA, "reporte", tipo, null, null,
				"tipo=" + tipo + "; " + causa);
		return new ExportacionRechazadaException(mensaje);
	}

	private ArchivoExportado registrar(String tipo, String rango, int filas, String codigo, String nombre,
			byte[] contenido) {
		String sha = sha256(contenido);
		auditoria.registrar(AccionAuditoria.REPORTE_EXPORTADO, "reporte", codigo, null, null,
				"tipo=" + tipo + "; rango=" + rango + "; filas=" + filas + "; sha256=" + sha + "; codigo=" + codigo);
		return new ArchivoExportado(nombre, contenido, codigo);
	}

	private static HojaReporte control(String codigo, LocalDateTime ahora, String reporte, String rango, int filas,
			List<List<String>> totales) {
		List<List<Celda>> datos = new ArrayList<>();
		datos.add(List.of(Celda.texto("Reporte"), Celda.texto(reporte)));
		datos.add(List.of(Celda.texto("Exportado por"), Celda.texto(Formato.usuarioActual())));
		datos.add(List.of(Celda.texto("Fecha y hora (Lima)"), Celda.texto(FECHA_HORA.format(ahora))));
		datos.add(List.of(Celda.texto("Rango"), Celda.texto(rango)));
		datos.add(List.of(Celda.texto("Filas"), Celda.texto(String.valueOf(filas))));
		totales.forEach(t -> datos.add(List.of(Celda.texto(t.get(0)), Celda.texto(t.get(1)))));
		datos.add(List.of(Celda.texto("Código de exportación"), Celda.texto(codigo)));
		datos.add(List.of(Celda.texto("Aviso"), Celda.texto(AVISO_USO)));
		return new HojaReporte("Control", List.of("Dato", "Valor"), datos);
	}

	/** «2026-10» si el rango es de un solo mes; «2026-01-a-2026-10» si no. Sin datos personales. */
	private static String periodo(LocalDate desde, LocalDate hasta) {
		String inicio = desde.getYear() + "-" + String.format("%02d", desde.getMonthValue());
		String fin = hasta.getYear() + "-" + String.format("%02d", hasta.getMonthValue());
		return inicio.equals(fin) ? inicio : inicio + "-a-" + fin;
	}

	static String sha256(byte[] contenido) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}
}
