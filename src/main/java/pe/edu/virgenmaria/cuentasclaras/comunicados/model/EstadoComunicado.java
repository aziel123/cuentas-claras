package pe.edu.virgenmaria.cuentasclaras.comunicados.model;

/**
 * Sprint 9, tanda 3 (sección 5): nace ENVIADO o POR_APROBAR (PDF de un docente o PDF que requiere revisión, B3); este
 * pasa a ENVIADO o RECHAZADO con su solicitud; un ENVIADO pasa a RETIRADO (Dirección o Promotoría, con firma y motivo).
 */
public enum EstadoComunicado {
	POR_APROBAR, ENVIADO, RECHAZADO, RETIRADO
}
