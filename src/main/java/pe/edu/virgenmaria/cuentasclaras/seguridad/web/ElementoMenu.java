package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;

/**
 * Opción del menú o tarjeta del inicio, armada en el servidor a partir de {@link ModuloApp}.
 *
 * @param codigo      nombre del módulo (para pruebas y estilos)
 * @param etiqueta    texto visible
 * @param descripcion qué podrá hacer el usuario ahí
 * @param ruta        enlace; solo se usa si está disponible
 * @param disponible  {@code false}: se muestra "Próximamente" y sin enlace
 * @param etapa       cuándo llega ("Sprint 2", "Más adelante")
 */
public record ElementoMenu(String codigo, String etiqueta, String descripcion, String ruta, boolean disponible,
		String etapa) {

	static ElementoMenu de(ModuloApp modulo) {
		return new ElementoMenu(modulo.name(), modulo.etiqueta(), modulo.descripcion(), modulo.ruta(),
				modulo.disponible(), modulo.etapa());
	}
}
