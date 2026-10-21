package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;

/**
 * Opción del menú, armada en el servidor a partir de {@link ModuloApp}.
 *
 * @param codigo        nombre del módulo (para pruebas y estilos)
 * @param etiqueta      texto visible
 * @param descripcion   qué podrá hacer el usuario ahí
 * @param ruta          enlace; solo se usa si está disponible
 * @param disponible    {@code false}: se muestra "Próximamente" y sin enlace
 * @param etapa         cuándo llega ("Sprint 2", "Más adelante")
 * @param contador      número junto a la opción (0: sin número)
 * @param contadorTexto el número para lectores de pantalla («3 por aprobar»)
 * @param urgente       el número pide una acción (rojo) o es un aviso (ámbar)
 */
public record ElementoMenu(String codigo, String etiqueta, String descripcion, String ruta, boolean disponible,
		String etapa, long contador, String contadorTexto, boolean urgente) {

	static ElementoMenu de(ModuloApp modulo) {
		return new ElementoMenu(modulo.name(), modulo.etiqueta(), modulo.descripcion(), modulo.ruta(),
				modulo.disponible(), modulo.etapa(), 0, null, false);
	}

	ElementoMenu conContador(long cantidad, String texto, boolean esUrgente) {
		return new ElementoMenu(codigo, etiqueta, descripcion, ruta, disponible, etapa, cantidad, texto, esUrgente);
	}
}
