package pe.edu.virgenmaria.cuentasclaras.academico.model;

/**
 * Sprint 9, tanda 2 (decisión 144): quién toma la asistencia. En este sprint SOLO {@link #DOCENTE}: los triggers rechazan
 * {@link #MARCADOR} y una regla ArchUnit prohíbe usarlo fuera de este enum (A15). Habilitar un marcador dactilar será otra
 * migración, otra versión de los triggers y su propio diseño.
 */
public enum FuenteAsistencia {
	DOCENTE,
	/** Puerta abierta, sin implementar: nadie la usa. */
	MARCADOR
}
