package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comun.marca.Marca;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

/**
 * Sprint 9, tanda 3 (decisión 156, E22): el pie FIJO de todo comunicado, en el portal, el correo y la impresión. El padre,
 * otra vez, como auditor: un comunicado que pide un Yape es fraude por definición. No va en los parámetros del WhatsApp
 * (dice «pagos» y la regla de cobranza de {@code trg_mensaje_nace} lo rechazaría): va en la plantilla del correo y en la
 * pantalla. La marca sale de {@code CC_MARCA_NOMBRE}.
 */
@Component
public class PieComunicado {

	private final ColegioService colegios;

	private final Marca marca;

	public PieComunicado(ColegioService colegios, Marca marca) {
		this.colegios = colegios;
		this.marca = marca;
	}

	/** El pie del colegio en contexto. */
	public String texto() {
		String colegio = colegios.nombreParaMensajes(ContextoColegio.actual()).replace('\n', ' ').strip();
		return colegio + " nunca le pedirá pagos ni datos personales por un comunicado. Los pagos se hacen solo en el "
				+ "portal de " + marca.nombre() + ", en el banco con el código del alumno o en la caja del colegio. Si algo "
				+ "le parece raro, avísenos desde el portal.";
	}
}
