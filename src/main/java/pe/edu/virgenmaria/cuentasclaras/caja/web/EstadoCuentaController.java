package pe.edu.virgenmaria.cuentasclaras.caja.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CorreccionRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CorreccionVista;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioEstadoCuenta;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;

/**
 * Estado de cuenta del alumno (pestaña de la ficha) y sus comprobantes; Administración pide desde aquí anulaciones y
 * correcciones (las aprueba otra persona). Sin lógica: delega en los servicios, que exigen el rol.
 */
@Controller
public class EstadoCuentaController {

	private final ServicioEstadoCuenta estados;

	private final ServicioAnulacionPagos anulaciones;

	public EstadoCuentaController(ServicioEstadoCuenta estados, ServicioAnulacionPagos anulaciones) {
		this.estados = estados;
		this.anulaciones = anulaciones;
	}

	@GetMapping("/alumnos/{id:\\d+}/estado-cuenta")
	public String estadoCuenta(@PathVariable Long id, Model model) {
		model.addAttribute("estado", estados.deAlumno(id));
		return "alumnos/estado-cuenta";
	}

	@GetMapping("/alumnos/comprobantes/{id:\\d+}")
	public String comprobante(@PathVariable Long id, @RequestParam(name = "alumno", required = false) Long alumno,
			Model model) {
		model.addAttribute("comprobante", estados.comprobante(id));
		model.addAttribute("imprimirAlAbrir", false);
		model.addAttribute("volverUrl", alumno == null ? "/alumnos" : "/alumnos/" + alumno + "/estado-cuenta");
		model.addAttribute("volverTexto", alumno == null ? "Volver a alumnos" : "Volver al estado de cuenta");
		model.addAttribute("nuevoCobro", false);
		return "caja/comprobante";
	}

	@PostMapping("/alumnos/pagos/{id:\\d+}/devolucion")
	public String devolucion(@PathVariable Long id, @RequestParam(required = false) String motivo,
			@RequestParam(name = "alumno") Long alumno, RedirectAttributes avisos) {
		try {
			anulaciones.solicitarDevolucion(id, motivo);
			avisos.addFlashAttribute("exito", "Listo: pediste la anulación. Promotoría o Dirección la revisarán.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/alumnos/" + alumno + "/estado-cuenta";
	}

	@GetMapping("/alumnos/pagos/{id:\\d+}/correccion")
	public String correccion(@PathVariable Long id, @RequestParam(name = "q", required = false) String texto,
			@RequestParam(name = "familia", required = false) Long familia, Model model) {
		return vistaCorreccion(model, anulaciones.prepararCorreccion(id, familia, texto), "/alumnos/pagos", "/alumnos",
				"Alumnos");
	}

	@PostMapping("/alumnos/pagos/{id:\\d+}/correccion")
	public String pedirCorreccion(@PathVariable Long id, @Valid CorreccionRequest pedido, BindingResult validacion,
			RedirectAttributes avisos) {
		return pedirCorreccion(anulaciones, id, pedido, validacion, avisos, "/alumnos/pagos", "/alumnos");
	}

	/** La misma pantalla de corrección para Caja y Administración (solo cambian las rutas). */
	static String vistaCorreccion(Model model, CorreccionVista vista, String base, String volverUrl, String volverTexto) {
		model.addAttribute("correccion", vista);
		model.addAttribute("base", base);
		model.addAttribute("volverUrl", volverUrl);
		model.addAttribute("volverTexto", volverTexto);
		return "caja/correccion";
	}

	static String pedirCorreccion(ServicioAnulacionPagos anulaciones, Long id, CorreccionRequest pedido,
			BindingResult validacion, RedirectAttributes avisos, String base, String despues) {
		String volver = "redirect:" + base + "/" + id + "/correccion"
				+ (pedido != null && pedido.familiaId() != null ? "?familia=" + pedido.familiaId() : "");
		if (pedido == null || validacion.hasErrors()) {
			avisos.addFlashAttribute("error", validacion.hasErrors() ? Formularios.primerError(validacion)
					: "Elige la familia, las cuotas y el motivo.");
			return volver;
		}
		try {
			anulaciones.solicitarCorreccion(id, pedido);
			avisos.addFlashAttribute("exito", "Listo: pediste la corrección. Promotoría o Dirección la revisarán; "
					+ "mientras tanto el pago sigue como está.");
			return "redirect:" + despues;
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return volver;
		}
	}
}
