package pe.edu.virgenmaria.cuentasclaras.familias.web;

import pe.edu.virgenmaria.cuentasclaras.familias.service.ServicioPreferencias;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoRequest;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.InicioFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.service.ConsultaEstadoCuentaFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.service.InicioPortalFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.service.ServicioAvisosFamilia;
import pe.edu.virgenmaria.cuentasclaras.matricula.dto.RenovacionFamilia;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioRenovacionFamilia;

/**
 * Portal de familias para el celular (sprint 5, pantallas 1, 2, 3, 5 y 6): inicio, estado de cuenta completo,
 * comprobantes, «¿Algo no cuadra?» y la renovación de matrícula. La familia sale SIEMPRE de la cuenta en sesión (los
 * servicios responden 404 con lo de otra familia). Sin lógica: delega en los servicios, que exigen el rol APODERADO. El
 * pago en línea sigue en {@code pasarela} y el historial de mensajes en {@code comunicacion}.
 */
@Controller
public class PortalFamiliaController {

	private final InicioPortalFamilia inicio;

	private final ConsultaEstadoCuentaFamilia estadoCuenta;

	private final ServicioAvisosFamilia avisos;

	private final ServicioRenovacionFamilia renovaciones;

	private final ServicioPreferencias preferencias;

	public PortalFamiliaController(InicioPortalFamilia inicio, ConsultaEstadoCuentaFamilia estadoCuenta,
			ServicioAvisosFamilia avisos, ServicioRenovacionFamilia renovaciones, ServicioPreferencias preferencias) {
		this.preferencias = preferencias;
		this.inicio = inicio;
		this.estadoCuenta = estadoCuenta;
		this.avisos = avisos;
		this.renovaciones = renovaciones;
	}

	@GetMapping("/familia")
	public String inicio(Model model) {
		InicioFamilia datos = inicio.inicio();
		model.addAttribute("inicio", datos);
		model.addAttribute("cuenta", datos.cuenta());
		model.addAttribute("recordatoriosActivos", preferencias.recordatoriosActivos());
		return "familia/inicio";
	}

	/** Tanda 3: el apoderado apaga o enciende SUS recordatorios (los avisos de pago no se apagan). */
	@PostMapping("/familia/preferencias")
	public String preferencias(@RequestParam(required = false) Boolean recordatorios, RedirectAttributes mensajes) {
		// QA-S5-2: sin el campo no se decide nada por el apoderado (antes, un POST vacío los apagaba).
		if (recordatorios == null) {
			mensajes.addFlashAttribute("error", "No recibimos tu elección. Vuelve a intentarlo.");
			return "redirect:/familia";
		}
		preferencias.recordatorios(recordatorios);
		mensajes.addFlashAttribute("exito", recordatorios ? "Listo: te recordaremos tus vencimientos."
				: "Listo: ya no te enviaremos recordatorios. Los avisos de tus pagos siguen llegando.");
		return "redirect:/familia";
	}

	@GetMapping("/familia/estado-de-cuenta")
	public String estadoDeCuenta(Model model) {
		model.addAttribute("estado", estadoCuenta.deMiFamilia());
		return "familia/estado-de-cuenta";
	}

	@GetMapping("/familia/comprobantes")
	public String comprobantes(Model model) {
		model.addAttribute("comprobantes", estadoCuenta.comprobantes());
		return "familia/comprobantes";
	}

	@GetMapping("/familia/algo-no-cuadra")
	public String algoNoCuadra(Model model) {
		model.addAttribute("aviso", AvisoRequest.vacio());
		prepararAviso(model);
		return "familia/algo-no-cuadra";
	}

	@PostMapping("/familia/algo-no-cuadra")
	public String enviarAviso(@Valid @ModelAttribute("aviso") AvisoRequest aviso, BindingResult validacion, Model model,
			RedirectAttributes mensajes) {
		if (!validacion.hasErrors()) {
			try {
				avisos.enviar(aviso);
				mensajes.addFlashAttribute("exito", "Lo recibió Promotoría. Te responderemos aquí.");
				return "redirect:/familia/algo-no-cuadra";
			}
			catch (ReglaNegocioException e) {
				model.addAttribute("error", e.getMessage());
			}
		}
		prepararAviso(model);
		return "familia/algo-no-cuadra";
	}

	@GetMapping("/familia/matricula/{id:\\d+}")
	public String renovacion(@PathVariable Long id, Model model) {
		model.addAttribute("renovacion", renovaciones.una(id));
		return "familia/matricula";
	}

	@PostMapping("/familia/matricula/{id:\\d+}")
	public String responder(@PathVariable Long id, @RequestParam(required = false) Boolean continua,
			RedirectAttributes mensajes) {
		// QA-S5-2: un POST sin la respuesta (doble envío, botón que no llegó) no registra «no continúa» para siempre.
		if (continua == null) {
			mensajes.addFlashAttribute("error", "No recibimos tu respuesta. Elige «Sí, continúa» o «No continuará».");
			return "redirect:/familia/matricula/" + id;
		}
		try {
			RenovacionFamilia respuesta = renovaciones.responder(id, continua);
			mensajes.addFlashAttribute("exito", continua ? "Listo. Ya puedes pagar la matrícula de " + respuesta.alumno()
					+ " en «Lo que debes»." : "Listo: registramos que " + respuesta.alumno() + " no continuará.");
		}
		catch (ReglaNegocioException e) {
			mensajes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/familia/matricula/" + id;
	}

	private void prepararAviso(Model model) {
		model.addAttribute("tipos", TipoAvisoFamilia.values());
		model.addAttribute("opciones", avisos.opciones());
		model.addAttribute("misAvisos", avisos.misAvisos());
	}
}
