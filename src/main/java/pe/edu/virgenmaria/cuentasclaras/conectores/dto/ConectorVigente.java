package pe.edu.virgenmaria.cuentasclaras.conectores.dto;

import pe.edu.virgenmaria.cuentasclaras.conectores.model.ModoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;

import java.util.Map;
import java.util.Objects;

/**
 * Sprint 8, tanda 2 (sección 3.6): el conector VIGENTE de un colegio, ya descifrado, como lo usan los módulos que mueven
 * el dinero o emiten (comprobantes, pasarela, caja, recaudación y conciliación). Lo entrega {@code ConectoresColegio}, que
 * lo guarda en caché por {@code (id, version)}. Nunca va a una pantalla ni a un log: {@link #toString()} no lleva los
 * secretos.
 *
 * @param secretos los secretos con nombre ({@link #TOKEN}; {@link #LLAVE_SECRETA} y {@link #SECRETO_AVISO}); vacío si el
 *                 tipo no tiene secreto
 */
public record ConectorVigente(Long id, Long version, Long colegioId, TipoConector tipo, ProveedorConector proveedor,
		ModoConector modo, String clavePublica, ConfiguracionConector configuracion, Map<String, String> secretos) {

	/** El token del OSE (Nubefact). */
	public static final String TOKEN = "token";

	/** La llave secreta de la pasarela (consulta de cobros y reembolsos). */
	public static final String LLAVE_SECRETA = "llaveSecreta";

	/** El secreto con que la pasarela firma sus avisos (webhooks). */
	public static final String SECRETO_AVISO = "secretoAviso";

	public ConectorVigente {
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(configuracion, "configuracion");
		secretos = secretos == null ? Map.of() : Map.copyOf(secretos);
	}

	public String secreto(String nombre) {
		return secretos.get(nombre);
	}

	@SuppressWarnings("unchecked")
	public <T extends ConfiguracionConector> T configuracionDe(Class<T> clase) {
		return (T) clase.cast(configuracion);
	}

	@Override
	public String toString() {
		return "ConectorVigente[id=" + id + ", version=" + version + ", colegio=" + colegioId + ", tipo=" + tipo
				+ ", proveedor=" + proveedor + ", modo=" + modo + "]";
	}
}
