package pe.edu.virgenmaria.cuentasclaras.recaudacion.web;

import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.DevolucionLineaRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.ArchivoCargado;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.AplicacionLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.dto.VistaPreviaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ConsultaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioExcepcionesRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ServicioRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Recaudación bancaria: subir el archivo del banco en 3 pasos (subir → revisar → registrar), confirmarlo a ciegas (otra
 * persona), ver los lotes y resolver las líneas por revisar. Sin lógica: delega en los servicios, que exigen el rol de
 * cada acción. La revisión vive SOLO en la sesión de quien subió el archivo (clave {@value #CLAVE_SESION}).
 */
@Controller
@RequestMapping("/recaudacion")
public class RecaudacionController {

	public static final String CLAVE_SESION = "recaudacionPendiente";

	private static final String RUTA = "/recaudacion";

	private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");

	private final ServicioRecaudacion servicio;

	private final ConsultaRecaudacion consulta;

	private final ServicioExcepcionesRecaudacion excepciones;

	public RecaudacionController(ServicioRecaudacion servicio, ConsultaRecaudacion consulta,
			ServicioExcepcionesRecaudacion excepciones) {
		this.servicio = servicio;
		this.consulta = consulta;
		this.excepciones = excepciones;
	}

	@GetMapping
	public String lista(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
		model.addAttribute("r", consulta.lista());
		// Solo para mostrar u ocultar botones: cada servicio exige su rol.
		model.addAttribute("puedeSubir", usuario != null && usuario.roles().contains(Rol.ADMINISTRACION));
		model.addAttribute("puedeConfirmar", usuario != null
				&& (usuario.roles().contains(Rol.PROMOTOR) || usuario.roles().contains(Rol.DIRECTOR)));
		return "recaudacion/lista";
	}

	@PostMapping("/vista-previa")
	public String subir(@RequestParam(name = "archivo", required = false) MultipartFile archivo, HttpSession sesion,
			RedirectAttributes avisos) throws IOException {
		sesion.removeAttribute(CLAVE_SESION);
		if (archivo == null || archivo.isEmpty()) {
			avisos.addFlashAttribute("error", "Elige el archivo del banco que quieres subir.");
			return "redirect:" + RUTA;
		}
		byte[] contenido = archivo.getSize() > ArchivoCargado.MAXIMO_BYTES
				? archivo.getInputStream().readNBytes(ArchivoCargado.MAXIMO_BYTES + 1) : archivo.getBytes();
		try {
			VistaPreviaRecaudacion previa = servicio.previsualizar(archivo.getOriginalFilename(), contenido,
					archivo.getSize());
			sesion.setAttribute(CLAVE_SESION, previa);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:" + RUTA;
		}
		return "redirect:" + RUTA + "/vista-previa";
	}

	@GetMapping("/vista-previa")
	public String revision(HttpSession sesion, Model model) {
		if (!(sesion.getAttribute(CLAVE_SESION) instanceof VistaPreviaRecaudacion previa)) {
			return "redirect:" + RUTA;
		}
		model.addAttribute("previa", previa);
		return "recaudacion/revisar";
	}

	@PostMapping("/lotes")
	public String registrar(@RequestParam(required = false) String token, HttpSession sesion,
			RedirectAttributes avisos) {
		VistaPreviaRecaudacion previa = sesion.getAttribute(CLAVE_SESION) instanceof VistaPreviaRecaudacion v ? v : null;
		try {
			Long lote = servicio.registrar(previa, uuid(token));
			sesion.removeAttribute(CLAVE_SESION);
			avisos.addFlashAttribute("exito", "Listo: registraste el archivo. Ahora otra persona de Promotoría o Dirección "
					+ "debe confirmarlo escribiendo a ciegas el total que ve en el banco; recién entonces se aplican los "
					+ "pagos.");
			return "redirect:" + RUTA + "/lotes/" + lote;
		}
		catch (ReglaNegocioException e) {
			sesion.removeAttribute(CLAVE_SESION);
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:" + RUTA;
		}
	}

	@PostMapping("/cancelar")
	public String cancelar(HttpSession sesion, RedirectAttributes avisos) {
		sesion.removeAttribute(CLAVE_SESION);
		avisos.addFlashAttribute("exito", "Listo: cancelaste la carga. No se guardó nada.");
		return "redirect:" + RUTA;
	}

	@GetMapping("/lotes/{id:\\d+}")
	public String lote(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
		model.addAttribute("lote", consulta.detalle(id));
		model.addAttribute("puedeConfirmar", usuario != null
				&& (usuario.roles().contains(Rol.PROMOTOR) || usuario.roles().contains(Rol.DIRECTOR)));
		return "recaudacion/lote";
	}

	@PostMapping("/lotes/{id:\\d+}/descartar")
	public String descartar(@PathVariable Long id, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		try {
			servicio.descartar(id, motivo);
			avisos.addFlashAttribute("exito", "Listo: descartaste el archivo. No se aplicó ningún pago.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + RUTA + "/lotes/" + id;
	}

	@GetMapping("/lotes/{id:\\d+}/confirmar")
	public String paraConfirmar(@PathVariable Long id, Model model) {
		model.addAttribute("c", servicio.paraConfirmar(id));
		return "recaudacion/confirmar";
	}

	@PostMapping("/lotes/{id:\\d+}/confirmar")
	public String confirmar(@PathVariable Long id, @RequestParam(required = false) Long version,
			@RequestParam(required = false) String totalVisto, RedirectAttributes avisos) {
		BigDecimal total;
		try {
			total = monto(totalVisto);
		}
		catch (NumberFormatException e) {
			avisos.addFlashAttribute("error", "Escribe el total con punto decimal, sin comas ni «S/» (por ejemplo 12500.00).");
			return "redirect:" + RUTA + "/lotes/" + id + "/confirmar";
		}
		try {
			servicio.confirmar(id, version, total);
			avisos.addFlashAttribute("exito", "Coincide: confirmaste el archivo. El sistema registró los pagos con su "
					+ "boleta; los que no pudo aplicar quedaron por revisar.");
			return "redirect:" + RUTA + "/lotes/" + id;
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:" + RUTA + "/lotes/" + id + "/confirmar";
		}
	}

	@GetMapping("/lotes/{id:\\d+}/archivo")
	public ResponseEntity<byte[]> archivo(@PathVariable Long id) {
		ConsultaRecaudacion.ArchivoOriginal archivo;
		try {
			archivo = consulta.archivo(id);
		}
		catch (ReglaNegocioException e) {
			// Solo con una petición armada a mano: la pantalla no muestra el botón mientras no se puede descargar.
			return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
					.contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
					.body(e.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre())
						.build().toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store").body(archivo.contenido());
	}

	@GetMapping("/base-deudas")
	public ResponseEntity<byte[]> baseDeudas() {
		ServicioRecaudacion.ArchivoExportado archivo = servicio.exportarBaseDeudas();
		return ResponseEntity.ok().contentType(CSV)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre())
						.build().toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store").body(archivo.contenido());
	}

	@GetMapping("/lineas/{id:\\d+}")
	public String linea(@PathVariable Long id, @RequestParam(required = false) String codigo, Model model) {
		model.addAttribute("l", excepciones.detalle(id, codigo));
		return "recaudacion/linea";
	}

	@PostMapping("/lineas/{id:\\d+}/aplicar")
	public String aplicar(@PathVariable Long id, @Valid AplicacionLineaRequest pedido, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:" + RUTA + "/lineas/" + id;
		}
		try {
			excepciones.solicitarAplicacion(id, pedido);
			avisos.addFlashAttribute("exito", "Listo: pediste aplicar el pago. Promotoría o Dirección lo aprueban; el pago "
					+ "lo registra el sistema al aprobarse.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + RUTA + "/lineas/" + id;
	}

	@PostMapping("/lineas/{id:\\d+}/devolver")
	public String devolver(@PathVariable Long id, @Valid DevolucionLineaRequest pedido, BindingResult validacion,
			RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:" + RUTA + "/lineas/" + id;
		}
		try {
			excepciones.solicitarDevolucion(id, pedido);
			avisos.addFlashAttribute("exito", "Listo: pediste devolver el pago. Cuando Promotoría o Dirección lo aprueben, "
					+ "otra persona de Administración (no tú) hace la transferencia a esa cuenta y registra su número.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + RUTA + "/lineas/" + id;
	}

	@PostMapping("/lineas/{id:\\d+}/devolucion")
	public String registrarDevolucion(@PathVariable Long id, @RequestParam(required = false) String numeroOperacion,
			RedirectAttributes avisos) {
		try {
			excepciones.registrarDevolucion(id, numeroOperacion);
			avisos.addFlashAttribute("exito", "Listo: registraste la devolución con su número de transferencia.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + RUTA + "/lineas/" + id;
	}

	/** «12500.00» → 12500.00. Solo dígitos y punto decimal (una coma no se adivina). */
	static BigDecimal monto(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		String limpio = texto.strip();
		if (!limpio.matches("\\d{1,10}(\\.\\d{1,2})?")) {
			throw new NumberFormatException(limpio);
		}
		return new BigDecimal(limpio);
	}

	private static UUID uuid(String texto) {
		try {
			return texto == null ? null : UUID.fromString(texto);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}
}
