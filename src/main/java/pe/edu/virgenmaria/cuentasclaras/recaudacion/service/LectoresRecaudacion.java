package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.config.PropiedadesRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.formato.AdaptadorRecaudacion;

import java.util.List;

/** Elige el adaptador del banco configurado que lee ese archivo (por su extensión). */
@Component
public class LectoresRecaudacion {

	private final List<AdaptadorRecaudacion> adaptadores;

	private final PropiedadesRecaudacion propiedades;

	public LectoresRecaudacion(List<AdaptadorRecaudacion> adaptadores, PropiedadesRecaudacion propiedades) {
		this.adaptadores = List.copyOf(adaptadores);
		this.propiedades = propiedades;
	}

	public AdaptadorRecaudacion para(String nombreArchivo) {
		List<AdaptadorRecaudacion> delBanco = adaptadores.stream().filter(a -> a.banco() == propiedades.banco()).toList();
		if (delBanco.isEmpty()) {
			throw new ArchivoNoValidoException("Todavía no está disponible el formato del banco "
					+ propiedades.banco().etiqueta() + ": usa el formato genérico.");
		}
		return delBanco.stream().filter(a -> a.acepta(nombreArchivo)).findFirst()
				.orElseThrow(() -> new ArchivoNoValidoException("Sube el archivo del banco en CSV (.csv o .txt) o en "
						+ "Excel (.xlsx)."));
	}
}
