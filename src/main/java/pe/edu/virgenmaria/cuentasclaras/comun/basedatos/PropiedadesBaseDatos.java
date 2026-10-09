package pe.edu.virgenmaria.cuentasclaras.comun.basedatos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cuentasclaras.basedatos} (sprint 7, tanda 2; sección 11 del diseño).
 *
 * @param sistema    el segundo pool: procesos {@code sistema.*} e identidad ({@code cc_sistema})
 * @param exigirTls  en prod, la conexión a MySQL debe ir cifrada (H11); solo la instalación local en Docker lo apaga
 */
@ConfigurationProperties("cuentasclaras.basedatos")
public record PropiedadesBaseDatos(@DefaultValue Sistema sistema, @DefaultValue("true") boolean exigirTls) {

	/**
	 * @param usuario     {@code DB_SISTEMA_USUARIO}; vacío en dev y test (las dos rutas usan el mismo pool). En prod y
	 *                    piloto es obligatorio ({@code VerificadorConfiguracion}).
	 * @param clave       {@code DB_SISTEMA_CLAVE}
	 * @param poolMaximo  conexiones como máximo (los procesos y los ingresos son pocos)
	 */
	public record Sistema(String usuario, String clave, @DefaultValue("4") int poolMaximo) {

		public boolean configurado() {
			return usuario != null && !usuario.isBlank();
		}
	}
}
