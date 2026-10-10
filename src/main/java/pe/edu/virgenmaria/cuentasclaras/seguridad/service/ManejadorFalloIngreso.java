package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;

import java.io.IOException;
import java.util.Map;

/**
 * Un ingreso fallido.
 * <ul>
 *   <li>Clave incorrecta, usuario inexistente, cuenta desactivada o BLOQUEADA: el mismo mensaje genérico, para no revelar
 *       qué usuarios existen ni cuáles están bloqueados. La clave temporal vencida tiene su aviso: solo se llega ahí con la
 *       clave correcta.</li>
 *   <li>Sprint 7, tanda 3 (H9): el intento se cuenta para la conexión ({@link LimiteIngresos}). Cuando una conexión empieza
 *       a esperar, queda en la bitácora para revisar (con la IP, sin el texto escrito como usuario).</li>
 * </ul>
 */
@Component
public class ManejadorFalloIngreso extends ExceptionMappingAuthenticationFailureHandler {

	private final LimiteIngresos limite;

	private final AuditoriaService auditoria;

	public ManejadorFalloIngreso(LimiteIngresos limite, AuditoriaService auditoria) {
		this.limite = limite;
		this.auditoria = auditoria;
		setDefaultFailureUrl("/login?error");
		setExceptionMappings(Map.of(CredentialsExpiredException.class.getName(), "/login?vencida"));
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest peticion, HttpServletResponse respuesta,
			AuthenticationException excepcion) throws IOException, ServletException {
		String ip = Actor.normalizarIp(peticion.getRemoteAddr());
		LimiteIngresos.Fallo fallo = limite.fallo(ip, ServicioDetallesUsuario.normalizar(peticion.getParameter("usuario")));
		fallo.empiezaAEsperar().ifPresent(motivo -> auditoria.registrar(auditoria.actorPara(null, null, Actor.ANONIMO,
				null), AccionAuditoria.INGRESOS_LIMITADOS, "conexion", null, null, null,
				(motivo == LimiteIngresos.Motivo.CONEXION ? "Demasiados intentos de ingreso fallidos desde la IP " + ip
						: "Demasiados intentos de ingreso fallidos con un mismo usuario desde la IP " + ip)
						+ ": esa conexión espera " + limite.minutos() + " minutos. La cuenta no se bloqueó por esto."));
		super.onAuthenticationFailure(peticion, respuesta, excepcion);
	}
}
