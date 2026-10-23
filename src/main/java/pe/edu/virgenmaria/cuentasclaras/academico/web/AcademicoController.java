package pe.edu.virgenmaria.cuentasclaras.academico.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ConsultaAcademica;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;

/**
 * Sprint 9, tanda 1 (sección 10): el inicio académico y la lista de una sección. Sin lógica: delega en
 * {@link ConsultaAcademica}, que exige el rol (Dirección o Docente) y la asignación vigente (404 si no es suya). La lista
 * queda en «Quién vio datos personales» ({@code LISTA_SECCION}, con la cantidad).
 */
@Controller
@RequestMapping("/academico")
public class AcademicoController {

	private final ConsultaAcademica consulta;

	public AcademicoController(ConsultaAcademica consulta) {
		this.consulta = consulta;
	}

	@GetMapping
	public String inicio(Model model) {
		model.addAttribute("inicio", consulta.inicio());
		return "academico/inicio";
	}

	@GetMapping("/secciones/{id:\\d+}")
	@RegistraAcceso(TipoAcceso.LISTA_SECCION)
	public String seccion(@PathVariable long id, Model model) {
		model.addAttribute("lista", consulta.lista(id));
		return "academico/seccion";
	}
}
