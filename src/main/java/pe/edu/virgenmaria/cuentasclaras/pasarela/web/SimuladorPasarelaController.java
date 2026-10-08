package pe.edu.virgenmaria.cuentasclaras.pasarela.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;

import java.util.EnumSet;
import java.util.Set;

/**
 * La página de la pasarela SIMULADA (solo dev, test y piloto), con la franja «SIMULADOR · NO ES DINERO REAL». Hace el
 * papel de la página alojada por la pasarela: el apoderado elige cómo «paga» y vuelve a nuestra página de estado.
 */
@Controller
@Profile({ "dev", "test", "piloto" })
@ConditionalOnProperty(name = "cuentasclaras.pasarela.proveedor", havingValue = "SIMULADA")
public class SimuladorPasarelaController {

	private final ServicioPagoEnLinea pagos;

	private final SimuladorPagos simulador;

	public SimuladorPasarelaController(ServicioPagoEnLinea pagos, SimuladorPagos simulador) {
		this.pagos = pagos;
		this.simulador = simulador;
	}

	@GetMapping("/familia/pasarela-simulada/{referencia:[0-9a-f-]{36}}")
	public String pagina(@PathVariable String referencia, Model model) {
		model.addAttribute("orden", pagos.estado(referencia));
		return "familia/pasarela-simulada";
	}

	/**
	 * Correcciones del sprint 4 (S4-B3): desde la página solo se elige cómo «paga» el apoderado (o que la pasarela
	 * rechace o cobre de menos, para probar la revisión). Un CONTRACARGO no lo provoca nadie desde la web (404).
	 */
	static final Set<PasarelaSimulada.Accion> ACCIONES_DE_LA_PAGINA = EnumSet.of(PasarelaSimulada.Accion.YAPE,
			PasarelaSimulada.Accion.TARJETA, PasarelaSimulada.Accion.RECHAZAR, PasarelaSimulada.Accion.MONTO_MENOR);

	@PostMapping("/familia/pasarela-simulada/{referencia:[0-9a-f-]{36}}/{accion:[A-Z_]{4,20}}")
	public String simular(@PathVariable String referencia, @PathVariable("accion") String nombre,
			RedirectAttributes avisos) {
		PasarelaSimulada.Accion accion = ACCIONES_DE_LA_PAGINA.stream().filter(a -> a.name().equals(nombre)).findFirst()
				.orElseThrow(() -> new RecursoNoEncontradoException("Página no encontrada"));
		try {
			simulador.simular(referencia, accion);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/familia/pagos/" + referencia;
	}
}
