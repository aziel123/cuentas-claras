package pe.edu.virgenmaria.cuentasclaras.academico.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 10, tanda 3 (sección 3.7): las libretas por avisar a las familias (los hermanos juntos). Lo lee SOLO
 * {@code comunicacion.proceso.AvisosLibretas} ({@code sistema.libretas}). Sin notas, conclusiones ni documentos: el periodo,
 * los primeros nombres y si alguna es una versión corregida.
 * <p>
 * Correcciones del sprint 10 (hallazgo 4 y P-5): UN aviso por familia por OLEADA de emisión. Las libretas de una familia
 * emitidas con menos de la ventana de agrupación entre una y otra (hermanos de secciones que se emiten en pasadas
 * distintas, o una corrección de un periodo anterior que emite también las versiones de los periodos siguientes) van en un
 * solo aviso, que sale cuando la oleada lleva una ventana sin libretas nuevas.
 */
public interface LibretasPorAvisar {

	/**
	 * Una oleada de libretas de una familia.
	 *
	 * @param periodoId        el periodo de la libreta más nueva de la oleada
	 * @param periodo          los nombres de los periodos de la oleada («I bimestre y II bimestre»)
	 * @param nombres          los primeros nombres de los hijos con libreta en la oleada
	 * @param corregida        si alguna es la versión 2 o mayor (entonces el aviso es LIBRETA_CORREGIDA)
	 * @param ultimaLibretaId  la libreta más nueva de la oleada (la clave idempotente del aviso)
	 */
	record PorAvisar(long familiaId, long periodoId, String periodo, List<String> nombres, boolean corregida,
			long ultimaLibretaId) {

		public PorAvisar {
			nombres = List.copyOf(nombres);
		}
	}

	/** Las oleadas emitidas desde {@code desde} que ya terminaron a la hora {@code ahora}. */
	List<PorAvisar> emitidasDesde(LocalDateTime desde, LocalDateTime ahora);
}
