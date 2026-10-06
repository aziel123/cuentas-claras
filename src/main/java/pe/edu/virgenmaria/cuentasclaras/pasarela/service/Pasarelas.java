package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.pasarela.config.PropiedadesPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;

import java.util.List;
import java.util.Optional;

/**
 * La pasarela configurada ({@code cuentasclaras.pasarela.proveedor}). NINGUNA por defecto: sin pago en línea. Si se
 * configura una pasarela cuyo adaptador no existe en este perfil (por ejemplo SIMULADA en prod, donde su bean no
 * existe), la aplicación no arranca.
 */
@Component
public class Pasarelas {

	private final PasarelaPagos activa;

	public Pasarelas(PropiedadesPasarela propiedades, List<PasarelaPagos> adaptadores) {
		String configurado = propiedades.proveedorNormalizado();
		if ("NINGUNA".equals(configurado)) {
			this.activa = null;
			return;
		}
		ProveedorPasarela proveedor;
		try {
			proveedor = ProveedorPasarela.valueOf(configurado);
		}
		catch (IllegalArgumentException e) {
			throw new IllegalStateException("cuentasclaras.pasarela.proveedor no válido: " + configurado);
		}
		this.activa = adaptadores.stream().filter(a -> a.proveedor() == proveedor).findFirst()
				.orElseThrow(() -> new IllegalStateException("La pasarela " + proveedor + " está configurada pero su "
						+ "adaptador no existe en este perfil. Usa NINGUNA o el adaptador probado."));
	}

	/** La pasarela activa; si no hay, el pago en línea no está disponible. */
	public PasarelaPagos activa() {
		if (activa == null) {
			throw new ReglaNegocioException("El pago en línea todavía no está disponible en el colegio. Puedes pagar en "
					+ "caja o en el banco con el código de pago de tu hijo.");
		}
		return activa;
	}

	public Optional<PasarelaPagos> configurada() {
		return Optional.ofNullable(activa);
	}

	/** El adaptador de ese proveedor (para avisos y consultas de órdenes ya creadas). */
	public PasarelaPagos de(ProveedorPasarela proveedor) {
		if (activa == null || activa.proveedor() != proveedor) {
			throw new RecursoNoEncontradoException("Pasarela no disponible");
		}
		return activa;
	}

	public boolean simulada() {
		return activa != null && activa.proveedor() == ProveedorPasarela.SIMULADA;
	}
}
