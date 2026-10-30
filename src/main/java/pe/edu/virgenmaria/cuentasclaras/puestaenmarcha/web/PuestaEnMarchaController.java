package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.ListaVerificacion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.CorreoDeControl;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.GuiaPuestaEnMarcha;
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

	private final CorreoDeControl correoDeControl;

	private final EstadoPuestaEnMarcha estado;

	public PuestaEnMarchaController(PasoAProduccion paso, CorreoDeControl correoDeControl, EstadoPuestaEnMarcha estado) {
		this.paso = paso;
		this.estado = estado;
		this.correoDeControl = correoDeControl;
	}

	@GetMapping("/puesta-en-marcha")
	public String lista(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
		ListaVerificacion lista = paso.lista();
		java.util.Set<Rol> roles = usuario == null ? java.util.Set.of() : usuario.roles();
		model.addAttribute("lista", lista);
		model.addAttribute("pendientes", PasoAProduccion.pendientesDe(lista));
		model.addAttribute("puedePasar", roles.contains(Rol.PROMOTOR));
		// Sprint 11, tanda 1b (sección 3.14): estado, responsable e «Ir» de cada paso, y el mismo avance del banner.
		model.addAttribute("guia", GuiaPuestaEnMarcha.guia(lista, roles));
		model.addAttribute("filas", GuiaPuestaEnMarcha.filas(lista, roles));
		// F-D8: con un lote de saldo inicial en curso, primero «Ir al lote»; si no, primero la firma.
		model.addAttribute("loteEnCurso", estado.loteSaldoEnCurso().orElse(null));
		model.addAttribute("correoControl", correoDeControl.vista());
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
			avisos.addFlashAttribute("exito", "Listo: firmaste que el colegio empieza sin deudas anteriores.");
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
			avisos.addFlashAttribute("exito", "Listo: firmaste el uso del comprobante simulado.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/puesta-en-marcha";
	}

	/** Correcciones del sprint 8 (S8-A2): la Promotoría aprueba con su firma el correo de control que propuso el operador. */
	@PostMapping("/puesta-en-marcha/correo-control/{id}/aprobar")
	public String aprobarCorreoControl(@PathVariable Long id, RedirectAttributes avisos) {
		try {
			correoDeControl.aprobar(id);
			avisos.addFlashAttribute("exito", "Listo: aprobaste el correo externo de control. Desde ahora recibe la huella y el "
					+ "resumen diario. Se avisó al correo anterior, al nuevo y a toda la Promotoría.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/puesta-en-marcha";
	}

	@PostMapping("/puesta-en-marcha/correo-control/{id}/rechazar")
	public String rechazarCorreoControl(@PathVariable Long id, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		try {
			correoDeControl.rechazar(id, motivo);
			avisos.addFlashAttribute("exito", "Listo: rechazaste el cambio. Sigue el correo de control anterior.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/puesta-en-marcha";
	}
}
