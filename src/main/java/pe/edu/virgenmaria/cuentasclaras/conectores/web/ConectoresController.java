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

	private final pe.edu.virgenmaria.cuentasclaras.conectores.service.EsperaConectores espera;

	private final pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PasoAProduccion aceptos;

	private final pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha puesta;

	public ConectoresController(ConsultaConectores consulta, PropuestaConector propuesta,
			pe.edu.virgenmaria.cuentasclaras.conectores.service.EsperaConectores espera,
			pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PasoAProduccion aceptos,
			pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha puesta) {
		this.aceptos = aceptos;
		this.puesta = puesta;
		this.consulta = consulta;
		this.propuesta = propuesta;
		this.espera = espera;
	}

	/** Sprint 11, tanda 1c (decisión 218): el código del socio para aprobar al instante un conector en espera. */
	@PostMapping("/conectores/{id:\\d{1,12}}/codigo-socio")
	public String codigoSocio(@PathVariable Long id, RedirectAttributes avisos) {
		try {
			pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria.Pedido pedido = espera.pedirCodigoDelSocio(id);
			avisos.addFlashAttribute("pedido", pedido);
			avisos.addFlashAttribute("pedidoConector", id);
			avisos.addFlashAttribute("codigoDemostracion", espera.codigoDeDemostracion(pedido).orElse(null));
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/conectores";
	}

	/** Sprint 11, tanda 1c (decisión 218, A1c-2): el socio lo aprueba al instante con su código. */
	@PostMapping("/conectores/{id:\\d{1,12}}/aprobar-ahora")
	public String aprobarAhora(@PathVariable Long id,
			@org.springframework.web.bind.annotation.RequestParam(required = false) Long codigoMensajeId,
			@org.springframework.web.bind.annotation.RequestParam(required = false) String codigo, RedirectAttributes avisos) {
		try {
			espera.aprobarAhora(id, pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria.Escrito.de(
					codigoMensajeId, codigo));
			avisos.addFlashAttribute("exito", "Listo: lo aprobaste al instante y entró en vigencia. Se avisó a todas las "
					+ "Promotorías y al correo de control.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/conectores";
	}

	/** Sprint 11, tanda 1c (decisión 218, A1c-2): otra Promotoría con 30 días en el rol lo frena durante la espera. */
	@PostMapping("/conectores/{id:\\d{1,12}}/frenar")
	public String frenar(@PathVariable Long id,
			@org.springframework.web.bind.annotation.RequestParam(required = false) String motivo, RedirectAttributes avisos) {
		try {
			espera.frenar(id, motivo);
			avisos.addFlashAttribute("exito", "Listo: lo frenaste. No entra en vigencia; se avisó a todos.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/conectores";
	}

	@GetMapping("/conectores")
	public String lista(@org.springframework.security.core.annotation.AuthenticationPrincipal
			pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado usuario, Model model) {
		model.addAttribute("vista", consulta.vista());
		// Sprint 11, tanda 1b (F-D8, decisión 213): el acepto del OSE simulado se ofrece en el mismo formulario.
		model.addAttribute("esPromotoria", usuario != null
				&& usuario.roles().contains(pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.PROMOTOR));
		model.addAttribute("oseAceptado", puesta.actual().hecho(
				pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha.OSE));
		return "conectores/lista";
	}

	@PostMapping("/conectores/{tipo:[a-z-]{3,20}}")
	public String proponer(@PathVariable String tipo, @Valid @ModelAttribute PropuestaConectorRequest pedido,
			BindingResult validacion,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean aceptoOseSimulado,
			RedirectAttributes avisos) {
		TipoConector conector = tipoDe(tipo);
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/conectores";
		}
		try {
			propuesta.proponer(conector, pedido);
			avisos.addFlashAttribute("exito", "Listo: propusiste el cambio de «" + conector.etiqueta().toLowerCase()
					+ "». Ahora prueba la conexión; después otra persona de Promotoría o Dirección lo aprueba en su bandeja.");
			// F-D8: con la casilla marcada, la Promotoría firma el acepto del simulado en el mismo envío (la misma firma de
			// siempre: PasoAProduccion.aceptarOseSimulado, con su clave, bitácora y avisos). Exige el rol en el servicio.
			if (aceptoOseSimulado && conector == TipoConector.OSE
					&& pedido.modo() == pe.edu.virgenmaria.cuentasclaras.conectores.model.ModoConector.SIMULADO
					&& !puesta.actual().hecho(pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha.OSE)) {
				aceptos.aceptarOseSimulado();
				avisos.addFlashAttribute("exito", "Listo: propusiste el comprobante simulado y firmaste su acepto.");
			}
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
