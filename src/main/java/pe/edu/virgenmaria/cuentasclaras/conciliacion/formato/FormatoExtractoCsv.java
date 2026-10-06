package pe.edu.virgenmaria.cuentasclaras.conciliacion.formato;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.LectorCsv;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.ValidadorArchivoPlano;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.config.PropiedadesConciliacion;

import java.util.List;

/** Extracto en el formato genérico, en CSV ({@code ;} o {@code ,}, UTF-8 o ISO-8859-1) o TXT con el mismo contenido. */
@Component
public class FormatoExtractoCsv extends FormatoExtractoGenerico {

	public static final String FORMATO = "GENERICO_CSV";

	private final PropiedadesConciliacion propiedades;

	public FormatoExtractoCsv(PropiedadesConciliacion propiedades) {
		this.propiedades = propiedades;
	}

	@Override
	public boolean acepta(String nombreArchivo) {
		return ValidadorArchivoPlano.esTextoPlano(nombreArchivo);
	}

	@Override
	public LecturaExtracto leer(String nombreArchivo, byte[] archivo) {
		// Preámbulo + cabecera + movimientos.
		int maximo = propiedades.maximoLineas() + 12;
		String texto = ValidadorArchivoPlano.validar(nombreArchivo, archivo, maximo);
		List<FilaCruda> filas = LectorCsv.leer(texto, maximo).stream().map(f -> new FilaCruda(f.linea(), f.celdas()))
				.toList();
		return convertir(FORMATO, filas);
	}
}
