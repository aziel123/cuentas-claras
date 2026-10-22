package pe.edu.virgenmaria.cuentasclaras.academico.web;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.ArchivoNomina;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.CodigoModularRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.NominaSiagie;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.AccesoMostrado;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.RegistraAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;
import pe.edu.virgenmaria.cuentasclaras.comun.web.Formularios;

/**
 * Sprint 9, tanda 3 (sección 3.11, E34): los datos para el SIAGIE. Solo Dirección (ModuloApp y {@code @PreAuthorize} del
 * servicio). Sin lógica: delega en {@link NominaSiagie}.
 */
@Controller
public class SiagieController {

	private final NominaSiagie nomina;

	public SiagieController(NominaSiagie nomina) {
		this.nomina = nomina;
	}

	@GetMapping("/academico/siagie")
	@RegistraAcceso(TipoAcceso.NOMINA_SIAGIE)
	public String inicio(Model model) {
		var faltantes = nomina.faltantes();
		AccesoMostrado.filas(faltantes.secciones().stream().mapToInt(s -> s.faltantes()).sum());
		model.addAttribute("faltantes", faltantes);
		model.addAttribute("codigos", nomina.codigosModulares());
		model.addAttribute("niveles", Nivel.values());
		return "academico/siagie";
	}

	@PostMapping("/academico/siagie/codigos-modulares")
	public String codigoModular(@Valid CodigoModularRequest pedido, BindingResult validacion, RedirectAttributes avisos) {
		if (validacion.hasErrors()) {
			avisos.addFlashAttribute("error", Formularios.primerError(validacion));
		}
		else {
			try {
				nomina.registrarCodigoModular(pedido);
				avisos.addFlashAttribute("exito", "Listo: el código modular quedó registrado.");
			}
			catch (ReglaNegocioException e) {
				avisos.addFlashAttribute("error", e.getMessage());
			}
		}
		return "redirect:/academico/siagie";
	}

	@GetMapping("/academico/siagie/nomina")
	@RegistraAcceso(TipoAcceso.NOMINA_SIAGIE)
	public ResponseEntity<byte[]> descargar(@RequestParam("seccion") long seccion) {
		ArchivoNomina archivo = nomina.exportar(seccion);
		AccesoMostrado.filas(archivo.filas());
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(archivo.nombreArchivo()).build().toString())
				.header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore().cachePrivate())
				.body(archivo.contenido());
	}
}
