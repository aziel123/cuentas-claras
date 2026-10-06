package pe.edu.virgenmaria.cuentasclaras.conciliacion.formato;

/**
 * Puerto: convierte el extracto de un banco (CSV, XLSX o TXT) a las filas del formato genérico. Un adaptador por
 * formato; el del banco del colegio se construye con un archivo de ejemplo anonimizado y su prueba. Solo lee: no guarda
 * nada ni consulta la base. El genérico exige la columna SALDO: sin saldo no se puede probar la continuidad.
 */
public interface AdaptadorExtracto {

	/** Si lee este archivo (por su extensión). */
	boolean acepta(String nombreArchivo);

	/**
	 * Lee el archivo. Un archivo ilegible lanza {@code ArchivoNoValidoException}; un dato mal escrito queda en
	 * {@code errores} con su línea.
	 */
	LecturaExtracto leer(String nombreArchivo, byte[] archivo);
}
