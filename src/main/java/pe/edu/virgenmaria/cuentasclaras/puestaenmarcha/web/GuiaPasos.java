package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.GuiaVista.PasoGuia;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.ListaVerificacion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.GuiaPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.Set;

/**
 * Sprint 11, tanda 1b (sección 3.14, punto 2): los estados vacíos que guían. Las plantillas lo llaman solo cuando la lista
 * está vacía ({@code ${@guiaPasos.vacio('secciones')}}), así que la lista de la puesta en marcha se calcula solo entonces y
 * ningún controlador cambia. Devuelve el paso con su botón (si el rol puede entrar), el paso del que depende (si está
 * pendiente) y si el colegio está en preparación (en producción no se menciona la puesta en marcha).
 */
@Component("guiaPasos")
public class GuiaPasos {

	private final EstadoPuestaEnMarcha estado;

	public GuiaPasos(EstadoPuestaEnMarcha estado) {
		this.estado = estado;
	}

	/** El paso del estado vacío, para quien está en sesión. */
	public PasoVacio vacio(String clave) {
		Set<Rol> roles = roles();
		ListaVerificacion lista = estado.actual();
		PuntoLista punto = lista.punto(clave).orElseThrow();
		PasoGuia paso = GuiaPuestaEnMarcha.vista(punto, lista, roles, false);
		PasoGuia primero = punto.bloqueado() ? lista.punto(punto.bloqueadoPor().clave())
				.map(p -> GuiaPuestaEnMarcha.vista(p, lista, roles, false)).orElse(null) : null;
		return new PasoVacio(paso, primero, lista.enPreparacion(), lista.anio());
	}

	/** Cuántos pasos obligatorios faltan (para el aviso de un bloqueo). */
	public long faltan() {
		return estado.actual().pendientes();
	}

	/** Si quien está en sesión puede abrir la Puesta en marcha (para el enlace de un aviso). */
	public boolean verLista() {
		return ModuloApp.puedeEntrar(roles(), "/puesta-en-marcha");
	}

	/**
	 * @param paso          el paso con su acción y su enlace (nulo si el rol no puede entrar)
	 * @param primero       el paso pendiente del que depende, o nulo
	 * @param enPreparacion si el colegio está en preparación
	 * @param anio          el año escolar vigente, o nulo
	 */
	public record PasoVacio(PasoGuia paso, PasoGuia primero, boolean enPreparacion, Integer anio) {
	}

	private static Set<Rol> roles() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? Set.of() : ModuloApp.rolesDe(autenticacion.getAuthorities());
	}
}
