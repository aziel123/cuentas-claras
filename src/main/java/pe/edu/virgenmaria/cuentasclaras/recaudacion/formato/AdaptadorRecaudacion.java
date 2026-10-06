package pe.edu.virgenmaria.cuentasclaras.recaudacion.formato;

import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.BancoRecaudacion;

import java.time.LocalDate;

/**
 * Puerto: convierte el archivo de recaudación de un banco (CSV, XLSX o TXT de ancho fijo) a las filas del formato
 * genérico. Un adaptador por banco; el del banco del colegio se construye con un archivo de ejemplo anonimizado y su
 * prueba. Solo lee: no guarda nada ni consulta la base.
 */
public interface AdaptadorRecaudacion {

	BancoRecaudacion banco();

	/** Si lee este archivo (por su extensión). */
	boolean acepta(String nombreArchivo);

	/**
	 * Lee el archivo. Un archivo ilegible (sin las columnas, binario, demasiado grande) lanza
	 * {@code ArchivoNoValidoException}; una fila con un dato mal escrito queda en {@code errores} con su línea.
	 *
	 * @param hoy fecha de hoy (Lima): un pago con fecha posterior es un error
	 */
	LecturaRecaudacion leer(String nombreArchivo, byte[] archivo, LocalDate hoy);
}
