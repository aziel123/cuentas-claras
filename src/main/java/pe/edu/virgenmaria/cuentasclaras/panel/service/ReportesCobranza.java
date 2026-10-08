package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.AnuladoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.FamiliaMorosa;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MorosidadGrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.dto.UltimoAviso;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ConsultaMensajes;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.FamiliaMorosaVista;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.ReporteIngresos;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.ReporteMorosidad;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reportes de cobranza en pantalla (sprint 6, decisiones 73 a 76) para Promotoría, Dirección y Administración:
 * morosidad por grado (nunca por sección), ingresos por medio de pago y la lista de familias morosas (que no se exporta).
 * Caja, Docente y Apoderado no ven la morosidad (P14).
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ReportesCobranza {

	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

	private final CifrasCaja caja;

	private final CifrasCobranza cobranza;

	private final ConsultaMensajes mensajes;

	private final Clock reloj;

	public ReportesCobranza(CifrasCaja caja, CifrasCobranza cobranza, ConsultaMensajes mensajes, Clock reloj) {
		this.caja = caja;
		this.cobranza = cobranza;
		this.mensajes = mensajes;
		this.reloj = reloj;
	}

	/** Morosidad por grado del año pedido (por defecto, el año en curso) al día de hoy. */
	public ReporteMorosidad morosidadPorGrado(Long anioId) {
		LocalDate hoy = LocalDate.now(reloj);
		List<AnioOpcion> anios = cobranza.anios();
		Optional<AnioOpcion> anio = cobranza.anio(anioId);
		boolean puedeExportar = Formato.tieneAlgunRol("PROMOTOR", "ADMINISTRACION");
		if (anio.isEmpty()) {
			return new ReporteMorosidad(anios, null, hoy, List.of(), Dinero.formatear(Dinero.CERO), 0, 0, puedeExportar);
		}
		List<MorosidadGrado> filas = cobranza.morosidadPorGrado(anio.get().id(), hoy);
		return new ReporteMorosidad(anios, anio.get(), hoy, filas.stream().map(f -> new ReporteMorosidad.Fila(
				f.etiqueta(), f.matriculados(), f.conDeuda(), Dinero.formatear(f.monto()), f.tramos().hasta30(),
				f.tramos().hasta60(), f.tramos().hasta90(), f.tramos().masDe90())).toList(),
				Dinero.formatear(Dinero.sumar(filas.stream().map(MorosidadGrado::monto).toList())),
				filas.stream().mapToLong(MorosidadGrado::conDeuda).sum(),
				filas.stream().mapToLong(MorosidadGrado::matriculados).sum(), puedeExportar);
	}

	/** Ingresos por medio y por origen en un rango (por defecto, el mes en curso hasta hoy; 12 meses como máximo). */
	public ReporteIngresos ingresosPorMedio(LocalDate desde, LocalDate hasta) {
		RangoReporte rango = RangoReporte.de(desde, hasta, LocalDate.now(reloj));
		CobradoPeriodo cobrado = caja.cobrado(rango.desde(), rango.hasta());
		AnuladoPeriodo anulado = caja.anulado(rango.desde(), rango.hasta());
		return new ReporteIngresos(rango.desde(), rango.hasta(), Dinero.formatear(cobrado.total()), cobrado.cantidad(),
				Formato.porcentaje(cobrado.porcentajeDigital()),
				cobrado.porMedio().stream().map(m -> new ReporteIngresos.Linea(m.etiqueta(), m.cantidad(),
						Dinero.formatear(m.total()))).toList(),
				cobrado.porCanal().stream().map(c -> new ReporteIngresos.Linea(c.etiqueta(), c.cantidad(),
						Dinero.formatear(c.total()))).toList(),
				Dinero.formatear(anulado.total()), anulado.cantidad(), Formato.tieneAlgunRol("PROMOTOR", "ADMINISTRACION"));
	}

	/** Familias con deuda vencida hoy, de la que más debe a la que menos, con su último aviso entregado. */
	public List<FamiliaMorosaVista> familiasMorosas() {
		List<FamiliaMorosa> morosas = cobranza.familiasMorosas(LocalDate.now(reloj));
		Map<Long, UltimoAviso> avisos = mensajes.ultimosAvisosDeCobranza(morosas.stream()
				.map(FamiliaMorosa::familiaId).toList());
		return morosas.stream().map(f -> {
			UltimoAviso aviso = avisos.get(f.familiaId());
			return new FamiliaMorosaVista(f.familiaId(), f.familia(), f.alumnos(), Dinero.formatear(f.monto()), f.dias(),
					aviso == null ? "Sin avisos entregados"
							: aviso.tipo() + " entregado el " + DIA.format(aviso.entregadoEn()));
		}).toList();
	}
}
