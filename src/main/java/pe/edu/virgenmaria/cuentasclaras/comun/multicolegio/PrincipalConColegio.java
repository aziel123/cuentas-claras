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

	/**
	 * Roles para la auditoría ("CAJA,DOCENTE"). Por defecto {@code null}: se toman de las autoridades.
	 * Sirve cuando las autoridades no reflejan los roles (por ejemplo, con la clave pendiente).
	 */
	default String rolesParaAuditoria() {
		return null;
	}
}
