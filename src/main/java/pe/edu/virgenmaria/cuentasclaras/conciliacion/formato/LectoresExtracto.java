package pe.edu.virgenmaria.cuentasclaras.conciliacion.formato;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;

import java.util.List;

/** Elige el adaptador que lee el extracto (por su extensión). */
@Component
public class LectoresExtracto {

	private final List<AdaptadorExtracto> adaptadores;

	public LectoresExtracto(List<AdaptadorExtracto> adaptadores) {
		this.adaptadores = List.copyOf(adaptadores);
	}

	public AdaptadorExtracto para(String nombreArchivo) {
		return adaptadores.stream().filter(a -> a.acepta(nombreArchivo)).findFirst()
				.orElseThrow(() -> new ArchivoNoValidoException("Sube el extracto del banco en CSV (.csv o .txt) o en "
						+ "Excel (.xlsx)."));
	}
}
