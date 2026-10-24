package pe.edu.virgenmaria.cuentasclaras.academico.api;

/**
 * Sprint 9, tanda 2 (sección 3.6, H2, decisión 152): si un apoderado recibe los avisos de asistencia por un canal, según
 * su último consentimiento. WhatsApp exige un «sí» explícito; el correo sale salvo oposición. Lo leen la mensajería (el
 * aviso y su respaldo por correo) y el portal. En MySQL lo exige también trg_mensaje_nace.
 */
public interface Consentimientos {

	/** ¿Se le escribe por WhatsApp ({@code true}) o por correo ({@code false}) los avisos de asistencia? */
	boolean recibeAsistencia(long apoderadoId, boolean porWhatsapp);

	/** Sprint 9, tanda 3: ¿se le escriben los avisos de comunicados por WhatsApp ({@code true}) o por correo? */
	boolean recibeComunicados(long apoderadoId, boolean porWhatsapp);

	/**
	 * Sprint 9, tanda 3 (sección 3.10): el apoderado es el contacto principal de su familia: recibe el resumen de
	 * comunicados por WhatsApp (con su «sí»); los demás, por correo.
	 */
	boolean esContactoPrincipal(long apoderadoId);

	/**
	 * El contacto principal de la familia: el apoderado cuyo último consentimiento de comunicados por WhatsApp es un «sí»
	 * como principal; si hay varios, el más reciente (cada apoderado lo elige con su cuenta; uno solo recibe el resumen).
	 */
	java.util.Optional<Long> contactoPrincipal(long familiaId);

	/**
	 * Sprint 10, tanda 3 (decisión 180): ¿se le avisa que la libreta está en el portal por WhatsApp ({@code true}, con su
	 * «sí» a la finalidad LIBRETAS) o por correo (salvo oposición)?
	 */
	boolean recibeLibretas(long apoderadoId, boolean porWhatsapp);
}
