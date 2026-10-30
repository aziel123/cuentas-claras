package pe.edu.virgenmaria.cuentasclaras.aprobaciones.web;

import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioContactoPersonal;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.List;

/**
 * Bandeja de aprobaciones. Sin lógica: delega en {@link BandejaAprobaciones} (que exige el rol). Sprint 6, tanda 2
 * (decisión 72): la vista para el celular ({@code ?vista=movil}) y el detalle de una solicitud ({@code GET /{id}}) usan la
 * MISMA bandeja y los mismos POST con CSRF: ningún enlace (GET) aprueba nada.
 */
@Controller
@RequestMapping("/aprobaciones")
public class AprobacionesController {

	static final String MOVIL = "movil";

	private final BandejaAprobaciones bandeja;

	private final ServicioContactoPersonal contactos;

	private final pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaExterna externa;

	public AprobacionesController(BandejaAprobaciones bandeja, ServicioContactoPersonal contactos,
			pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaExterna externa) {
		this.externa = externa;
		this.bandeja = bandeja;
		this.contactos = contactos;
	}

	@GetMapping
	public String bandeja(@RequestParam(required = false) String vista, Model model) {
		model.addAttribute("bandeja", bandeja.bandeja());
		// Sprint 11, tanda 1b (F-D5): lo que se aprueba en otra pantalla, con «Revisar» (no se aprueba aquí).
		var externos = externa.pendientes();
		model.addAttribute("externos", externos.stream().filter(e -> e.puedeResolver()).toList());
		model.addAttribute("externosEnEspera", externos.stream().filter(e -> !e.puedeResolver()).toList());
		return MOVIL.equals(vista) ? "aprobaciones/movil" : "aprobaciones/bandeja";
	}

	@GetMapping("/{id:\\d+}")
	@RegistraAcceso(TipoAcceso.APROBACION_CONTACTO)
	public String detalle(@PathVariable Long id, Model model) {
		model.addAttribute("s", bandeja.detalle(id));
		// Sprint 11, tanda 1c (A1c-1): la dueña de una cuenta directiva confirma el cambio de su contacto.
		model.addAttribute("confirmarContacto", contactos.puedeConfirmar(id));
		// Ley 29733: si es el cambio de contacto de un apoderado, queda registrado quién lo abrió (lo decide privacidad).
		AccesoMostrado.solicitud(id);
		return "aprobaciones/detalle";
	}

	@PostMapping("/{id:\\d+}/aprobar")
	public String aprobar(@PathVariable Long id, @RequestParam(required = false) String comentario,
			@RequestParam(defaultValue = "false") boolean hablo, @RequestParam(required = false) List<String> telefonos,
			@RequestParam(required = false) Long codigoMensajeId, @RequestParam(required = false) String codigo,
			@RequestParam(required = false) String volver, RedirectAttributes avisos) {
		return resolver(avisos, () -> bandeja.aprobar(id, comentario, hablo, telefonos,
				CodigoPromotoria.Escrito.de(codigoMensajeId, codigo)),
				codigoMensajeId == null ? "Listo: aprobaste la solicitud y el cambio se aplicó."
						: "Listo: se aprobó y se aplicó (o quedó en su espera de 24 horas). Se avisó a quien corresponde.",
				volver);
	}

	/** Sprint 11, tanda 1c: el código de una acción sensible que pidió la Promotoría (le llega a su WhatsApp). */
	@PostMapping("/{id:\\d+}/codigo")
	public String codigo(@PathVariable Long id, RedirectAttributes avisos) {
		try {
			CodigoPromotoria.Pedido pedido = bandeja.pedirCodigo(id);
			avisos.addFlashAttribute("pedido", pedido);
			avisos.addFlashAttribute("codigoDemostracion", bandeja.codigoDeDemostracion(pedido).orElse(null));
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/aprobaciones/" + id;
	}

	/** Sprint 11, tanda 1c (A1c-2, A1c-3): frena una directa que espera. */
	@PostMapping("/{id:\\d+}/frenar")
	public String frenar(@PathVariable Long id, @RequestParam(required = false) String motivo, RedirectAttributes avisos) {
		return resolver(avisos, () -> bandeja.frenar(id, motivo), "Listo: lo frenaste. No se aplica; se avisó a todos.",
				MOVIL);
	}

	/** Sprint 11, tanda 1c (A1c-1): el código al contacto anterior para confirmar el cambio del propio contacto. */
	@PostMapping("/{id:\\d+}/confirmar-contacto/codigo")
	public String codigoContacto(@PathVariable Long id, RedirectAttributes avisos) {
		try {
			CodigoPromotoria.Pedido pedido = contactos.pedirCodigoDelContactoAnterior(id);
			avisos.addFlashAttribute("pedidoContacto", pedido);
			avisos.addFlashAttribute("codigoDemostracion", contactos.codigoDeDemostracion(pedido).orElse(null));
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/aprobaciones/" + id;
	}

	/** Sprint 11, tanda 1c (A1c-1): confirma el cambio del propio contacto con el código del anterior. */
	@PostMapping("/{id:\\d+}/confirmar-contacto")
	public String confirmarContacto(@PathVariable Long id, @RequestParam(required = false) Long codigoMensajeId,
			@RequestParam(required = false) String codigo, RedirectAttributes avisos) {
		try {
			contactos.confirmarDesdeContactoAnterior(id, CodigoPromotoria.Escrito.de(codigoMensajeId, codigo));
			avisos.addFlashAttribute("exito", "Listo: confirmaste el cambio desde tu contacto anterior. Ahora lo aprueba otra "
					+ "persona de Promotoría o Dirección.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/aprobaciones/" + id;
	}

	@PostMapping("/{id:\\d+}/rechazar")
	public String rechazar(@PathVariable Long id, @RequestParam(required = false) String motivo,
			@RequestParam(required = false) String volver, RedirectAttributes avisos) {
		return resolver(avisos, () -> bandeja.rechazar(id, motivo), "Listo: rechazaste la solicitud. No se cambió nada.",
				volver);
	}

	/** Vuelve a la vista del celular solo si se pidió; cualquier otro valor vuelve a la bandeja (sin redirección abierta). */
	private static String resolver(RedirectAttributes avisos, Runnable accion, String exito, String volver) {
		try {
			accion.run();
			avisos.addFlashAttribute("exito", exito);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		catch (OptimisticLockingFailureException e) {
			avisos.addFlashAttribute("error", "Otra persona cambió esto al mismo tiempo; revísalo de nuevo.");
		}
		return MOVIL.equals(volver) ? "redirect:/aprobaciones?vista=" + MOVIL : "redirect:/aprobaciones";
	}
}
