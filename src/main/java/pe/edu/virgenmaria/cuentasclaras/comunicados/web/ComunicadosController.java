package pe.edu.virgenmaria.cuentasclaras.comunicados.web;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.AdjuntoValidado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.BorradorComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.DetalleComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.RetiroRequest;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.AdjuntosComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.ConsultaComunicados;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.EnvioComunicados;

import java.io.IOException;

/**
 * Sprint 9, tanda 3 (secciones 3.8 a 3.10 y 10): los comunicados del personal. Sin lógica: delega en
 * {@link EnvioComunicados}, {@link ConsultaComunicados} y {@link AdjuntosComunicado} (que exigen los roles y la sección
 * del docente). La vista previa y el PDF ya revisado viven en la sesión de quien escribe (un solo PDF a la vez); el envío
 * vuelve a comprobar su huella.
 */
@Controller
public class ComunicadosController {

	static final String ADJUNTO_EN_SESION = "comunicados.adjunto";

	private final EnvioComunicados envio;

	private final ConsultaComunicados consulta;

	private final AdjuntosComunicado adjuntos;

	public ComunicadosController(EnvioComunicados envio, ConsultaComunicados consulta, AdjuntosComunicado adjuntos) {
		this.envio = envio;
		this.consulta = consulta;
		this.adjuntos = adjuntos;
	}

	@GetMapping("/comunicados")
	public String lista(Model model) {
		model.addAttribute("comunicados", consulta.lista());
		model.addAttribute("opciones", envio.opciones());
		return "comunicados/lista";
	}

	@GetMapping("/comunicados/nuevo")
	public String nuevo(HttpSession sesion, Model model) {
		sesion.removeAttribute(ADJUNTO_EN_SESION);
		model.addAttribute("opciones", envio.opciones());
		return "comunicados/nuevo";
	}

	/** Vista previa ({@code accion=previsualizar}) o envío ({@code accion=enviar}), con el PDF opcional. */
	@PostMapping("/comunicados/nuevo")
	public String enviar(@Valid BorradorComunicado borrador, BindingResult validacion,
			@RequestParam(name = "archivo", required = false) MultipartFile archivo,
			@RequestParam(name = "accion", defaultValue = "previsualizar") String accion, HttpSession sesion, Model model,
			RedirectAttributes avisos) {
		return procesar(null, borrador, validacion, archivo, accion, sesion, model, avisos);
	}

	@GetMapping("/comunicados/{id:\\d+}/fe-de-erratas")
	public String feDeErratas(@PathVariable long id, HttpSession sesion, Model model) {
		sesion.removeAttribute(ADJUNTO_EN_SESION);
		DetalleComunicado original = consulta.detalle(id);
		model.addAttribute("original", original);
		model.addAttribute("opciones", envio.opciones());
		return "comunicados/nuevo";
	}

	@PostMapping("/comunicados/{id:\\d+}/fe-de-erratas")
	public String enviarFeDeErratas(@PathVariable long id, @Valid BorradorComunicado borrador, BindingResult validacion,
			@RequestParam(name = "archivo", required = false) MultipartFile archivo,
			@RequestParam(name = "accion", defaultValue = "previsualizar") String accion, HttpSession sesion, Model model,
			RedirectAttributes avisos) {
		model.addAttribute("original", consulta.detalle(id));
		return procesar(id, borrador, validacion, archivo, accion, sesion, model, avisos);
	}

	@GetMapping("/comunicados/{id:\\d+}")
	public String detalle(@PathVariable long id, Model model) {
		model.addAttribute("c", consulta.detalle(id));
		return "comunicados/detalle";
	}

	@PostMapping("/comunicados/{id:\\d+}/retirar")
	public String retirar(@PathVariable long id, @Valid RetiroRequest pedido, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
		}
		else {
			try {
				envio.retirar(id, pedido.motivo());
				avisos.addFlashAttribute("exito", "Listo: el comunicado quedó retirado. Las familias ven la constancia.");
			}
			catch (ReglaNegocioException e) {
				avisos.addFlashAttribute("error", e.getMessage());
			}
		}
		return "redirect:/comunicados/" + id;
	}

	@GetMapping("/comunicados/{id:\\d+}/adjunto")
	public ResponseEntity<byte[]> adjunto(@PathVariable long id) {
		return DescargaPdf.de(id, consulta.adjunto(id));
	}

	private String procesar(Long corrige, BorradorComunicado borrador, BindingResult validacion, MultipartFile archivo,
			String accion, HttpSession sesion, Model model, RedirectAttributes avisos) {
		model.addAttribute("opciones", envio.opciones());
		model.addAttribute("borrador", borrador);
		if (validacion.hasErrors()) {
			model.addAttribute("error", Formularios.primerError(validacion));
			return "comunicados/nuevo";
		}
		try {
			if (archivo != null && !archivo.isEmpty()) {
				sesion.setAttribute(ADJUNTO_EN_SESION, adjuntos.validar(archivo.getOriginalFilename(), archivo.getBytes()));
			}
			else if ("quitar-adjunto".equals(accion)) {
				sesion.removeAttribute(ADJUNTO_EN_SESION);
			}
			AdjuntoValidado adjunto = (AdjuntoValidado) sesion.getAttribute(ADJUNTO_EN_SESION);
			if ("enviar".equals(accion)) {
				Long id = corrige == null ? envio.enviar(borrador, adjunto)
						: envio.enviarFeDeErratas(corrige, borrador, adjunto);
				sesion.removeAttribute(ADJUNTO_EN_SESION);
				avisos.addFlashAttribute("exito", "Listo: el comunicado quedó registrado. Aquí ves a cuántas familias "
						+ "llegó y quién confirmó la lectura.");
				return "redirect:/comunicados/" + id;
			}
			model.addAttribute("previa", envio.previsualizar(borrador, adjunto, corrige));
			model.addAttribute("adjunto", adjunto);
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
		}
		catch (IOException e) {
			model.addAttribute("error", "No se pudo leer el archivo: vuelve a adjuntarlo.");
		}
		return "comunicados/nuevo";
	}
}
