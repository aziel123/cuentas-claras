package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.ListaVerificacion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PasoAProduccion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/**
 * Sprint 8 (secciones 3.4 y 3.5): la lista de verificación (Promotoría, Dirección y Administración), los dos «aceptos»
 * firmados y el paso a producción (solo Promotoría, con la lista completa). Sin lógica: delega en {@link PasoAProduccion}.
 */
@Controller
public class PuestaEnMarchaController {

	private final PasoAProduccion paso;

	public PuestaEnMarchaController(PasoAProduccion paso) {
		this.paso = paso;
	}

	@GetMapping("/puesta-en-marcha")
	public String lista(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
		ListaVerificacion lista = paso.lista();
		model.addAttribute("lista", lista);
		model.addAttribute("pendientes", PasoAProduccion.pendientesDe(lista));
		model.addAttribute("puedePasar", usuario != null && usuario.roles().contains(Rol.PROMOTOR));
		return "puesta-en-marcha/lista";
	}

	@PostMapping("/puesta-en-marcha/produccion")
	public String produccion(RedirectAttributes avisos) {
		try {
			paso.pasar();
			avisos.addFlashAttribute("exito", "Listo: el colegio está en producción. Desde ahora Caja registra pagos y las "
					+ "familias reciben sus avisos. Toda la Promotoría y el correo externo de control recibieron el aviso.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/puesta-en-marcha";
	}

	@PostMapping("/puesta-en-marcha/sin-saldo-inicial")
	public String sinSaldoInicial(RedirectAttributes avisos) {
		try {
			paso.sinSaldoInicial();
			avisos.addFlashAttribute("exito", "Listo: firmaste que el colegio empieza sin deudas anteriores. Quedó en la "
					+ "bitácora.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/puesta-en-marcha";
	}

	@PostMapping("/puesta-en-marcha/ose-simulado")
	public String oseSimulado(RedirectAttributes avisos) {
		try {
			paso.aceptarOseSimulado();
			avisos.addFlashAttribute("exito", "Listo: firmaste que el colegio usa el comprobante simulado y emite su "
					+ "comprobante legal aparte. Quedó en la bitácora.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/puesta-en-marcha";
	}
}
