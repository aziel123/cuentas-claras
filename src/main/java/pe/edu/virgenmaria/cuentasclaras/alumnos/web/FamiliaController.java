package pe.edu.virgenmaria.cuentasclaras.alumnos.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoCorregido;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoDetalle;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatoInvalidoException;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioFamilias;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.MotivoRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/**
 * Familias y apoderados. Sin lógica: valida, delega en {@link ServicioFamilias} y muestra el resultado.
 */
@Controller
@RequestMapping("/alumnos")
public class FamiliaController {

	private static final String VISTA_FAMILIA = "alumnos/familia";

	private static final String VISTA_APODERADO = "alumnos/apoderado";

	private final ServicioFamilias familias;

	private final pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAccesoApoderados accesos;

	public FamiliaController(ServicioFamilias familias,
			pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAccesoApoderados accesos) {
		this.familias = familias;
		this.accesos = accesos;
	}

	@GetMapping("/familias/{id:\\d+}")
	public String familia(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado sesion, Model model) {
		model.addAttribute("nuevoApoderado", ApoderadoRequest.vacio());
		prepararFamilia(model, id, sesion);
		return VISTA_FAMILIA;
	}

	@PostMapping("/familias/{id:\\d+}/apoderados")
	public String agregarApoderado(@PathVariable Long id,
			@Valid @ModelAttribute("nuevoApoderado") ApoderadoRequest solicitud, BindingResult validacion,
			@AuthenticationPrincipal UsuarioAutenticado sesion, Model model, RedirectAttributes avisos) {
		if (!validacion.hasErrors()) {
			try {
				familias.agregarApoderado(id, solicitud);
				avisos.addFlashAttribute("exito", "Listo: agregaste a " + solicitud.nombres().strip() + " "
						+ solicitud.apellidoPaterno().strip() + " como apoderado.");
				return "redirect:/alumnos/familias/" + id;
			}
			catch (DatoInvalidoException e) {
				Formularios.errorEnCampo(validacion, model, e.campo(), e.getMessage());
			}
			catch (ReglaNegocioException e) {
				model.addAttribute("error", e.getMessage());
			}
		}
		prepararFamilia(model, id, sesion);
		model.addAttribute("formularioAbierto", true);
		return VISTA_FAMILIA;
	}

	@PostMapping("/familias/{id:\\d+}/nombre")
	public String renombrar(@PathVariable Long id, @RequestParam(defaultValue = "") String nombre,
			RedirectAttributes avisos) {
		try {
			familias.renombrar(id, nombre);
			avisos.addFlashAttribute("exito", "Listo: cambió el nombre de la familia.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/alumnos/familias/" + id;
	}

	@GetMapping("/apoderados/{id:\\d+}")
	public String apoderado(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado sesion, Model model) {
		ApoderadoDetalle detalle = familias.obtenerApoderado(id);
		model.addAttribute("solicitud", detalle.datos());
		prepararApoderado(model, detalle);
		// Sprint 4: la cuenta en línea del apoderado (la dan Promotoría o Administración).
		model.addAttribute("cuentaEnLinea", accesos.cuentaDe(id).orElse(null));
		model.addAttribute("puedeDarAcceso", sesion != null && (sesion.roles().contains(
				pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.PROMOTOR) || sesion.roles().contains(
						pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.ADMINISTRACION)));
		// S4-M2: Promotoría restablece el acceso (enlace nuevo de un solo uso).
		model.addAttribute("puedeRestablecerAcceso", sesion != null && sesion.roles().contains(
				pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.PROMOTOR));
		return VISTA_APODERADO;
	}

	@PostMapping("/apoderados/{id:\\d+}")
	public String actualizarApoderado(@PathVariable Long id, @Valid @ModelAttribute("solicitud") ApoderadoRequest solicitud,
			BindingResult validacion, Model model, RedirectAttributes avisos) {
		prepararApoderado(model, familias.obtenerApoderado(id));
		if (validacion.hasErrors()) {
			return VISTA_APODERADO;
		}
		try {
			ApoderadoCorregido resultado = familias.actualizarApoderado(id, solicitud);
			avisos.addFlashAttribute("exito", resultado.aviso());
			return "redirect:/alumnos/familias/" + resultado.familiaId();
		}
		catch (DatoInvalidoException e) {
			Formularios.errorEnCampo(validacion, model, e.campo(), e.getMessage());
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
		}
		return VISTA_APODERADO;
	}

	/** B2: pide registrar o cambiar el RUC del apoderado (lo aprueba otra persona en la bandeja). */
	@PostMapping("/apoderados/{id:\\d+}/facturacion")
	public String pedirDatosFacturacion(@PathVariable Long id, @RequestParam(required = false) String ruc,
			@RequestParam(required = false) String razonSocial, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		Long familia = familias.obtenerApoderado(id).familiaId();
		try {
			familias.solicitarDatosFacturacion(id, ruc, razonSocial, motivo);
			avisos.addFlashAttribute("exito", "Listo: pediste registrar el RUC. Promotoría o Dirección lo aprobarán; "
					+ "hasta entonces, en caja solo sale boleta.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/alumnos/familias/" + familia;
	}

	@PostMapping("/apoderados/{id:\\d+}/desactivar")
	public String desactivarApoderado(@PathVariable Long id, @RequestParam Long familiaId, @Valid MotivoRequest solicitud,
			BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/alumnos/familias/" + familiaId;
		}
		try {
			Long familia = familias.desactivarApoderado(id, solicitud.motivo());
			avisos.addFlashAttribute("exito", "Listo: el apoderado quedó desactivado.");
			return "redirect:/alumnos/familias/" + familia;
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/alumnos/familias/" + familiaId;
		}
	}

	private void prepararFamilia(Model model, Long id, UsuarioAutenticado sesion) {
		var familia = familias.obtener(id);
		model.addAttribute("familia", familia);
		// S4-M2: Promotoría restablece desde aquí el acceso en línea de un apoderado (enlace nuevo de un solo uso).
		boolean restablece = sesion != null && sesion.roles().contains(
				pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.PROMOTOR);
		model.addAttribute("puedeRestablecerAcceso", restablece);
		model.addAttribute("conCuentaEnLinea", restablece ? accesos.conCuentaActiva(familia.apoderados().stream()
				.map(pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoVista::id).toList()) : java.util.Set.of());
		model.addAttribute("puedeEditar", Formularios.puedeEditar(sesion));
		model.addAttribute("tiposDocumento", TipoDocumento.values());
		model.addAttribute("parentescos", Parentesco.values());
	}

	private static void prepararApoderado(Model model, ApoderadoDetalle detalle) {
		model.addAttribute("apoderado", detalle);
		model.addAttribute("tiposDocumento", TipoDocumento.values());
		model.addAttribute("parentescos", Parentesco.values());
	}
}
