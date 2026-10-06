package pe.edu.virgenmaria.cuentasclaras.recaudacion.formato;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.LectorCsv;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.ValidadorArchivoPlano;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.config.PropiedadesRecaudacion;

import java.time.LocalDate;
import java.util.List;

/** Formato genérico en CSV ({@code ;} o {@code ,}, UTF-8 o ISO-8859-1) o TXT con el mismo contenido. */
@Component
public class FormatoGenericoCsv extends FormatoGenerico {

	public static final String FORMATO = "GENERICO_CSV";

	private final PropiedadesRecaudacion propiedades;

	public FormatoGenericoCsv(PropiedadesRecaudacion propiedades) {
		this.propiedades = propiedades;
	}

	@Override
	public boolean acepta(String nombreArchivo) {
		return ValidadorArchivoPlano.esTextoPlano(nombreArchivo);
	}

	@Override
	public LecturaRecaudacion leer(String nombreArchivo, byte[] archivo, LocalDate hoy) {
		// Cabecera + líneas + pie.
		int maximo = propiedades.maximoLineas() + 2;
		String texto = ValidadorArchivoPlano.validar(nombreArchivo, archivo, maximo);
		List<FilaCruda> filas = LectorCsv.leer(texto, maximo).stream()
				.map(f -> new FilaCruda(f.linea(), f.celdas())).toList();
		return convertir(FORMATO, filas, hoy);
	}
}
