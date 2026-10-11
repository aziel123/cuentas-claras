package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;

/**
 * Sprint 8, tanda 2: la «prueba» de una cuenta bancaria. No hay a qué conectarse: se comprueba el formato (número y CCI de
 * 20 dígitos) y se recuerda a quien aprueba que confirme la cuenta con el banco (nunca con quien la propuso). El control
 * de fondo es la conciliación del sprint 4: lo que no llega al extracto de ESTA cuenta sale en rojo.
 */
@Component
public class PruebaCuentaBancaria implements AdaptadorConector {

	@Override
	public TipoConector tipo() {
		return TipoConector.CUENTA_BANCARIA;
	}

	@Override
	public boolean admite(ProveedorConector proveedor) {
		return TipoConector.CUENTA_BANCARIA.proveedores().contains(proveedor);
	}

	@Override
	public ResultadoPrueba probar(ConectorVigente candidato) {
		ConfiguracionConector.CuentaBancaria cuenta = candidato.configuracionDe(ConfiguracionConector.CuentaBancaria.class);
		if (cuenta.numero() == null || !cuenta.numero().matches("[0-9]{10,20}") || cuenta.cci() == null
				|| !cuenta.cci().matches("[0-9]{20}")) {
			return ResultadoPrueba.fallida("El número o el CCI no tienen el formato del banco.");
		}
		return ResultadoPrueba.correcta("Número y CCI con formato correcto. Quien aprueba confirma la cuenta con el banco.");
	}
}
