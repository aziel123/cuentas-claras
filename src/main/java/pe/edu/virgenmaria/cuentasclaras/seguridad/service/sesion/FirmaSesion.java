package pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion;

import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.PrincipalSistema;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.FirmaOperacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.FirmaOperacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.Arrays;

/**
 * Firma una aprobación con la sesión de quien resuelve (sprint 7, tanda 2; sección 3.4, paso 3). Se llama ANTES de
 * cambiar la entidad, en la misma transacción: inserta (con {@code saveAndFlush}) una {@link FirmaOperacion} con la clave
 * canónica ({@link ClaveFirma}) y el secreto de la sesión. En MySQL, trg_firma_operacion_nace valida el secreto contra la
 * sesión abierta de esa persona y el trigger de la tabla resuelta exige esa firma a nombre de quien figura como aprobador.
 * <p>
 * Un actor de sistema no firma: su fila la acepta el trigger solo con la conexión de {@code cc_sistema}. Sin una sesión
 * de la base (por ejemplo, venció a las 10 horas o se cerró al ingresar en otro equipo), nada se aprueba: hay que volver
 * a ingresar.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class FirmaSesion {

	static final String SIN_SESION = "Tu sesión ya no sirve para aprobar (venció o ingresaste en otro equipo). Cierra "
			+ "sesión y vuelve a ingresar: no se aprobó nada.";

	private final TokenDeSesion tokens;

	private final FirmaOperacionRepository firmas;

	public FirmaSesion(TokenDeSesion tokens, FirmaOperacionRepository firmas, Environment entorno) {
		// Sin atajos fuera de las pruebas: el secreto sale SIEMPRE de la sesión HTTP de quien firma.
		if (!Arrays.asList(entorno.getActiveProfiles()).contains("test") && !(tokens instanceof TokenDeSesionHttp)) {
			throw new IllegalStateException("Fuera de las pruebas el secreto de la sesión sale de la sesión HTTP "
					+ "(TokenDeSesionHttp), no de " + tokens.getClass().getName());
		}
		this.tokens = tokens;
		this.firmas = firmas;
	}

	public void firmar(String clave) {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		Object principal = autenticacion == null ? null : autenticacion.getPrincipal();
		if (principal instanceof PrincipalSistema) {
			return;
		}
		if (!(principal instanceof UsuarioAutenticado usuario)) {
			throw new ReglaNegocioException(SIN_SESION);
		}
		SesionAbierta sesion = tokens.actual()
				.filter(s -> s.usuarioId().equals(usuario.usuarioId()) && s.colegioId().equals(usuario.colegioId()))
				.orElseThrow(() -> new ReglaNegocioException(SIN_SESION));
		firmas.saveAndFlush(FirmaOperacion.de(sesion.sesionId(), sesion.usuarioId(), clave, sesion.token()));
	}
}
