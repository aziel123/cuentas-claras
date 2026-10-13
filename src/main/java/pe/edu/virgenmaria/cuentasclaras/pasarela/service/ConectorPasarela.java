package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.AdaptadorConector;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;

import java.util.List;

/**
 * Sprint 8, tanda 2 (sección 3.6): el conector de la pasarela visto desde el pago en línea. Solo se prueba (y por lo tanto
 * solo entra en vigencia) un proveedor cuyo adaptador existe en este perfil: hoy, la pasarela SIMULADA en dev, test y
 * piloto (que además exige la fila {@code pasarela_simulada} del DBA para crear órdenes). Culqi, Niubiz e Izipay se
 * registran, pero no pasan la prueba hasta tener su adaptador (decisión 22).
 */
@Component
public class ConectorPasarela implements AdaptadorConector {

	private final List<PasarelaPagos> adaptadores;

	public ConectorPasarela(List<PasarelaPagos> adaptadores) {
		this.adaptadores = List.copyOf(adaptadores);
	}

	@Override
	public TipoConector tipo() {
		return TipoConector.PASARELA;
	}

	@Override
	public boolean admite(ProveedorConector proveedor) {
		ProveedorPasarela pasarela = Pasarelas.proveedorDe(proveedor);
		return adaptadores.stream().anyMatch(a -> a.proveedor() == pasarela);
	}

	@Override
	public ResultadoPrueba probar(ConectorVigente candidato) {
		String secreto = candidato.secreto(ConectorVigente.SECRETO_AVISO);
		if (secreto == null || secreto.length() < 16) {
			return ResultadoPrueba.fallida("Falta el secreto con que la pasarela firma sus avisos.");
		}
		if (candidato.proveedor() == ProveedorConector.SIMULADO) {
			return ResultadoPrueba.correcta("La pasarela simulada no mueve dinero real; sus avisos se firmarán con el secreto "
					+ "guardado y llegarán a la ruta de este conector.");
		}
		return ResultadoPrueba.correcta("El adaptador de " + candidato.proveedor().etiqueta() + " está disponible.");
	}
}
