package pe.edu.virgenmaria.cuentasclaras.comun.multicolegio;

/**
 * Principal de Spring Security que sabe a qué colegio pertenece. {@link ContextoColegio}
 * lo lee del {@code SecurityContextHolder} para resolver el colegio de cada petición.
 */
public interface PrincipalConColegio {

	/** Colegio del usuario autenticado. */
	Long colegioId();

	/** Id del usuario autenticado, para la auditoría. */
	Long usuarioId();
}
