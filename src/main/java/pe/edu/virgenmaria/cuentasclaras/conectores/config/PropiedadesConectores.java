package pe.edu.virgenmaria.cuentasclaras.conectores.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cuentasclaras.conectores} (sprint 8, tanda 2; sección 3.6, decisión 118).
 *
 * @param clave            la clave maestra de los secretos de los conectores, 32 bytes en base64
 *                         ({@code CC_CLAVE_CONECTORES}, del gestor de secretos). Obligatoria en una instalación COMPARTIDA;
 *                         en una DEDICADA, sin ella no se puede proponer un conector con secreto.
 * @param claveVersion     su versión ({@code CC_CLAVE_CONECTORES_VERSION}); cada secreto guarda la versión con que se cifró
 * @param claveAnterior    la clave anterior, solo durante una rotación ({@code CC_CLAVE_CONECTORES_ANTERIOR}): su versión es
 *                         {@code claveVersion - 1}. La usa {@code java -jar ... recifrar-conectores}
 * @param avisoCambioDias  días que la alerta CRÍTICA de un cambio de conector queda en «Para revisar»
 */
@ConfigurationProperties("cuentasclaras.conectores")
public record PropiedadesConectores(@DefaultValue("") String clave, @DefaultValue("1") int claveVersion,
		@DefaultValue("") String claveAnterior, @DefaultValue("7") int avisoCambioDias) {

	public PropiedadesConectores {
		clave = clave == null ? "" : clave.strip();
		claveAnterior = claveAnterior == null ? "" : claveAnterior.strip();
		if (claveVersion < 1 || claveVersion > 1000) {
			throw new IllegalArgumentException("cuentasclaras.conectores.clave-version (CC_CLAVE_CONECTORES_VERSION) debe "
					+ "estar entre 1 y 1000");
		}
		if (!claveAnterior.isEmpty() && claveVersion < 2) {
			throw new IllegalArgumentException("Con CC_CLAVE_CONECTORES_ANTERIOR, la versión de la clave nueva "
					+ "(CC_CLAVE_CONECTORES_VERSION) es 2 o más: la anterior es la versión de antes");
		}
		if (avisoCambioDias < 1 || avisoCambioDias > 60) {
			throw new IllegalArgumentException("cuentasclaras.conectores.aviso-cambio-dias debe estar entre 1 y 60");
		}
	}

	public boolean conClave() {
		return !clave.isEmpty();
	}

	@Override
	public String toString() {
		// Sin las claves: este texto puede terminar en un log.
		return "PropiedadesConectores[claveVersion=" + claveVersion + ", conClave=" + conClave() + ", conAnterior="
				+ !claveAnterior.isEmpty() + ", avisoCambioDias=" + avisoCambioDias + "]";
	}
}
