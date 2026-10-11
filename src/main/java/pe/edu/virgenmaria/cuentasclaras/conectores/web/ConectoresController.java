package pe.edu.virgenmaria.cuentasclaras.conectores.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.PropuestaConectorRequest;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.AdaptadorConector.ResultadoPrueba;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.ConsultaConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.PropuestaConector;

import java.util.Locale;

/**
 * Sprint 8, tanda 2 (sección 3.6): los conectores del colegio. {@code GET /conectores} (Promotoría, Dirección y
 * Administración: sin secretos, solo la pista); {@code POST /conectores/{tipo}} (proponer: Promotoría o Administración;
 * crea la solicitud que aprueba OTRA persona en la bandeja) y {@code POST /conectores/{id}/probar} (probar la conexión de
 * uno propuesto). Sin lógica: delega en {@link ConsultaConectores} y {@link PropuestaConector}.
 */
@Controller
public class ConectoresController {

	private final ConsultaConectores consulta;

	private final PropuestaConector propuesta;

	public ConectoresController(ConsultaConectores consulta, PropuestaConector propuesta) {
		this.consulta = consulta;
		this.propuesta = propuesta;
	}

	@GetMapping("/conectores")
	public String lista(Model model) {
		model.addAttribute("vista", consulta.vista());
		return "conectores/lista";
	}

	@PostMapping("/conectores/{tipo:[a-z-]{3,20}}")
	public String proponer(@PathVariable String tipo, @Valid @ModelAttribute PropuestaConectorRequest pedido,
			BindingResult validacion, RedirectAttributes avisos) {
		TipoConector conector = tipoDe(tipo);
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/conectores";
		}
		try {
			propuesta.proponer(conector, pedido);
			avisos.addFlashAttribute("exito", "Listo: propusiste el cambio de «" + conector.etiqueta().toLowerCase()
					+ "». Ahora prueba la conexión; después otra persona de Promotoría o Dirección lo aprueba en su bandeja.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/conectores";
	}

	@PostMapping("/conectores/{id:\\d{1,12}}/probar")
	public String probar(@PathVariable Long id, RedirectAttributes avisos) {
		try {
			ResultadoPrueba resultado = propuesta.probar(id);
			avisos.addFlashAttribute(resultado.ok() ? "exito" : "error", (resultado.ok() ? "Conexión correcta: "
					: "La prueba falló: ") + resultado.detalle());
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/conectores";
	}

	private static TipoConector tipoDe(String ruta) {
		try {
			return TipoConector.valueOf(ruta.toUpperCase(Locale.ROOT).replace('-', '_'));
		}
		catch (IllegalArgumentException e) {
			throw new RecursoNoEncontradoException("Tipo de conector no encontrado");
		}
	}
}
