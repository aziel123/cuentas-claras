package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CorreccionRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DescuentoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ModalidadDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.util.List;

/** Ayudas para las pruebas de anulaciones y descuentos (sprint 3, tanda 2). */
public final class EscenarioAprobaciones {

	public static final String MOTIVO_ANULACION = "Se cobró a la familia equivocada en ventanilla";

	private EscenarioAprobaciones() {
	}

	/** La solicitud pendiente de esa entidad (pago, descuento...). */
	public static Long pendiente(JdbcTemplate jdbc, String entidad, Long entidadId) {
		return jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE estado = 'PENDIENTE' AND entidad = ? "
				+ "AND entidad_id = ?", Long.class, entidad, entidadId);
	}

	/**
	 * Aprueba como esa persona la solicitud pendiente de la entidad; la sesión queda en esa persona. Si la bandeja pide
	 * llamadas (A2), confirma «Hablé con el apoderado» con el celular registrado de cada familia.
	 */
	public static void aprueba(UsuarioAutenticado quien, BandejaAprobaciones bandeja, JdbcTemplate jdbc, String entidad,
			Long entidadId) {
		UsuariosDePrueba.iniciarSesion(quien);
		Long id = pendiente(jdbc, entidad, entidadId);
		List<String> telefonos = telefonosPorLlamar(bandeja, jdbc, id);
		bandeja.aprobar(id, null, !telefonos.isEmpty(), telefonos);
	}

	/** El celular registrado de un apoderado de cada familia que la bandeja pide llamar (vacío si no pide). */
	public static List<String> telefonosPorLlamar(BandejaAprobaciones bandeja, JdbcTemplate jdbc, Long solicitud) {
		return bandeja.bandeja().pendientes().stream().filter(s -> s.id().equals(solicitud)).findFirst()
				.map(s -> s.llamadas().stream().map(familia -> telefonoDe(jdbc, familia)).toList()).orElse(List.of());
	}

	/** El primer celular registrado de un apoderado de la familia con ese nombre. */
	public static String telefonoDe(JdbcTemplate jdbc, String familia) {
		return jdbc.queryForObject("SELECT a.telefono_whatsapp FROM apoderado a JOIN familia f ON f.id = a.familia_id "
				+ "WHERE f.nombre = ? AND a.telefono_whatsapp IS NOT NULL ORDER BY a.id LIMIT 1", String.class, familia);
	}

	public static DescuentoRequest descuento(Long alumno, TipoDescuento tipo, String porcentaje, List<Long> cuotas) {
		return new DescuentoRequest(alumno, tipo, ModalidadDescuento.PORCENTAJE, new BigDecimal(porcentaje), cuotas,
				"Descuento del reglamento de pensiones para la familia", "Reglamento de pensiones 2027, art. 12");
	}

	public static CorreccionRequest correccion(Long familia, List<Long> cuotas) {
		return new CorreccionRequest(familia, cuotas, MOTIVO_ANULACION);
	}
}
