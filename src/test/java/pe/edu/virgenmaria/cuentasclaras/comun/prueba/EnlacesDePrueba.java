package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor.BuzonSimulado;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint 5: el enlace de activación ya no lo ve quien da el acceso; llega al titular. En las pruebas, el titular es el
 * buzón de la mensajería SIMULADA: se corre una pasada del envío ({@code sistema.mensajeria}) y se lee lo que le habría
 * llegado a su celular o correo.
 */
public final class EnlacesDePrueba {

	public static final Pattern ENLACE = Pattern.compile("/activar/(\\d+)/([A-Za-z0-9_-]{43})");

	private EnlacesDePrueba() {
	}

	/**
	 * Envía los mensajes pendientes del colegio y devuelve la ruta {@code /activar/{colegio}/{token}} que llegó a
	 * {@code destino} (por ejemplo {@code +51987654321} o un correo).
	 */
	public static String recibido(DespachoMensajes despacho, BuzonSimulado buzon, long colegioId, String destino) {
		// El despacho envía de a 20 (el más antiguo primero): en una base con muchos pendientes, varias pasadas.
		for (int pasada = 0; pasada < 50 && despacho.despacharColegio(colegioId) > 0; pasada++) {
			// sigue hasta que no quede nada por enviar
		}
		return buzon.entregas().reversed().stream().filter(e -> e.destino().equals(destino) && e.sufijoBoton() != null
				&& e.sufijoBoton().contains("/activar/")).map(e -> ruta(e.sufijoBoton())).findFirst()
				.orElseThrow(() -> new AssertionError("No llegó ningún enlace a " + destino + ": " + buzon.entregas()));
	}

	public static String ruta(String texto) {
		Matcher m = ENLACE.matcher(texto);
		if (!m.find()) {
			throw new AssertionError("No hay un enlace de activación en: " + texto);
		}
		return m.group();
	}

	public static String token(String ruta) {
		Matcher m = ENLACE.matcher(ruta);
		if (!m.find()) {
			throw new AssertionError("No es un enlace de activación: " + ruta);
		}
		return m.group(2);
	}

	/**
	 * Correcciones del sprint 5 (S5-A1): envía los pendientes y devuelve la ruta {@code /verificar/{colegio}/{token}}
	 * que llegó a {@code destino} para confirmar ese contacto.
	 */
	public static String verificacionRecibida(DespachoMensajes despacho, BuzonSimulado buzon, long colegioId,
			String destino) {
		for (int pasada = 0; pasada < 50 && despacho.despacharColegio(colegioId) > 0; pasada++) {
			// sigue hasta que no quede nada por enviar
		}
		return buzon.entregas().reversed().stream().filter(e -> e.destino().equals(destino) && e.sufijoBoton() != null
				&& e.sufijoBoton().startsWith("/verificar/")).map(BuzonSimulado.Entrega::sufijoBoton).findFirst()
				.orElseThrow(() -> new AssertionError("No llegó ninguna verificación a " + destino + ": " + buzon.entregas()));
	}
}
