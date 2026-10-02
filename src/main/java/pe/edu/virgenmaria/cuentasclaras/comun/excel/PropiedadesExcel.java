package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Límites de los archivos Excel que se suben ({@code cuentasclaras.excel.*}).
 *
 * @param maxBytes         tamaño máximo del archivo subido (lo valida la aplicación con un mensaje claro)
 * @param maxFilas         filas de datos como máximo (sin contar los encabezados)
 * @param maxDescomprimido suma máxima de las entradas del zip ya descomprimidas (contra zip bombs)
 * @param maxEntradasZip   cantidad máxima de entradas del zip
 */
@ConfigurationProperties("cuentasclaras.excel")
public record PropiedadesExcel(@DefaultValue("2MB") DataSize maxBytes, @DefaultValue("2000") int maxFilas,
		@DefaultValue("20MB") DataSize maxDescomprimido, @DefaultValue("200") int maxEntradasZip) {

	public PropiedadesExcel {
		if (maxBytes == null || maxBytes.toBytes() <= 0 || maxFilas < 1 || maxDescomprimido == null
				|| maxDescomprimido.toBytes() <= 0 || maxEntradasZip < 1) {
			throw new IllegalArgumentException("cuentasclaras.excel: los límites deben ser positivos");
		}
	}

	public static PropiedadesExcel porDefecto() {
		return new PropiedadesExcel(DataSize.ofMegabytes(2), 2000, DataSize.ofMegabytes(20), 200);
	}
}
