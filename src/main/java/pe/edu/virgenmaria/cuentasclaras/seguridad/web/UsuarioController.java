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
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpSession;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.FacultadTitular;
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

	private final FacultadTitular facultad;

	/** Sprint 11, tanda 1: lo que el titular pidió hacer con su facultad, mientras escribe el código (sesión HTTP). */
	static final String PENDIENTE = "facultad.pendiente";

	static final String PEDIDO = "facultad.pedido";

	/** Lo pendiente de un uso de la facultad: crear una cuenta, o dar o quitar un rol directivo a una que existe. */
	record Pendiente(boolean crear, Long usuarioId, Rol rol, boolean dar, String nombreCompleto, String nombreUsuario,
			String correo, String telefonoWhatsapp, java.util.EnumSet<Rol> roles, String resumen)
			implements java.io.Serializable {

		CrearUsuarioRequest solicitud() {
			return new CrearUsuarioRequest(nombreCompleto, nombreUsuario, correo, telefonoWhatsapp, roles);
		}
	}

	public UsuarioController(ServicioUsuarios servicio, FacultadTitular facultad) {
		this.servicio = servicio;
		this.facultad = facultad;
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
		model.addAttribute("facultad", estadoFacultad());
		return "usuarios/formulario";
	}

	/**
	 * Muestra a dónde se envió el enlace (enmascarado), en la misma respuesta: nunca una clave ni el enlace. Sprint 11,
	 * tanda 1: si el titular pide una cuenta con Promotoría o Dirección, va por su facultad (con su código).
	 */
	@PostMapping
	public String crear(@Valid @ModelAttribute("solicitud") CrearUsuarioRequest solicitud, BindingResult validacion,
			Model model, HttpSession sesion) {
		model.addAttribute("rolesAsignables", servicio.rolesAsignables());
		model.addAttribute("facultad", estadoFacultad());
		if (validacion.hasErrors()) {
			return "usuarios/formulario";
		}
		boolean directiva = solicitud.roles() != null
				&& (solicitud.roles().contains(Rol.PROMOTOR) || solicitud.roles().contains(Rol.DIRECTOR));
		if (directiva && facultad.esTitular()) {
			Rol rol = solicitud.roles().contains(Rol.DIRECTOR) ? Rol.DIRECTOR : Rol.PROMOTOR;
			java.util.EnumSet<Rol> roles = java.util.EnumSet.copyOf(solicitud.roles());
			return pedirCodigo(new Pendiente(true, null, rol, true, solicitud.nombreCompleto(), solicitud.nombreUsuario(),
					solicitud.correo(), solicitud.telefonoWhatsapp(), roles, "Crear la cuenta " + solicitud.nombreUsuario()
							+ " (" + solicitud.nombreCompleto() + ") con " + roles.stream().map(Rol::etiqueta)
							.collect(java.util.stream.Collectors.joining(", "))), sesion, model,
					"usuarios/formulario");
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
		model.addAttribute("facultad", estadoFacultad());
		return "usuarios/detalle";
	}

	/**
	 * Sprint 11, tanda 1c (ajuste de 3.12): dar o quitar Promotoría o Dirección a una cuenta que ya existe ya no es la
	 * facultad del titular: es «Cambiar roles», directo con el código de la Promotoría.
	 */
	@PostMapping("/{id}/facultad")
	public String usarFacultad(@PathVariable Long id, RedirectAttributes avisos) {
		avisos.addFlashAttribute("error", "Para dar o quitar Promotoría o Dirección usa «Cambiar roles»: como Promotoría lo "
				+ "aplicas directo con tu código por WhatsApp (dar Promotoría en producción o quitarla espera 24 horas).");
		return redirigirADetalle(id);
	}

	/** Sprint 11, tanda 1: otro código para lo mismo (el anterior venció o no llegó). */
	@PostMapping("/facultad/nuevo-codigo")
	public String nuevoCodigo(HttpSession sesion, Model model, RedirectAttributes avisos) {
		if (!(sesion.getAttribute(PENDIENTE) instanceof Pendiente pendiente)) {
			avisos.addFlashAttribute("error", "No hay nada pendiente: vuelve a empezar desde «Usuarios».");
			return "redirect:/usuarios";
		}
		return pedirCodigo(pendiente, sesion, model, "usuarios/codigo-facultad");
	}

	/** Sprint 11, tanda 1: el titular escribe el código y se aplica lo pendiente, con su firma. */
	@PostMapping("/facultad/confirmar")
	public String confirmarFacultad(@RequestParam(required = false) String codigo, HttpSession sesion, Model model,
			RedirectAttributes avisos) {
		if (!(sesion.getAttribute(PENDIENTE) instanceof Pendiente pendiente)
				|| !(sesion.getAttribute(PEDIDO) instanceof CodigoPromotoria.Pedido pedido)) {
			avisos.addFlashAttribute("error", "No hay nada pendiente: vuelve a empezar desde «Usuarios».");
			return "redirect:/usuarios";
		}
		try {
			model.addAttribute("creado", facultad.crearCuentaDirectiva(pendiente.solicitud(), codigo, pedido));
			model.addAttribute("restablecida", false);
			model.addAttribute("conFacultad", true);
			limpiar(sesion);
			return "usuarios/creado";
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
			prepararCodigo(model, pendiente, pedido);
			return "usuarios/codigo-facultad";
		}
	}

	private String pedirCodigo(Pendiente pendiente, HttpSession sesion, Model model, String vistaSiFalla) {
		CodigoPromotoria.Pedido pedido;
		try {
			pedido = facultad.pedirCodigo(pendiente.rol(), pendiente.nombreUsuario());
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
			return vistaSiFalla;
		}
		sesion.setAttribute(PENDIENTE, pendiente);
		sesion.setAttribute(PEDIDO, pedido);
		prepararCodigo(model, pendiente, pedido);
		return "usuarios/codigo-facultad";
	}

	private void prepararCodigo(Model model, Pendiente pendiente, CodigoPromotoria.Pedido pedido) {
		model.addAttribute("pendiente", pendiente);
		model.addAttribute("pedido", pedido);
		model.addAttribute("codigoDemostracion", facultad.codigoDeDemostracion(pedido).orElse(null));
	}

	private static void limpiar(HttpSession sesion) {
		sesion.removeAttribute(PENDIENTE);
		sesion.removeAttribute(PEDIDO);
	}

	private static String primeraMinuscula(String texto) {
		return texto.isEmpty() ? texto : Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
	}

	/** La facultad, solo para la Promotoría (Dirección no la ve). */
	private FacultadTitular.Estado estadoFacultad() {
		try {
			return facultad.estado();
		}
		catch (org.springframework.security.access.AccessDeniedException e) {
			return new FacultadTitular.Estado(false, false, null, false);
		}
	}

	@PostMapping("/{id}/roles")
	public String cambiarRoles(@PathVariable Long id, @Valid CambiarRolesRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		// Sprint 7, tanda 2: dar o quitar Promotoría o Dirección se pide; lo aprueba otra persona en la bandeja.
		String[] exito = { "Listo: se cambiaron los roles. Sus sesiones abiertas se cerraron." };
		return ejecutar(id, validacion, avisos, () -> {
			if (servicio.cambiarRoles(id, solicitud)) {
				exito[0] = "Listo: se pidió el cambio de roles. Si eres Promotoría, confírmalo con tu código en Aprobaciones "
						+ "(«Aprobar y aplicar»; dar Promotoría en producción o quitarla espera 24 horas); si no, lo aprueba "
						+ "una persona de Promotoría. Hasta entonces los roles no cambian.";
			}
		}, exito);
	}

	@PostMapping("/{id}/desactivar")
	public String desactivar(@PathVariable Long id, @Valid MotivoRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		// Correcciones del sprint 7: una cuenta de Promotoría o Dirección se pide; la aprueba otra persona.
		String[] exito = { "Listo: el usuario quedó desactivado y ya no puede ingresar." };
		return ejecutar(id, validacion, avisos, () -> {
			if (servicio.desactivar(id, solicitud.motivo())) {
				exito[0] = PEDIDO_ESTADO;
			}
		}, exito);
	}

	@PostMapping("/{id}/reactivar")
	public String reactivar(@PathVariable Long id, @Valid MotivoRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		String[] exito = { "Listo: el usuario puede volver a ingresar." };
		return ejecutar(id, validacion, avisos, () -> {
			if (servicio.reactivar(id, solicitud.motivo())) {
				exito[0] = PEDIDO_ESTADO;
			}
		}, exito);
	}

	private static final String PEDIDO_ESTADO = "Listo: se pidió el cambio. Es una cuenta de Promotoría o Dirección: lo "
			+ "aprueba otra persona de Promotoría o Dirección en la bandeja de aprobaciones; hasta entonces no cambia.";

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
		return ejecutar(id, validacion, avisos, accion, new String[] { exito });
	}

	private static String ejecutar(Long id, BindingResult validacion, RedirectAttributes avisos, Runnable accion,
			String[] exito) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", primerError(validacion));
			return redirigirADetalle(id);
		}
		try {
			accion.run();
			avisos.addFlashAttribute("exito", exito[0]);
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
