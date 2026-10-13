package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.MarcaColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.marca.Marca;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agrega a toda vista el usuario en sesión y su menú, armado en el servidor desde {@link ModuloApp}
 * (sin {@code thymeleaf-extras-springsecurity}).
 */
@ControllerAdvice
public class ModeloSesionAdvice {

	private final ColegioService colegios;

	private final Marca marca;

	public ModeloSesionAdvice(ColegioService colegios, Marca marca) {
		this.colegios = colegios;
		this.marca = marca;
	}

	@ModelAttribute("usuarioActual")
	public SesionVista usuarioActual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		if (usuario == null) {
			return null;
		}
		String rol = usuario.roles().stream().sorted().map(Rol::etiqueta).collect(Collectors.joining(" · "));
		return new SesionVista(usuario.nombreCompleto(), usuario.getUsername(), rol,
				colegios.nombreDe(usuario.colegioId()), usuario.debeCambiarClave());
	}

	@ModelAttribute("menu")
	public List<ElementoMenu> menu(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		if (usuario == null) {
			return List.of();
		}
		return ModuloApp.para(usuario.getAuthorities()).stream().map(ElementoMenu::de).toList();
	}

	@ModelAttribute("rutaActual")
	public String rutaActual(HttpServletRequest request) {
		return request.getRequestURI();
	}

	/**
	 * Sprint 8, tanda 1 (decisión 125): las páginas públicas (ingreso, errores) llevan la marca del producto, no el nombre
	 * de un colegio: la lista de clientes no se publica y el ingreso es uno para todos.
	 */
	@ModelAttribute("nombreInstitucional")
	public String nombreInstitucional() {
		return marca.nombre();
	}

	/**
	 * Sprint 8, tanda 1 (decisión 123): la marca del colegio de la sesión (nombre, razón social, RUC, dirección y si tiene
	 * logo), para la cabecera y las pantallas que la muestran. {@code null} sin sesión: las páginas públicas no nombran a
	 * ningún colegio.
	 */
	@ModelAttribute("marcaColegio")
	public MarcaColegio marcaColegio(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		if (usuario == null) {
			return null;
		}
		return colegios.marcaDe(usuario.colegioId()).orElse(null);
	}
}
