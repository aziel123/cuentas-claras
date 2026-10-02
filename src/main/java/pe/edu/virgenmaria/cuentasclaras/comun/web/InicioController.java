package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;

/**
 * Página de inicio. En el sprint 1 se reemplaza por el inicio de sesión y el menú por rol.
 */
@Controller
public class InicioController {

	private final ColegioRepository colegioRepository;

	public InicioController(ColegioRepository colegioRepository) {
		this.colegioRepository = colegioRepository;
	}

	@GetMapping("/")
	public String inicio(Model model) {
		String nombre = colegioRepository.findByActivoTrueOrderByIdAsc().stream()
				.findFirst()
				.map(Colegio::getNombre)
				.orElse("Colegio sin configurar");
		model.addAttribute("nombreColegio", nombre);
		return "inicio";
	}
}
