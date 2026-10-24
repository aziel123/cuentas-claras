package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;

/**
 * Sprint 11, tanda 1c: el código que le llegó a una persona por WhatsApp (en las pruebas, lo que haría el envío: se deriva
 * de la clave del mensaje; el mensaje guarda solo su SHA-256).
 */
public final class CodigosDePrueba {

	private CodigosDePrueba() {
	}

	public static String codigo(JdbcTemplate jdbc, DerivadorSecreto derivador, long colegioId, Long mensajeId) {
		String clave = jdbc.queryForObject("SELECT clave FROM mensaje WHERE id = ?", String.class, mensajeId);
		return derivador.codigoPromotoria(colegioId, clave);
	}

	/** Lo que escribe la persona en la pantalla para ese pedido. */
	public static CodigoPromotoria.Escrito escrito(JdbcTemplate jdbc, DerivadorSecreto derivador, long colegioId,
			CodigoPromotoria.Pedido pedido) {
		return new CodigoPromotoria.Escrito(pedido.mensajeId(), codigo(jdbc, derivador, colegioId, pedido.mensajeId()));
	}

	/** Un código que no es el que llegó. */
	public static String otro(String codigo) {
		return codigo.equals("000000") ? "111111" : "000000";
	}
}
