package pe.edu.virgenmaria.cuentasclaras.plataforma.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.ConsultaAuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.FiltroBitacora;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.AltaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ContactoTitularRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.CorreoControlRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.FichaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.FichaPlataforma;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.ColegiosDeLaPlataforma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.AltaTitular.DatosTitular;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Sprint 8, tanda 1 (sección 11): las pantallas del operador de la plataforma. Sin lógica: delega en {@link AltaColegio} y
 * {@link ColegiosDeLaPlataforma}. Ninguna muestra datos de las familias.
 */
@Controller
@RequestMapping("/plataforma")
public class PlataformaController {

	private final AltaColegio alta;

	private final ColegiosDeLaPlataforma colegios;

	private final ConsultaAuditoriaService bitacora;

	private final Clock reloj;

	public PlataformaController(AltaColegio alta, ColegiosDeLaPlataforma colegios, ConsultaAuditoriaService bitacora,
			Clock reloj) {
		this.alta = alta;
		this.colegios = colegios;
		this.bitacora = bitacora;
		this.reloj = reloj;
	}

	@GetMapping
	public String lista(Model model) {
		model.addAttribute("colegios", colegios.todos());
		return "plataforma/lista";
	}

	@GetMapping("/colegios/nuevo")
	public String nuevo(Model model) {
		model.addAttribute("solicitud", AltaColegioRequest.vacio());
		model.addAttribute("niveles", Nivel.values());
		return "plataforma/nuevo";
	}

	@PostMapping("/colegios/nuevo")
	public String crear(@Valid @ModelAttribute("solicitud") AltaColegioRequest solicitud, BindingResult validacion,
			@RequestParam(name = "logo", required = false) MultipartFile logo, Model model) {
		model.addAttribute("niveles", Nivel.values());
		if (validacion.hasErrors()) {
			return "plataforma/nuevo";
		}
		try {
			byte[] bytes = logo == null || logo.isEmpty() ? null : logo.getBytes();
			ColegioCreado creado = alta.crear(solicitud, bytes, logo == null ? null : logo.getOriginalFilename());
			model.addAttribute("creado", creado);
			// Sprint 9, tanda 3 (B6): un nombre que calza con la regla de cobranza se avisa.
			pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza.advertenciaNombreColegio(creado.nombre())
					.ifPresent(a -> model.addAttribute("advertencia", a));
			return "plataforma/creado";
		}
		catch (ReglaNegocioException e) {
			model.addAttribute("error", e.getMessage());
			return "plataforma/nuevo";
		}
		catch (IOException e) {
			model.addAttribute("error", "No se pudo leer el archivo del logo. Prueba con otro archivo PNG o JPEG.");
			return "plataforma/nuevo";
		}
	}

	@GetMapping("/colegios/{id:\\d+}")
	public String ficha(@PathVariable Long id, Model model) {
		FichaPlataforma ficha = colegios.ficha(id);
		model.addAttribute("ficha", ficha);
		model.addAttribute("niveles", Nivel.values());
		if (!model.containsAttribute("fichaForm")) {
			model.addAttribute("fichaForm", new FichaColegioRequest(ficha.nombre(), ficha.razonSocial(), ficha.ruc(),
					ficha.direccion(), ficha.correoInstitucional(), ficha.niveles(), ficha.contratoCodigo(),
					ficha.titularNombre(), ""));
		}
		if (!model.containsAttribute("contacto")) {
			model.addAttribute("contacto", new ContactoTitularRequest("", "", ""));
		}
		if (!model.containsAttribute("correoControl")) {
			model.addAttribute("correoControl", new CorreoControlRequest("", ""));
		}
		return "plataforma/ficha";
	}

	@PostMapping("/colegios/{id:\\d+}/ficha")
	public String corregirFicha(@PathVariable Long id, @Valid @ModelAttribute("fichaForm") FichaColegioRequest ficha,
			BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/plataforma/colegios/" + id;
		}
		try {
			alta.corregirFicha(id, ficha);
			avisos.addFlashAttribute("exito", "Listo: la ficha del colegio quedó corregida.");
			pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza.advertenciaNombreColegio(ficha.nombre())
					.ifPresent(a -> avisos.addFlashAttribute("advertencia", a));
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/plataforma/colegios/" + id;
	}

	/** «Completar alta»: el colegio quedó sin titular (falló la firma o la cuenta); se reintenta con sus datos. */
	@PostMapping("/colegios/{id:\\d+}/completar")
	public String completar(@PathVariable Long id, @RequestParam String titularUsuario,
			@RequestParam String titularNombre, @RequestParam(required = false) String titularTelefono,
			@RequestParam(required = false) String titularCorreo, Model model, RedirectAttributes avisos) {
		try {
			model.addAttribute("creado", alta.completarAlta(id, new DatosTitular(titularUsuario, titularNombre,
					titularTelefono, titularCorreo)));
			return "plataforma/creado";
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/plataforma/colegios/" + id;
		}
	}

	/** S8-A1: reenviar el enlace al MISMO contacto del titular (con aviso al titular y al colegio). */
	@PostMapping("/colegios/{id:\\d+}/titular/reenviar")
	public String reenviar(@PathVariable Long id, Model model, RedirectAttributes avisos) {
		try {
			model.addAttribute("creado", alta.reenviarEnlaceTitular(id));
			return "plataforma/creado";
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/plataforma/colegios/" + id;
		}
	}

	/** S8-A1: proponer otro contacto del titular (lo aplica otro operador). */
	@PostMapping("/colegios/{id:\\d+}/titular")
	public String titular(@PathVariable Long id, @Valid @ModelAttribute("contacto") ContactoTitularRequest contacto,
			BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/plataforma/colegios/" + id;
		}
		try {
			alta.proponerContactoTitular(id, contacto);
			avisos.addFlashAttribute("exito", "Listo: propusiste el cambio del contacto del titular. Lo aplica otro operador, "
					+ "con su propia sesión.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/plataforma/colegios/" + id;
	}

	@PostMapping("/colegios/{id:\\d+}/titular/{cambio:\\d+}/aplicar")
	public String aplicarContacto(@PathVariable Long id, @PathVariable Long cambio, Model model,
			RedirectAttributes avisos) {
		try {
			model.addAttribute("creado", alta.aplicarContactoTitular(id, cambio));
			return "plataforma/creado";
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:/plataforma/colegios/" + id;
		}
	}

	@PostMapping("/colegios/{id:\\d+}/titular/{cambio:\\d+}/rechazar")
	public String rechazarContacto(@PathVariable Long id, @PathVariable Long cambio, RedirectAttributes avisos) {
		try {
			alta.rechazarContactoTitular(id, cambio);
			avisos.addFlashAttribute("exito", "Listo: el cambio del contacto del titular quedó rechazado.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/plataforma/colegios/" + id;
	}

	@PostMapping("/colegios/{id:\\d+}/correo-control")
	public String correoControl(@PathVariable Long id, @Valid @ModelAttribute("correoControl") CorreoControlRequest correo,
			BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:/plataforma/colegios/" + id;
		}
		try {
			boolean registrado = alta.registrarCorreoControl(id, correo.correo(), correo.motivo());
			avisos.addFlashAttribute("exito", registrado
					? "Listo: el correo externo de control quedó registrado. Recibirá el resumen diario y la huella de la "
							+ "bitácora del colegio. Se avisó al correo anterior, al institucional y a la Promotoría."
					: "Listo: propusiste el correo externo de control. Cambia cuando lo apruebe una persona de Promotoría del "
							+ "colegio; ya se le avisó, y también al correo anterior.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/plataforma/colegios/" + id;
	}

	@GetMapping("/bitacora")
	public String bitacora(@RequestParam(defaultValue = "0") int pagina, Model model) {
		model.addAttribute("eventos", bitacora.listar(new FiltroBitacora(null, null, null, null, false), pagina));
		model.addAttribute("acciones", AccionAuditoria.values());
		return "plataforma/bitacora";
	}
}
