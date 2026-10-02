package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
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

	public ModeloSesionAdvice(ColegioService colegios) {
		this.colegios = colegios;
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

	/** Nombre para las páginas públicas. Si la base no responde, la página de error igual se muestra. */
	@ModelAttribute("nombreInstitucional")
	public String nombreInstitucional() {
		try {
			return colegios.nombreInstitucional();
		}
		catch (DataAccessException e) {
			return ColegioService.NOMBRE_PLATAFORMA;
		}
	}
}
