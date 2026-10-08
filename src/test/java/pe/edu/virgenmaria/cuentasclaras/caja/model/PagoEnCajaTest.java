package pe.edu.virgenmaria.cuentasclaras.caja.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.AfectacionIgv;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.DocumentoReceptor;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.LineaDocumento;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Receptor;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hallazgo 5 de QA (M4b): la segunda capa del modelo. Aunque el servicio dejara pasar un cobro en efectivo con la caja
 * cerrada, {@link Pago#enCaja} lo rechaza; un pago digital sí entra (se verifica contra el banco).
 */
class PagoEnCajaTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 2);

	@Test
	void pagoEnEfectivoEnCajaCerradaEsRechazadoPorElModelo() {
		CajaDiaria caja = CajaDiaria.abrir("caja", HOY, BigDecimal.ZERO);
		caja.registrarConteo(new BigDecimal("0.00"), new BigDecimal("0.00"));
		caja.cerrar(CierreCaja.registrar(caja, new ResumenCaja(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO),
				new BigDecimal("0.00"), new BigDecimal("0.00"), null, null));
		assertThat(caja.aceptaEfectivo()).isFalse();

		assertThatThrownBy(() -> Pago.enCaja(caja, Familia.nueva("Familia Quispe"), boleta(), MedioPago.EFECTIVO, null,
				new BigDecimal("450.00"), new BigDecimal("450.00"), false, UUID.randomUUID()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya se cerró");
		assertThat(Pago.enCaja(caja, Familia.nueva("Familia Quispe"), boleta(), MedioPago.YAPE, "12345678",
				new BigDecimal("450.00"), null, false, UUID.randomUUID()).vigente()).isTrue();
	}

	private static Comprobante boleta() {
		SerieComprobante serie = SerieComprobante.nueva(TipoComprobante.BOLETA, "B001", ProveedorComprobantes.SIMULADO);
		return Comprobante.emitir(serie, serie.siguiente(), HOY, Receptor.de(DocumentoReceptor.DNI, "45678912",
				"Rosa Huamán"), AfectacionIgv.INAFECTO, List.of(new LineaDocumento("Pensión marzo", new BigDecimal("450.00"))));
	}
}
