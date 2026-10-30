package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;

import java.util.List;

/**
 * Sprint 8, tanda 1 (sección 3.5): la lista de verificación de un colegio y su estado. En la tanda 1 lleva los puntos
 * que ya se pueden calcular (datos, equipo con segregación, año y secciones, alumnos, correo de control y aviso de
 * privacidad); la tanda 2 agrega las pensiones, el saldo inicial, los conectores, WhatsApp y el respaldo.
 */
public record ListaVerificacion(Long colegioId, String nombre, EstadoColegio estado, List<PuntoLista> puntos, Integer anio) {

	/** Sprint 11, tanda 1b: {@code anio} es el año escolar vigente (para «Crear las secciones del año 2026»), o nulo. */
	public ListaVerificacion(Long colegioId, String nombre, EstadoColegio estado, List<PuntoLista> puntos) {
		this(colegioId, nombre, estado, puntos, null);
	}

	public boolean enPreparacion() {
		return estado == EstadoColegio.PREPARACION;
	}

	/** Todos los puntos obligatorios están hechos. */
	public boolean completa() {
		return puntos.stream().filter(PuntoLista::obligatorio).allMatch(PuntoLista::hecho);
	}

	/** El punto con esa clave. */
	public java.util.Optional<PuntoLista> punto(String clave) {
		return puntos.stream().filter(p -> p.clave().equals(clave)).findFirst();
	}

	public long pendientes() {
		return puntos.stream().filter(p -> p.obligatorio() && !p.hecho()).count();
	}

	/** Si el punto con esa clave ya está hecho (por ejemplo, para no ofrecer de nuevo una firma ya dada). */
	public boolean hecho(String clave) {
		return puntos.stream().anyMatch(p -> p.clave().equals(clave) && p.hecho());
	}

	/** La misma lista sin enlaces (para el operador, que no tiene rutas del colegio). */
	public ListaVerificacion paraOperador() {
		return new ListaVerificacion(colegioId, nombre, estado, puntos.stream().map(PuntoLista::paraOperador).toList(), anio);
	}
}
