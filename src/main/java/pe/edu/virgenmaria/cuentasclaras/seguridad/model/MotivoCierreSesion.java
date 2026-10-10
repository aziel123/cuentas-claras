package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

/** Por qué se cerró una sesión de la base (sprint 7, tanda 2; sección 3.4). Se guarda por nombre: no renombrar. */
public enum MotivoCierreSesion {

	/** La persona salió con «Cerrar sesión». */
	SALIO,

	/** La sesión HTTP expiró (30 minutos sin actividad o el tiempo máximo) o se invalidó. */
	VENCIO,

	/** La persona ingresó en otro equipo: una sola sesión por persona. */
	OTRA_SESION,

	/** Le cambiaron (o cambió) la clave, los roles, el contacto, o la desactivaron. */
	CUENTA_CAMBIADA,

	/** La aplicación arrancó: las sesiones HTTP anteriores ya no existen. */
	REINICIO
}
