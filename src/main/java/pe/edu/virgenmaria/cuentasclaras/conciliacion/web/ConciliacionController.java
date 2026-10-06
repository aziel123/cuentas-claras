package pe.edu.virgenmaria.cuentasclaras.conciliacion.web;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
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
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.CuentaRequest;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.dto.VistaPreviaExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.inicial.EjemploExtractoDev;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.BancoCuenta;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CategoriaExplicacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ResumenConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioPartidas;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Conciliación automática con el extracto (sprint 4, tanda 3): la pantalla de diferencias (la principal), la carga del
 * extracto en 3 pasos (subir → revisar → registrar), su confirmación a ciegas por otra persona, la revisión de las
 * diferencias y las cuentas del colegio. Sin lógica: delega en los servicios, que exigen el rol de cada acción. La
 * revisión vive SOLO en la sesión de quien subió el archivo (clave {@value #CLAVE_SESION}).
 */
@Controller
@RequestMapping("/conciliacion")
public class ConciliacionController {

	public static final String CLAVE_SESION = "extractoPendiente";

	private static final String RUTA = "/conciliacion";

	private static final String EXTRACTOS = RUTA + "/extractos";

	private final ResumenConciliacion resumen;

	private final ServicioExtractos extractos;

	private final ServicioPartidas partidas;

	private final ServicioCuentasBancarias cuentas;

	/** Solo en desarrollo: el extracto de ejemplo para probar el flujo (no hay banca por internet). */
	private final ObjectProvider<EjemploExtractoDev> ejemplo;

	public ConciliacionController(ResumenConciliacion resumen, ServicioExtractos extractos, ServicioPartidas partidas,
			ServicioCuentasBancarias cuentas, ObjectProvider<EjemploExtractoDev> ejemplo) {
		this.resumen = resumen;
		this.extractos = extractos;
		this.partidas = partidas;
		this.cuentas = cuentas;
		this.ejemplo = ejemplo;
	}

	@GetMapping
	public String diferencias(Model model) {
		model.addAttribute("d", resumen.diferencias());
		return "conciliacion/diferencias";
	}

	// --- Extractos: subir, revisar, registrar, descartar ---

	@GetMapping("/extractos")
	public String extractos(Model model) {
		model.addAttribute("x", resumen.extractos());
		EjemploExtractoDev demo = ejemplo.getIfAvailable();
		model.addAttribute("ejemploDev", demo != null && demo.disponible());
		return "conciliacion/extractos";
	}

	@PostMapping("/extractos/vista-previa")
	public String subir(@RequestParam(name = "archivo", required = false) MultipartFile archivo, HttpSession sesion,
			RedirectAttributes avisos) throws IOException {
		sesion.removeAttribute(CLAVE_SESION);
		if (archivo == null || archivo.isEmpty()) {
			avisos.addFlashAttribute("error", "Elige el extracto del banco que quieres subir.");
			return "redirect:" + EXTRACTOS;
		}
		byte[] contenido = archivo.getSize() > ArchivoCargado.MAXIMO_BYTES
				? archivo.getInputStream().readNBytes(ArchivoCargado.MAXIMO_BYTES + 1) : archivo.getBytes();
		try {
			VistaPreviaExtracto previa = extractos.previsualizar(archivo.getOriginalFilename(), contenido,
					archivo.getSize());
			sesion.setAttribute(CLAVE_SESION, previa);
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:" + EXTRACTOS;
		}
		return "redirect:" + EXTRACTOS + "/vista-previa";
	}

	@GetMapping("/extractos/vista-previa")
	public String revision(HttpSession sesion, Model model) {
		if (!(sesion.getAttribute(CLAVE_SESION) instanceof VistaPreviaExtracto previa)) {
			return "redirect:" + EXTRACTOS;
		}
		model.addAttribute("previa", previa);
		return "conciliacion/revisar";
	}

	@PostMapping("/extractos")
	public String registrar(@RequestParam(required = false) String token, HttpSession sesion, RedirectAttributes avisos) {
		VistaPreviaExtracto previa = sesion.getAttribute(CLAVE_SESION) instanceof VistaPreviaExtracto v ? v : null;
		try {
			Long extracto = extractos.registrar(previa, uuid(token));
			sesion.removeAttribute(CLAVE_SESION);
			avisos.addFlashAttribute("exito", "Listo: registraste el extracto. Ahora otra persona de Promotoría o Dirección "
					+ "debe confirmarlo escribiendo a ciegas el saldo que ve en su app del banco; recién entonces se "
					+ "concilia.");
			return "redirect:" + EXTRACTOS + "/" + extracto;
		}
		catch (ReglaNegocioException e) {
			sesion.removeAttribute(CLAVE_SESION);
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:" + EXTRACTOS;
		}
	}

	@PostMapping("/extractos/cancelar")
	public String cancelar(HttpSession sesion, RedirectAttributes avisos) {
		sesion.removeAttribute(CLAVE_SESION);
		avisos.addFlashAttribute("exito", "Listo: cancelaste la carga. No se guardó nada.");
		return "redirect:" + EXTRACTOS;
	}

	@GetMapping("/extractos/{id:\\d+}")
	public String extracto(@PathVariable Long id, Model model) {
		model.addAttribute("e", resumen.extracto(id));
		return "conciliacion/extracto";
	}

	@PostMapping("/extractos/{id:\\d+}/descartar")
	public String descartar(@PathVariable Long id, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		try {
			extractos.descartar(id, motivo);
			avisos.addFlashAttribute("exito", "Listo: descartaste el extracto. No se concilió nada con él.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + EXTRACTOS + "/" + id;
	}

	// --- Confirmación a ciegas (Promotoría o Dirección) ---

	@GetMapping("/cuentas/{id:\\d+}/confirmar")
	public String paraConfirmar(@PathVariable Long id, Model model) {
		model.addAttribute("c", extractos.paraConfirmar(id));
		return "conciliacion/confirmar";
	}

	@PostMapping("/cuentas/{id:\\d+}/confirmar")
	public String confirmar(@PathVariable Long id, @RequestParam(required = false) Long extracto,
			@RequestParam(required = false) Long version, @RequestParam(required = false) String saldoVisto,
			RedirectAttributes avisos) {
		BigDecimal saldo;
		try {
			saldo = monto(saldoVisto);
		}
		catch (NumberFormatException e) {
			avisos.addFlashAttribute("error", "Escribe el saldo con punto decimal, sin comas ni «S/» (por ejemplo "
					+ "15230.50).");
			return "redirect:" + RUTA + "/cuentas/" + id + "/confirmar";
		}
		try {
			extractos.confirmar(id, extracto, version, saldo);
			avisos.addFlashAttribute("exito", "Coincide: confirmaste el extracto. El sistema concilió lo que cuadra solo; "
					+ "aquí ves solo las diferencias.");
			return "redirect:" + RUTA;
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
			return "redirect:" + RUTA + "/cuentas/" + id + "/confirmar";
		}
	}

	// --- Revisión de las diferencias (Administración) ---

	@PostMapping("/partidas/{id:\\d+}/confirmar")
	public String confirmarSugerida(@PathVariable Long id, RedirectAttributes avisos) {
		try {
			partidas.confirmarSugerida(id);
			avisos.addFlashAttribute("exito", "Listo: confirmaste la pareja. Quedó verificado en el banco.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + RUTA;
	}

	@PostMapping("/partidas/{id:\\d+}/descartar")
	public String descartarPartida(@PathVariable Long id, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		try {
			partidas.descartar(id, motivo);
			avisos.addFlashAttribute("exito", "Listo: marcaste que no es. El movimiento y lo registrado quedan sin pareja.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + RUTA;
	}

	@PostMapping("/movimientos/{id:\\d+}/emparejar")
	public String emparejar(@PathVariable Long id, @RequestParam(required = false) String objeto,
			@RequestParam(required = false) String nota, RedirectAttributes avisos) {
		try {
			ObjetoPartida tipo = null;
			Long objetoId = null;
			if (objeto != null && objeto.matches("[A-Z_]{4,20}:\\d{1,18}")) {
				tipo = ObjetoPartida.valueOf(objeto.substring(0, objeto.indexOf(':')));
				objetoId = Long.valueOf(objeto.substring(objeto.indexOf(':') + 1));
			}
			partidas.emparejarManual(id, tipo, objetoId, nota);
			avisos.addFlashAttribute("exito", "Listo: emparejaste el movimiento. Quedó resaltado para Promotoría.");
		}
		catch (ReglaNegocioException | IllegalArgumentException e) {
			avisos.addFlashAttribute("error", e instanceof ReglaNegocioException ? e.getMessage()
					: "Elige con qué se empareja el movimiento.");
		}
		return "redirect:" + RUTA;
	}

	@PostMapping("/movimientos/{id:\\d+}/explicar")
	public String explicar(@PathVariable Long id, @RequestParam(required = false) String categoria,
			@RequestParam(required = false) String nota, RedirectAttributes avisos) {
		try {
			partidas.explicar(id, categoria(categoria), nota);
			avisos.addFlashAttribute("exito", "Listo: explicaste el movimiento. Quedó resaltado para Promotoría.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + RUTA;
	}

	// --- Cuentas del colegio (Promotoría) ---

	@GetMapping("/cuentas")
	public String cuentas(Model model) {
		model.addAttribute("x", resumen.extractos());
		model.addAttribute("bancos", BancoCuenta.values());
		return "conciliacion/cuentas";
	}

	@PostMapping("/cuentas")
	public String registrarCuenta(@Valid CuentaRequest pedido, BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
			return "redirect:" + RUTA + "/cuentas";
		}
		try {
			cuentas.registrar(pedido);
			avisos.addFlashAttribute("exito", "Listo: registraste la cuenta. Administración ya puede subir su extracto.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + RUTA + "/cuentas";
	}

	@PostMapping("/cuentas/{id:\\d+}/desactivar")
	public String desactivarCuenta(@PathVariable Long id, @RequestParam(required = false) String motivo,
			RedirectAttributes avisos) {
		try {
			cuentas.desactivar(id, motivo);
			avisos.addFlashAttribute("exito", "Listo: desactivaste la cuenta.");
		}
		catch (ReglaNegocioException e) {
			avisos.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + RUTA + "/cuentas";
	}

	/** «15230.50» o «-120.00» → monto. Solo dígitos, signo y punto decimal (una coma no se adivina). */
	static BigDecimal monto(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		String limpio = texto.strip();
		if (!limpio.matches("-?\\d{1,12}(\\.\\d{1,2})?")) {
			throw new NumberFormatException(limpio);
		}
		return new BigDecimal(limpio);
	}

	private static CategoriaExplicacion categoria(String texto) {
		try {
			return texto == null || texto.isBlank() ? null : CategoriaExplicacion.valueOf(texto);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
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
