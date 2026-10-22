package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

/**
 * Sprint 9, tanda 3 (sección 3.11, decisión 165, B14): el catálogo CERRADO de lo que puede ir en una columna de la nómina
 * para el SIAGIE. Ningún contacto (celular, correo, dirección), ningún dato de los apoderados y nada de cobranza
 * ({@code CampoNominaSinContactosNiCobranzaTest}): un perfil nuevo no puede sacar más datos que estos.
 */
public enum CampoNomina {
	ORDEN,
	TIPO_DOCUMENTO,
	NUMERO_DOCUMENTO,
	CODIGO_ESTUDIANTE,
	APELLIDO_PATERNO,
	APELLIDO_MATERNO,
	NOMBRES,
	SEXO,
	FECHA_NACIMIENTO,
	NIVEL,
	GRADO,
	SECCION,
	CODIGO_MODULAR,
	ANEXO,
	SITUACION,
	FECHA_MATRICULA,
	VACIO
}
