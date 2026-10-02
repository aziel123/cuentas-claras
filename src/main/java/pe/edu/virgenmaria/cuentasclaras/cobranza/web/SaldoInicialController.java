package pe.edu.virgenmaria.cuentasclaras.cobranza.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MotivoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;

import java.time.Clock;
import java.time.LocalDate;

/** Saldo inicial: lotes con total de control y doble control. Sin lógica: delega en {@link ServicioSaldoInicial}. */
@Controller
@RequestMapping("/pensiones/saldo-inicial")
public class SaldoInicialController {

	private static final String VISTA_LISTA = "pensiones/saldo-inicial-lista";

	private static final String VISTA_LOTE = "pensiones/saldo-inicial-lote";

	private final ServicioSaldoInicial servicio;

	private final Clock reloj;

	public SaldoInicialController(ServicioSaldoInicial servicio, Clock reloj) {
		this.servicio = servicio;
		this.reloj = reloj;
	}

	@GetMapping
	public String lista(Model model) {
		model.addAttribute("solicitud", new LoteRequest(null, LocalDate.now(reloj), null, null));
		prepararLista(model);
		return VISTA_LISTA;
	}

	@PostMapping
	public String crear(@Valid @ModelAttribute("solicitud") LoteRequest solicitud, BindingResult validacion,
			Model model, RedirectAttributes avisos) {
		if (!validacion.hasErrors()) {
			try {
				Long id = servicio.crearLote(solicitud);
				avisos.addFlashAttribute("exito", "Listo: creaste el lote. Agrega las deudas hasta que la suma cuadre "
						+ "con el total declarado.");
				return "redirect:/pensiones/saldo-inicial/" + id;
			}
			catch (ReglaNegocioException e) {
				model.addAttribute("error", e.getMessage());
			}
		}
		prepararLista(model);
		return VISTA_LISTA;
	}

	@GetMapping("/{id:\\d+}")
	public String lote(@PathVariable Long id, Model model) {
		if (!model.containsAttribute("linea")) {
			model.addAttribute("linea", new LineaSaldoRequest(null, ConceptoSaldo.PENSION, null, null, null, null));
		}
		prepararLote(model, id);
		return VISTA_LOTE;
	}

	@PostMapping("/{id:\\d+}/lineas")
	public String agregarLinea(@PathVariable Long id, @Valid @ModelAttribute("linea") LineaSaldoRequest solicitud,
			BindingResult validacion, Model model, RedirectAttributes avisos) {
		if (!validacion.hasErrors()) {
			try {
				servicio.agregarLinea(id, solicitud);
				avisos.addFlashAttribute("exito", "Listo: agregaste la deuda.");
				return "redirect:/pensiones/saldo-inicial/" + id;
			}
			catch (ReglaNegocioException e) {
				model.addAttribute("error", e.getMessage());
			}
		}
		prepararLote(model, id);
		return VISTA_LOTE;
	}

	@PostMapping("/{id:\\d+}/lineas/{lineaId:\\d+}/quitar")
	public String quitarLinea(@PathVariable Long id, @PathVariable Long lineaId, @Valid MotivoRequest solicitud,
			BindingResult validacion, RedirectAttributes avisos) {
		return conMotivo(id, solicitud, validacion, avisos, () -> servicio.quitarLinea(id, lineaId, solicitud.motivo()),
				"Listo: quitaste la línea. Queda visible como quitada.");
	}

	@PostMapping("/{id:\\d+}/enviar")
	public String enviar(@PathVariable Long id, RedirectAttributes avisos) {
		return ejecutar(id, avisos, () -> servicio.enviar(id),
				"Listo: enviaste el lote. Ahora debe confirmarlo otra persona de Promotoría o Dirección.");
	}

	@PostMapping("/{id:\\d+}/confirmar")
	public String confirmar(@PathVariable Long id, RedirectAttributes avisos) {
		try {
			int creadas = servicio.confirmar(id);
			avisos.addFlashAttribute("exito", "Listo: confirmaste el lote. Se crearon " + creadas
					+ " cuotas de saldo inicial en los cronogramas.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/pensiones/saldo-inicial/" + id;
	}

	@PostMapping("/{id:\\d+}/devolver")
	public String devolver(@PathVariable Long id, @Valid MotivoRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		return conMotivo(id, solicitud, validacion, avisos, () -> servicio.devolver(id, solicitud.motivo()),
				"Listo: devolviste el lote a Administración para que lo corrija.");
	}

	@PostMapping("/{id:\\d+}/descartar")
	public String descartar(@PathVariable Long id, @Valid MotivoRequest solicitud, BindingResult validacion,
			RedirectAttributes avisos) {
		return conMotivo(id, solicitud, validacion, avisos, () -> servicio.descartar(id, solicitud.motivo()),
				"Listo: descartaste el lote. Queda en la lista como descartado.");
	}

	private String conMotivo(Long id, MotivoRequest solicitud, BindingResult validacion, RedirectAttributes avisos,
			Runnable accion, String exito) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/pensiones/saldo-inicial/" + id;
		}
		return ejecutar(id, avisos, accion, exito);
	}

	private String ejecutar(Long id, RedirectAttributes avisos, Runnable accion, String exito) {
		try {
			accion.run();
			avisos.addFlashAttribute("exito", exito);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/pensiones/saldo-inicial/" + id;
	}

	private void prepararLista(Model model) {
		model.addAttribute("lotes", servicio.listar());
		model.addAttribute("anios", servicio.aniosAbiertos());
		model.addAttribute("puedeCrear", servicio.puedeArmarLotes());
	}

	private void prepararLote(Model model, Long id) {
		model.addAttribute("lote", servicio.obtener(id));
		model.addAttribute("conceptos", ConceptoSaldo.values());
		model.addAttribute("meses", Calendario.nombresDeMeses());
	}
}
