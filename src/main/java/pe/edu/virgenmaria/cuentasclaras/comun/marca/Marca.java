package pe.edu.virgenmaria.cuentasclaras.comun.marca;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sprint 8, tanda 1 (sección 3.7, decisión 122): el nombre comercial del producto vive en UN solo parámetro,
 * {@code cuentasclaras.marca.nombre} ({@code CC_MARCA_NOMBRE}; en prod es obligatorio). De aquí salen el título, la
 * cabecera, el pie y el ingreso de las páginas, los asuntos y el pie de los correos y las plantillas de mensajes. Los
 * datos de la empresa proveedora (razón social, RUC y soporte) van en el contrato y en el aviso de privacidad.
 * <p>
 * Lo que NO cambia con la marca: el paquete Java, el artefacto, la base {@code cuentasclaras}, los usuarios {@code cc_*},
 * la cookie y los mensajes de los triggers (identificadores internos).
 */
@ConfigurationProperties("cuentasclaras.marca")
public record Marca(@DefaultValue(Marca.NOMBRE_POR_DEFECTO) String nombre, @DefaultValue("") String empresa,
		@DefaultValue("") String rucEmpresa, @DefaultValue("") String soporteCorreo,
		@DefaultValue("") String soporteWhatsapp) {

	/** El ÚNICO lugar del código con el nombre comercial actual (lo comprueba {@code MarcaUnicaTest}). */
	public static final String NOMBRE_POR_DEFECTO = "Cuentas Claras";

	public Marca {
		nombre = nombre == null || nombre.isBlank() ? NOMBRE_POR_DEFECTO : nombre.strip();
		if (nombre.length() > 40) {
			throw new IllegalArgumentException("cuentasclaras.marca.nombre: hasta 40 caracteres");
		}
		empresa = empresa == null ? "" : empresa.strip();
		rucEmpresa = rucEmpresa == null ? "" : rucEmpresa.strip();
		soporteCorreo = soporteCorreo == null ? "" : soporteCorreo.strip();
		soporteWhatsapp = soporteWhatsapp == null ? "" : soporteWhatsapp.strip();
	}

	/** La marca de pruebas y desarrollo (sin leer la configuración). */
	public static Marca porDefecto() {
		return new Marca(NOMBRE_POR_DEFECTO, "", "", "", "");
	}
}
