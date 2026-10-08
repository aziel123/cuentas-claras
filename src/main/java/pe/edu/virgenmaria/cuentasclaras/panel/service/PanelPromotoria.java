package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AvanceMes;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DeudaVencida;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.RebajasPeriodo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.CifrasResumen;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.VistaPanel;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * El panel de Promotoría (sprint 6, sección 12.1): lo cobrado hoy y en el mes, la deuda vencida por tramos, el % de
 * pagos digitales, las rebajas del mes con quién aprobó, lo que espera aprobación y las alertas. Todo se calcula al
 * consultar desde los libros (P3: no hay cifras guardadas que alguien pueda maquillar). Ver el panel no se audita: no
 * cambia nada.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('PROMOTOR')")
public class PanelPromotoria {

	private final CifrasDelDia cifras;

	private final CifrasCobranza cobranza;

	private final BandejaAprobaciones bandeja;

	private final ObjectProvider<AlertasRevision> alertas;

	private final Clock reloj;

	public PanelPromotoria(CifrasDelDia cifras, CifrasCobranza cobranza, BandejaAprobaciones bandeja,
			ObjectProvider<AlertasRevision> alertas, Clock reloj) {
		this.cifras = cifras;
		this.cobranza = cobranza;
		this.bandeja = bandeja;
		this.alertas = alertas;
		this.reloj = reloj;
	}

	public VistaPanel ver() {
		LocalDate hoy = LocalDate.now(reloj);
		YearMonth mes = YearMonth.from(hoy);
		CifrasResumen c = cifras.calcular(hoy);
		AvanceMes avance = cobranza.avanceDelMes(mes);
		RebajasPeriodo rebajas = cobranza.rebajas(mes.atDay(1), hoy);
		List<AlertaRevision> todas = alertas.orderedStream().flatMap(a -> a.alertas().stream())
				.sorted(AlertaRevision.POR_GRAVEDAD).toList();
		CobradoPeriodo dia = c.dia();
		DeudaVencida deuda = c.deuda();
		return new VistaPanel(Calendario.formatear(hoy), todas,
				todas.stream().filter(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA).count(),
				bandeja.bandeja().pendientes().size(),
				new VistaPanel.Hoy(Dinero.formatear(dia.total()), dia.cantidad(), Formato.porcentaje(dia.porcentajeDigital()),
						Formato.porcentaje(dia.porcentajeDigitalPorMonto()), Dinero.formatear(dia.efectivo()),
						dia.pagosEfectivo(), c.cajas().abiertas(), c.cajas().cerradas(), c.cajas().conDiferencia()),
				new VistaPanel.Mes(Calendario.nombreMes(mes.getMonthValue()).toLowerCase(), Dinero.formatear(c.mes().total()),
						c.mes().cantidad(), Formato.porcentaje(avance.porcentaje()), Dinero.formatear(avance.vence()),
						Dinero.formatear(avance.pagado()), Dinero.formatear(c.anuladoMes().total()),
						c.anuladoMes().cantidad()),
				new VistaPanel.Deuda(Dinero.formatear(deuda.monto()), deuda.familias(), deuda.tramos().hasta30(),
						deuda.tramos().hasta60(), deuda.tramos().hasta90(), deuda.tramos().masDe90()),
				new VistaPanel.Rebajas(Dinero.formatear(rebajas.descuentos()), rebajas.cantidadDescuentos(),
						quienes(rebajas.descuentosPorAprobador()), Dinero.formatear(rebajas.cuotasAnuladas()),
						rebajas.cantidadCuotasAnuladas(), quienes(rebajas.anuladasPorAprobador())));
	}

	/** «director: 2 por S/ 90.00». */
	private static List<String> quienes(List<RebajasPeriodo.PorAprobador> porAprobador) {
		return porAprobador.stream().map(p -> p.aprobador() + ": " + p.cantidad() + " por " + Dinero.formatear(p.monto()))
				.toList();
	}
}
