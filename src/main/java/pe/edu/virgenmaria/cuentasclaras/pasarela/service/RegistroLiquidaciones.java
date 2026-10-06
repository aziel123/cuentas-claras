package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.NumeroOperacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.OrigenPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.LiquidacionLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.LiquidacionPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrigenLiquidacion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.TipoLineaLiquidacion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.LiquidacionLineaRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.LiquidacionPasarelaRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registra una liquidación de la pasarela (sprint 4, tanda 3), dentro de la transacción de quien llama. Solo la usa
 * {@code sistema.pasarela} (la importación diaria por la API): ninguna persona escribe una liquidación.
 * <ul>
 *   <li>Es idempotente: una referencia ya registrada no se vuelve a registrar.</li>
 *   <li>Valida neto = bruto − comisión − IGV en cada línea y en el total (la base lo vuelve a exigir con un CHECK).</li>
 *   <li>Ata cada cargo a su pago en línea por la operación canónica. Un cargo sin pago queda registrado y auditado como
 *       CRÍTICO: «la pasarela cobró algo que no registramos».</li>
 * </ul>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
@PreAuthorize("hasRole('SISTEMA_PASARELA')")
public class RegistroLiquidaciones {

	private final LiquidacionPasarelaRepository liquidaciones;

	private final LiquidacionLineaRepository lineas;

	private final PagoRepository pagos;

	private final AuditoriaService auditoria;

	public RegistroLiquidaciones(LiquidacionPasarelaRepository liquidaciones, LiquidacionLineaRepository lineas,
			PagoRepository pagos, AuditoriaService auditoria) {
		this.liquidaciones = liquidaciones;
		this.lineas = lineas;
		this.pagos = pagos;
		this.auditoria = auditoria;
	}

	/** @return el id de la liquidación nueva, o vacío si ya estaba registrada (o no trae líneas) */
	public Optional<Long> registrar(ProveedorPasarela proveedor, LiquidacionLeida leida) {
		if (leida.lineas().isEmpty() || liquidaciones.existsByProveedorAndReferencia(proveedor, leida.referencia())) {
			return Optional.empty();
		}
		BigDecimal bruto = Dinero.sumar(leida.lineas().stream().map(LiquidacionLeida.Linea::bruto).toList());
		BigDecimal comision = Dinero.sumar(leida.lineas().stream().map(LiquidacionLeida.Linea::comision).toList());
		BigDecimal igv = Dinero.sumar(leida.lineas().stream().map(LiquidacionLeida.Linea::igv).toList());
		LiquidacionPasarela liquidacion = liquidaciones.save(LiquidacionPasarela.registrar(proveedor, leida.referencia(),
				leida.fechaLiquidacion(), leida.fechaAbono(), bruto, comision, igv, leida.lineas().size(),
				OrigenLiquidacion.API, null));
		List<String> sinPago = new ArrayList<>();
		int numero = 0;
		for (LiquidacionLeida.Linea linea : leida.lineas()) {
			String operacion = NumeroOperacion.normalizar(linea.operacion());
			Long pagoId = pagos.findFirstByNumeroOperacionAndOrigenOrderByIdDesc(operacion, OrigenPago.PASARELA)
					.map(Pago::getId).orElse(null);
			lineas.save(LiquidacionLinea.nueva(liquidacion, ++numero, linea.tipo(), operacion, pagoId, linea.bruto(),
					linea.comision(), linea.igv()));
			if (pagoId == null && linea.tipo() == TipoLineaLiquidacion.CARGO) {
				sinPago.add(operacion + " por " + Dinero.formatear(linea.bruto()));
			}
		}
		auditoria.registrar(AccionAuditoria.LIQUIDACION_REGISTRADA, "liquidacion_pasarela", liquidacion.getId().toString(),
				null, liquidacion.getLineas() + " cargos · neto " + Dinero.formatear(liquidacion.getTotalNeto()),
				"Liquidación " + liquidacion.getReferencia() + " de " + proveedor + " del "
						+ Calendario.formatear(liquidacion.getFechaLiquidacion()) + ", abono al banco el "
						+ Calendario.formatear(liquidacion.getFechaAbono()) + ": bruto " + Dinero.formatear(bruto)
						+ ", comisión " + Dinero.formatear(comision) + ", IGV de la comisión " + Dinero.formatear(igv)
						+ ", neto " + Dinero.formatear(liquidacion.getTotalNeto()) + ".");
		if (!sinPago.isEmpty()) {
			auditoria.registrar(AccionAuditoria.LIQUIDACION_SIN_PAGO, "liquidacion_pasarela",
					liquidacion.getId().toString(), null, sinPago.size() + " cargo(s) sin pago",
					"La liquidación " + liquidacion.getReferencia() + " trae cargos que no corresponden a ningún pago en "
							+ "línea registrado: " + String.join("; ", sinPago) + ". Revisa con la pasarela.");
		}
		return Optional.of(liquidacion.getId());
	}
}
