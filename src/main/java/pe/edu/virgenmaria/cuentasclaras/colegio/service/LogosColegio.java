package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.LogoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.LogoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.Optional;

/**
 * Sprint 8, tanda 1 (sección 3.7): el logo del colegio en contexto. {@link #guardar} valida el archivo (PNG o JPEG por sus
 * bytes mágicos, hasta 1 MB y 4000 × 4000 px leídos de la cabecera), lo decodifica, lo reescala a 400 px como máximo y
 * lo vuelve a codificar como PNG (se pierden los metadatos y cualquier contenido políglota) antes de guardarlo con su
 * SHA-256. SVG no se acepta. Lo llama el alta del colegio (dentro de su transacción) y la pantalla de marca.
 */
@Service
public class LogosColegio {

	public static final long MAX_SUBIDA = 1_048_576L;

	private final LogoColegioRepository logos;

	public LogosColegio(LogoColegioRepository logos) {
		this.logos = logos;
	}

	/** El logo vigente del colegio en contexto. */
	@Transactional(readOnly = true)
	public Optional<LogoColegio> vigente() {
		return logos.findByVigenteTrue();
	}

	/**
	 * Valida, reescribe y guarda el logo como el vigente del colegio en contexto (el anterior queda reemplazado). Debe
	 * llamarse con una transacción abierta (la del alta o la de la pantalla de marca).
	 *
	 * @throws ReglaNegocioException si el archivo no es un PNG o JPEG válido de hasta 1 MB
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public LogoColegio guardar(byte[] contenido, String nombreOriginal) {
		throw new ReglaNegocioException("El logo todavía no se puede guardar.");
	}
}
