package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto;

import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.GuiaVista.PasoGuia;

/**
 * Sprint 11, tanda 1b (sección 3.14, punto 4): una fila de la Puesta en marcha. {@code estado} es el texto del badge
 * («Hecho», «Pendiente», «Opcional», «Bloqueado: primero el paso 3», «Lo hace la plataforma»), que nunca depende solo del
 * color; {@code variante} es su color (exito, alerta, neutro o info). {@code siguiente}: lleva la marca «Siguiente».
 */
public record FilaPaso(PuntoLista punto, PasoGuia vista, boolean siguiente, String estado, String variante) {

	public boolean hecho() {
		return punto.hecho();
	}
}
