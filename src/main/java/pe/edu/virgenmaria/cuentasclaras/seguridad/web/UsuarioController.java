package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.MotivoRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

/**
 * Usuarios y roles. Sin lógica: valida el formulario, delega en {@link ServicioUsuarios} y muestra el
 * resultado. Las reglas del negocio llegan como {@link ReglaNegocioException} y se muestran como aviso.
 */
@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

	private final ServicioUsuarios servicio;

	public UsuarioController(ServicioUsuarios servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public String lista(Model model) {
		model.addAttribute("usuarios", servicio.listar());
		return "usuarios/lista";
	}

	@GetMapping("/nuevo")
	public String formulario(Model model) {
		model.addAttribute("solicitud", CrearUsuarioRequest.vacio());
		model.addAttribute("rolesAsignables", servicio.rolesAsignables());
		return "usuarios/formulario";
	}

	/** Muestra la clave temporal en la misma respuesta (sin redirigir): no queda en la sesión ni en la URL. */
	@PostMapping
	public String crear(@Valid @ModelAttribute("solicitud") CrearUsuarioRequest solicitud, BindingResult validacion,
			Model model) {
		model.addAttribute("rolesAsignables", servicio.rolesAsignables());
		if (validacion.hasErrors()) {
			return "usuarios/formulario";
		}
		try {
			model.addAttribute("creado", servicio.crear(solicitud));
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
			return "usuarios/formulario";
		}
		model.addAttribute("restablecida", false);
		return "usuarios/creado";
	}

	@GetMapping("/{id}")
	public String detalle(@PathVariable Long id, Model model) {
		model.addAttribute("usuario", servicio.obtener(id));
		model.addAttribute("rolesAsignables", servicio.rolesAsignables());
		return "usuarios/detalle";
	}

	@PostMapping("/{id}/roles")
	public String cambiarRoles(@PathVariable Long id, @Valid CambiarRolesRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		return ejecutar(id, validacion, avisos, () -> servicio.cambiarRoles(id, solicitud),
				"Listo: se cambiaron los roles. Sus sesiones abiertas se cerraron.");
	}

	@PostMapping("/{id}/desactivar")
	public String desactivar(@PathVariable Long id, @Valid MotivoRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		return ejecutar(id, validacion, avisos, () -> servicio.desactivar(id, solicitud.motivo()),
				"Listo: el usuario quedó desactivado y ya no puede ingresar.");
	}

	@PostMapping("/{id}/reactivar")
	public String reactivar(@PathVariable Long id, @Valid MotivoRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		return ejecutar(id, validacion, avisos, () -> servicio.reactivar(id, solicitud.motivo()),
				"Listo: el usuario puede volver a ingresar.");
	}

	@PostMapping("/{id}/desbloquear")
	public String desbloquear(@PathVariable Long id, @Valid MotivoRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		return ejecutar(id, validacion, avisos, () -> servicio.desbloquear(id, solicitud.motivo()),
				"Listo: la cuenta quedó desbloqueada.");
	}

	@PostMapping("/{id}/restablecer-clave")
	public String restablecerClave(@PathVariable Long id, @Valid MotivoRequest solicitud, BindingResult validacion,
			Model model, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", primerError(validacion));
			return redirigirADetalle(id);
		}
		try {
			model.addAttribute("creado", servicio.restablecerClave(id, solicitud.motivo()));
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return redirigirADetalle(id);
		}
		model.addAttribute("restablecida", true);
		return "usuarios/creado";
	}

	private static String ejecutar(Long id, BindingResult validacion, RedirectAttributes avisos, Runnable accion,
			String exito) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", primerError(validacion));
			return redirigirADetalle(id);
		}
		try {
			accion.run();
			avisos.addFlashAttribute("exito", exito);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return redirigirADetalle(id);
	}

	private static String primerError(BindingResult validacion) {
		var error = validacion.getAllErrors().getFirst();
		// Un valor que no se pudo convertir (por ejemplo, un rol inventado) no muestra el mensaje técnico.
		boolean conversion = error.getCodes() != null && java.util.Arrays.stream(error.getCodes())
				.anyMatch(c -> c.startsWith("typeMismatch"));
		return conversion ? "Revisa los datos: elige roles de la lista." : error.getDefaultMessage();
	}

	private static String redirigirADetalle(Long id) {
		return "redirect:/usuarios/" + id;
	}
}
