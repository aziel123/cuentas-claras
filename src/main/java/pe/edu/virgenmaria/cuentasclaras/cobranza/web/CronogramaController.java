package pe.edu.virgenmaria.cuentasclaras.cobranza.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.ResultadoGeneracion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.GeneradorCronograma;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioCronograma;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;

/**
 * Cronogramas: la pestaña «Cronograma» de la ficha del alumno y las matrículas pendientes de generar con el botón
 * idempotente «Generar pendientes».
 */
@Controller
public class CronogramaController {

	private final ServicioCronograma cronogramas;

	private final GeneradorCronograma generador;

	public CronogramaController(ServicioCronograma cronogramas, GeneradorCronograma generador) {
		this.cronogramas = cronogramas;
		this.generador = generador;
	}

	@GetMapping("/alumnos/{id:\\d+}/cronograma")
	@RegistraAcceso(TipoAcceso.FICHA_ALUMNO)
	public String deAlumno(@PathVariable Long id, Model model) {
		var cronograma = cronogramas.deAlumno(id);
		model.addAttribute("cronograma", cronograma);
		// Correcciones del sprint 7 (QA-S7-4): muestra el nombre y el DNI del alumno: cuenta como su ficha.
		AccesoMostrado.alumno(id, cronograma.alumno().familiaId());
		return "alumnos/cronograma";
	}

	@GetMapping("/pensiones/cronogramas")
	public String pendientes(@RequestParam(name = "anio", required = false) Long anioId, Model model) {
		model.addAttribute("pendientes", cronogramas.pendientes(anioId));
		return "pensiones/cronogramas";
	}

	@PostMapping("/pensiones/cronogramas/generar")
	public String generar(@RequestParam("anio") Long anioId, RedirectAttributes avisos) {
		try {
			ResultadoGeneracion resultado = generador.generarPendientes(anioId);
			avisos.addFlashAttribute("exito", resultado.matriculas() == 0
					? "No había cronogramas pendientes: no se generó nada."
					: "Listo: se generaron " + resultado.cuotas() + " cuotas para " + resultado.matriculas()
							+ " matrículas (" + Dinero.formatear(resultado.total()) + ").");
			if (!resultado.omitidas().isEmpty()) {
				avisos.addFlashAttribute("advertencia", resultado.omitidas().size()
						+ " cuotas no se generaron porque esa deuda ya existía (por ejemplo, como saldo inicial).");
			}
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/pensiones/cronogramas?anio=" + anioId;
	}
}
