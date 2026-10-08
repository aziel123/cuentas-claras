package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que bajó la deuda sin que entrara dinero en un periodo (P16, «descuento fantasma»): descuentos APROBADOS (lo que
 * de verdad se descontó, según el libro de ajustes) y cuotas ANULADAS, cada uno con quién lo aprobó.
 */
public record RebajasPeriodo(LocalDate desde, LocalDate hasta, BigDecimal descuentos, long cantidadDescuentos,
		List<PorAprobador> descuentosPorAprobador, BigDecimal cuotasAnuladas, long cantidadCuotasAnuladas,
		List<PorAprobador> anuladasPorAprobador) {

	public record PorAprobador(String aprobador, long cantidad, BigDecimal monto) {
	}

	public RebajasPeriodo {
		descuentosPorAprobador = List.copyOf(descuentosPorAprobador);
		anuladasPorAprobador = List.copyOf(anuladasPorAprobador);
	}
}
