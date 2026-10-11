package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.ConectoresColegio;
import pe.edu.virgenmaria.cuentasclaras.pasarela.config.PropiedadesPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * La pasarela de pago en línea de cada colegio.
 * <ul>
 *   <li>Sprint 8, tanda 2 (sección 3.6, H4): si el colegio tiene un conector PASARELA vigente, la suya: el adaptador de
 *       su proveedor (si existe en este perfil; los reales llegan con su contrato, decisión 22), con SU secreto de los
 *       avisos y SU monto máximo. Si su adaptador no existe, el colegio no tiene pago en línea.</li>
 *   <li>Sin conector, la del entorno ({@code cuentasclaras.pasarela.proveedor}, NINGUNA por defecto: sin pago en línea).
 *       En una instalación COMPARTIDA el entorno solo admite NINGUNA o la SIMULADA ({@code VerificadorConfiguracion}).</li>
 * </ul>
 * Si se configura en el entorno una pasarela cuyo adaptador no existe en este perfil (por ejemplo SIMULADA en prod, donde
 * su bean no existe), la aplicación no arranca.
 */
@Component
public class Pasarelas {

	private final PasarelaPagos delEntorno;

	private final List<PasarelaPagos> adaptadores;

	private final ConectoresColegio conectores;

	private final PropiedadesPasarela propiedades;

	public Pasarelas(PropiedadesPasarela propiedades, List<PasarelaPagos> adaptadores, ConectoresColegio conectores) {
		this.propiedades = propiedades;
		this.adaptadores = List.copyOf(adaptadores);
		this.conectores = conectores;
		String configurado = propiedades.proveedorNormalizado();
		if ("NINGUNA".equals(configurado)) {
			this.delEntorno = null;
			return;
		}
		ProveedorPasarela proveedor;
		try {
			proveedor = ProveedorPasarela.valueOf(configurado);
		}
		catch (IllegalArgumentException e) {
			throw new IllegalStateException("cuentasclaras.pasarela.proveedor no válido: " + configurado);
		}
		this.delEntorno = adaptadores.stream().filter(a -> a.proveedor() == proveedor).findFirst()
				.orElseThrow(() -> new IllegalStateException("La pasarela " + proveedor + " está configurada pero su "
						+ "adaptador no existe en este perfil. Usa NINGUNA o el adaptador probado."));
	}

	/** La pasarela activa del colegio en contexto; si no hay, el pago en línea no está disponible. */
	public PasarelaPagos activa() {
		return configurada().orElseThrow(() -> new ReglaNegocioException("El pago en línea todavía no está disponible en "
				+ "el colegio. Puedes pagar en caja o en el banco con el código de pago de tu hijo."));
	}

	/** La pasarela del colegio en contexto: la de su conector vigente o, sin conector, la del entorno. */
	public Optional<PasarelaPagos> configurada() {
		Optional<ConectorVigente> conector = conectores.vigente(TipoConector.PASARELA);
		if (conector.isPresent()) {
			return adaptador(proveedorDe(conector.get().proveedor()));
		}
		return Optional.ofNullable(delEntorno);
	}

	/** El adaptador de ese proveedor (para avisos y consultas de órdenes ya creadas). */
	public PasarelaPagos de(ProveedorPasarela proveedor) {
		return adaptador(proveedor).filter(a -> delEntorno == a || conectores.vigente(TipoConector.PASARELA)
				.map(c -> proveedorDe(c.proveedor()) == proveedor).orElse(false))
				.orElseThrow(() -> new RecursoNoEncontradoException("Pasarela no disponible"));
	}

	/** El adaptador de ese proveedor en este perfil, si existe (lo usa el aviso que llega por la clave pública). */
	public Optional<PasarelaPagos> adaptador(ProveedorPasarela proveedor) {
		return adaptadores.stream().filter(a -> a.proveedor() == proveedor).findFirst();
	}

	/** Si el colegio en contexto tiene un conector de pasarela vigente (sus avisos llegan por su clave pública). */
	public Optional<ConectorVigente> conectorVigente() {
		return conectores.vigente(TipoConector.PASARELA);
	}

	/** El monto máximo por orden del colegio en contexto: el de su conector o el del entorno. */
	public BigDecimal montoMaximo() {
		return conectores.vigente(TipoConector.PASARELA)
				.map(c -> c.configuracionDe(ConfiguracionConector.Pasarela.class).montoMaximo())
				.orElse(propiedades.montoMaximo());
	}

	public boolean simulada() {
		return configurada().map(p -> p.proveedor() == ProveedorPasarela.SIMULADA).orElse(false);
	}

	/** El proveedor de la pasarela de un conector (el SIMULADO del conector es la pasarela SIMULADA). */
	public static ProveedorPasarela proveedorDe(ProveedorConector proveedor) {
		return switch (proveedor) {
			case SIMULADO -> ProveedorPasarela.SIMULADA;
			case CULQI -> ProveedorPasarela.CULQI;
			case NIUBIZ -> ProveedorPasarela.NIUBIZ;
			case IZIPAY -> ProveedorPasarela.IZIPAY;
			default -> throw new IllegalArgumentException(proveedor + " no es una pasarela");
		};
	}
}
