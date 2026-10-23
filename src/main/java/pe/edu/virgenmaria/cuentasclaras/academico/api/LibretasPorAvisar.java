package pe.edu.virgenmaria.cuentasclaras.academico.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 10, tanda 3 (sección 3.7): las libretas por avisar a las familias, por familia y periodo (los hermanos juntos). Lo
 * lee SOLO {@code comunicacion.proceso.AvisosLibretas} ({@code sistema.libretas}). Sin notas, conclusiones ni documentos:
 * el periodo, los primeros nombres y si la vigente de alguno es una versión corregida.
 */
public interface LibretasPorAvisar {

	/**
	 * Una familia y un periodo con libretas emitidas desde la hora pedida.
	 *
	 * @param nombres          los primeros nombres de los hijos con libreta en ese periodo
	 * @param corregida        si la vigente de alguno es la versión 2 o mayor (entonces el aviso es LIBRETA_CORREGIDA)
	 * @param ultimaLibretaId  la libreta más nueva de la familia en el periodo (la clave idempotente del aviso)
	 */
	record PorAvisar(long familiaId, long periodoId, String periodo, List<String> nombres, boolean corregida,
			long ultimaLibretaId) {

		public PorAvisar {
			nombres = List.copyOf(nombres);
		}
	}

	List<PorAvisar> emitidasDesde(LocalDateTime desde);
}
