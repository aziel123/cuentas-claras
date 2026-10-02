package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;

/**
 * Personas y planes para las pruebas de cobranza (sprint 2, tanda 3). Cada persona tiene su nombre de usuario: es lo
 * que la base compara en los CHECK de doble control ({@code creado_por}, {@code aprobado_por}...).
 */
public final class EscenarioCobranza {

	public static final UsuarioAutenticado ADMINISTRACION = persona(10, "administracion", Rol.ADMINISTRACION);

	public static final UsuarioAutenticado ADMINISTRACION_2 = persona(14, "administracion2", Rol.ADMINISTRACION);

	public static final UsuarioAutenticado DIRECCION = persona(11, "director", Rol.DIRECTOR);

	public static final UsuarioAutenticado PROMOTORIA = persona(12, "promotor", Rol.PROMOTOR);

	/** Promotoría que además hace trabajo de Administración (combinación permitida por ReglasSegregacion). */
	public static final UsuarioAutenticado PROMOTORIA_Y_ADMINISTRACION = persona(13, "promotora.adm", Rol.PROMOTOR,
			Rol.ADMINISTRACION);

	/** Solo en sesión: ReglasSegregacion no permite crear este usuario, pero el servicio no debe depender de eso. */
	public static final UsuarioAutenticado DIRECCION_Y_ADMINISTRACION = persona(15, "subdirector", Rol.DIRECTOR,
			Rol.ADMINISTRACION);

	public static final UsuarioAutenticado CAJA = persona(16, "caja", Rol.CAJA);

	private EscenarioCobranza() {
	}

	public static UsuarioAutenticado persona(long id, String usuario, Rol... roles) {
		return UsuariosDePrueba.autenticado(1L, id, usuario, "Nombre de " + usuario, false, EnumSet.of(roles[0], roles));
	}

	public static void como(UsuarioAutenticado usuario) {
		UsuariosDePrueba.iniciarSesion(usuario);
	}

	/** Plan por defecto del año: matrícula al 28/02 (o 29/02), pensiones del 31/03 al 31/12. */
	public static PlanRequest plan(int anio, String pension, String matricula, LocalDate cobroDesde) {
		return new PlanRequest(new BigDecimal(matricula), Calendario.ultimoDiaDelMes(anio, 2), new BigDecimal(pension),
				Calendario.vencimientosPorDefecto(anio, 3, 10), cobroDesde);
	}

	/** Administración propone y Dirección aprueba. Deja la sesión en Administración. */
	public static Long planAprobado(ServicioPlanesPension planes, Long anioId, int anio, Nivel nivel, String pension,
			String matricula, LocalDate cobroDesde) {
		como(ADMINISTRACION);
		Long id = planes.crearBorrador(anioId, nivel, plan(anio, pension, matricula, cobroDesde));
		como(DIRECCION);
		planes.aprobar(id);
		como(ADMINISTRACION);
		return id;
	}
}
