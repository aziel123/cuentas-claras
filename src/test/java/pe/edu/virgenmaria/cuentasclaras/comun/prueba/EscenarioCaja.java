package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Datos para las pruebas de caja (sprint 3): dos familias con cuotas 2027 generadas por el plan de Primaria (S/ 450 de
 * pensión, S/ 350 de matrícula) y dos cajeras. Con el reloj de {@link ConfiguracionRelojAjustable} hoy es el 02/10/2026.
 */
public final class EscenarioCaja {

	public static final UsuarioAutenticado CAJA = EscenarioCobranza.CAJA;

	public static final UsuarioAutenticado CAJA_2 = EscenarioCobranza.persona(18, "caja2", Rol.CAJA);

	public static final String DNI_SEBASTIAN = "76902114";

	public static final String DNI_PEDRO = "41234567";

	/** Familia Quispe Huamán (Mateo y Valeria, responsable Rosa) y familia Flores Rojas (Sebastián, responsable Pedro). */
	public record Familias(Long quispe, Long mateo, Long valeria, Long rosa, Long flores, Long sebastian, Long pedro,
			Long anio2027) {
	}

	private EscenarioCaja() {
	}

	/** Lo arma Administración y lo aprueba Dirección; deja la sesión en Administración. */
	public static Familias preparar(ServicioEstructura estructura, ServicioAlumnos alumnos, ServicioPlanesPension planes,
			JdbcTemplate jdbc) {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		EscenarioEscolar.Estructura escuela = EscenarioEscolar.crearEstructura(estructura);
		var mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027()));
		var valeria = alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(escuela.primaria6A2027()));
		var sebastian = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(DNI_SEBASTIAN, "Flores", "Rojas", "Sebastián",
				LocalDate.of(2014, 8, 21), DNI_PEDRO, "Flores", "Díaz", "Pedro", "912345678", null,
				escuela.primaria6A2027()));
		EscenarioCobranza.planAprobado(planes, escuela.anio2027(), 2027, Nivel.PRIMARIA, "450", "350", null);
		Long rosa = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class, mateo.alumnoId());
		Long pedro = jdbc.queryForObject("SELECT responsable_pago_id FROM alumno WHERE id = ?", Long.class,
				sebastian.alumnoId());
		return new Familias(mateo.familiaId(), mateo.alumnoId(), valeria.alumnoId(), rosa, sebastian.familiaId(),
				sebastian.alumnoId(), pedro, escuela.anio2027());
	}

	/** Id de la cuota de esa obligación («PEN-2027-03», «MAT-2027») del alumno. */
	public static Long cuota(JdbcTemplate jdbc, Long alumnoId, String obligacion) {
		return jdbc.queryForObject("SELECT id FROM cuota WHERE alumno_id = ? AND obligacion = ?", Long.class, alumnoId,
				obligacion);
	}

	public static String estado(JdbcTemplate jdbc, Long cuotaId) {
		return jdbc.queryForObject("SELECT estado FROM cuota WHERE id = ?", String.class, cuotaId);
	}

	public static BigDecimal pagado(JdbcTemplate jdbc, Long cuotaId) {
		return jdbc.queryForObject("SELECT monto_pagado FROM cuota WHERE id = ?", BigDecimal.class, cuotaId);
	}

	/** Cobro en efectivo con boleta al responsable de pago (por defecto). */
	public static CobroRequest efectivo(Long familia, List<Long> cuotas, String totalVisto, String recibido) {
		return new CobroRequest(UUID.randomUUID(), familia, cuotas, MedioPago.EFECTIVO, null, new BigDecimal(recibido), null,
				new BigDecimal(totalVisto), TipoComprobante.BOLETA, null, null, null);
	}

	public static CobroRequest digital(Long familia, List<Long> cuotas, MedioPago medio, String operacion,
			String totalVisto) {
		return new CobroRequest(UUID.randomUUID(), familia, cuotas, medio, operacion, null, null, new BigDecimal(totalVisto),
				TipoComprobante.BOLETA, null, null, null);
	}
}
