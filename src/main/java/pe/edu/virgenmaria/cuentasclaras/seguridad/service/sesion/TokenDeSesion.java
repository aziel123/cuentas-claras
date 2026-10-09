package pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion;

import java.util.Optional;

/**
 * De dónde sale el secreto de la sesión de quien firma (sprint 7, tanda 2; sección 3.4). En prod y piloto,
 * {@link TokenDeSesionHttp} (la sesión HTTP de la petición). Las pruebas tienen su propia implementación en
 * {@code src/test} (abre una sesión por la ruta de identidad para el principal de la prueba); fuera del perfil
 * {@code test}, {@code VerificadorConfiguracion} exige {@link TokenDeSesionHttp}.
 */
public interface TokenDeSesion {

	/** La sesión de la base de quien está en sesión, si tiene. */
	Optional<SesionAbierta> actual();
}
