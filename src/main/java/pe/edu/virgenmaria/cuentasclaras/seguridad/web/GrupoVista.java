package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import java.util.List;

/**
 * Un grupo plegable del menú con las opciones que el rol puede ver.
 *
 * @param codigo     nombre del grupo (para pruebas y estilos)
 * @param etiqueta   texto del encabezado del grupo
 * @param abierto    se muestra desplegado al cargar la página
 * @param elementos  opciones del grupo, en orden
 * @param pendientes suma de los contadores urgentes del grupo (se ve aunque el grupo esté plegado)
 */
public record GrupoVista(String codigo, String etiqueta, boolean abierto, List<ElementoMenu> elementos,
		long pendientes) {
}
