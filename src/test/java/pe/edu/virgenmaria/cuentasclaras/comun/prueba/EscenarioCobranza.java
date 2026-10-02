package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PlanRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.ResultadoGeneracion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial;
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

	/** Quien envía los planes a aprobación en las pruebas que no tratan del envío. */
	public static final UsuarioAutenticado ADMINISTRACION_ENVIO = persona(17, "administracion.envio", Rol.ADMINISTRACION);

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

	/** Administración propone y envía; Dirección aprueba. Deja la sesión en Administración. */
	public static Long planAprobado(ServicioPlanesPension planes, Long anioId, int anio, Nivel nivel, String pension,
			String matricula, LocalDate cobroDesde) {
		como(ADMINISTRACION);
		Long id = planes.crearBorrador(anioId, nivel, plan(anio, pension, matricula, cobroDesde));
		como(DIRECCION);
		aprobar(planes, id);
		como(ADMINISTRACION);
		return id;
	}

	/**
	 * Aprueba como el usuario en sesión la versión que este ve. Si el plan aún está en preparación, antes lo envía
	 * {@link #ADMINISTRACION_ENVIO} (y la sesión vuelve a quien aprueba).
	 */
	public static ResultadoGeneracion aprobar(ServicioPlanesPension planes, Long id) {
		Authentication aprobador = SecurityContextHolder.getContext().getAuthentication();
		como(ADMINISTRACION_ENVIO);
		if ("BORRADOR".equals(planes.obtener(id).estado())) {
			planes.enviar(id);
		}
		SecurityContextHolder.getContext().setAuthentication(aprobador);
		return planes.aprobar(id, planes.obtener(id).version());
	}

	/** Confirma como el usuario en sesión, con la versión que ve y el total del informe que escribe a ciegas. */
	public static int confirmar(ServicioSaldoInicial saldo, Long lote, String totalInforme) {
		return saldo.confirmar(lote, saldo.obtener(lote).version(), new BigDecimal(totalInforme));
	}
}
