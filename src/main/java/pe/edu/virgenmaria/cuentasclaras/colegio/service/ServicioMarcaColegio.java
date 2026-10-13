package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.MarcaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.MarcaColegioVista;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.LogoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sprint 8, tanda 1 (sección 3.7, decisión 123): la marca del colegio en contexto, solo para Promotoría. Corrige la ficha
 * (nombre, razón social, dirección y correo institucional; el RUC NO se cambia aquí) y reemplaza el logo. Cada cambio
 * queda en la bitácora con su valor anterior y el nuevo ({@code MARCA_COLEGIO_CAMBIADA}) y el logo con su SHA-256
 * anterior y nuevo ({@code LOGO_COLEGIO_CAMBIADO}).
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
public class ServicioMarcaColegio {

	private final ColegioRepository colegios;

	private final LogosColegio logos;

	private final AuditoriaService auditoria;

	private final pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion firma;

	public ServicioMarcaColegio(ColegioRepository colegios, LogosColegio logos, AuditoriaService auditoria,
			pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion firma) {
		this.colegios = colegios;
		this.logos = logos;
		this.auditoria = auditoria;
		this.firma = firma;
	}

	/** La ficha y el logo vigente del colegio de la sesión. */
	@Transactional(readOnly = true)
	public MarcaColegioVista ver() {
		Colegio colegio = colegioActual();
		LogoColegio logo = logos.vigente().orElse(null);
		return new MarcaColegioVista(colegio.getNombre(), colegio.getRazonSocial(), colegio.getRuc(),
				colegio.getDireccion(), colegio.getCorreoInstitucional(), logo != null,
				logo == null ? null : logo.getAncho(), logo == null ? null : logo.getAlto(),
				logo == null ? null : logo.getBytes());
	}

	/**
	 * Corrige la ficha y, si llega un archivo, reemplaza el logo. Las dos cosas en una transacción: si el logo no sirve, la
	 * ficha tampoco cambia.
	 *
	 * @param datos la ficha completa (sin el RUC)
	 * @param logo los bytes del archivo subido, o {@code null} si no se cambia el logo
	 * @param nombreArchivo el nombre del archivo, solo para los mensajes
	 * @throws ReglaNegocioException si no hay nada que guardar o el logo no es válido
	 */
	@Transactional
	public void cambiar(MarcaColegioRequest datos, byte[] logo, String nombreArchivo) {
		Objects.requireNonNull(datos, "datos");
		Colegio colegio = colegioActual();
		String id = colegio.getId().toString();
		List<String> antes = new ArrayList<>();
		List<String> despues = new ArrayList<>();
		String nombreAntes = colegio.getNombre();
		String razonAntes = colegio.getRazonSocial();
		String direccionAntes = colegio.getDireccion();
		String correoAntes = colegio.getCorreoInstitucional();
		if (!Objects.equals(nombreAntes, limpio(datos.nombre())) || !Objects.equals(razonAntes, limpio(datos.razonSocial()))
				|| !Objects.equals(direccionAntes, limpio(datos.direccion()))
				|| !Objects.equals(correoAntes, limpio(datos.correoInstitucional()))) {
			// S8-M5: la Promotoría firma el cambio de la ficha (trg_colegio_cambio exige su firma de la versión anterior).
			// ANTES de tocar la entidad: el INSERT de la firma hace flush y el UPDATE debe salir ya con quien firma.
			firma.firmar(pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma.fichaColegio(colegio.getId(),
					colegio.getVersion()));
			colegio.firmarCambioDeFicha(org.springframework.security.core.context.SecurityContextHolder.getContext()
					.getAuthentication().getName());
		}
		colegio.corregirFicha(datos.nombre(), datos.razonSocial(), datos.direccion(), datos.correoInstitucional());
		anotar("nombre", nombreAntes, colegio.getNombre(), antes, despues);
		anotar("razonSocial", razonAntes, colegio.getRazonSocial(), antes, despues);
		anotar("direccion", direccionAntes, colegio.getDireccion(), antes, despues);
		anotar("correoInstitucional", correoAntes, colegio.getCorreoInstitucional(), antes, despues);
		boolean conLogo = logo != null && logo.length > 0;
		if (antes.isEmpty() && !conLogo) {
			throw new ReglaNegocioException("No hay cambios que guardar: la ficha es la misma y no elegiste un logo.");
		}
		if (!antes.isEmpty()) {
			colegios.saveAndFlush(colegio);
			auditoria.registrar(AccionAuditoria.MARCA_COLEGIO_CAMBIADA, "colegio", id, String.join(" · ", antes),
					String.join(" · ", despues), "Promotoría corrigió la ficha del colegio (" + antes.size()
							+ (antes.size() == 1 ? " campo). " : " campos). ") + "Se ve en el portal de las familias y en las boletas.");
		}
		if (conLogo) {
			String shaAnterior = logos.vigente().map(LogoColegio::getSha256).orElse(null);
			LogoColegio nuevo = logos.guardar(logo, nombreArchivo);
			auditoria.registrar(AccionAuditoria.LOGO_COLEGIO_CAMBIADO, "colegio_logo", nuevo.getId().toString(),
					shaAnterior, nuevo.getSha256(), "Logo reescrito como PNG de " + nuevo.getAncho() + " × "
							+ nuevo.getAlto() + " px y " + nuevo.getBytes() + " bytes"
							+ (shaAnterior == null ? " (el primero del colegio)." : " (reemplaza al anterior)."));
		}
	}

	private Colegio colegioActual() {
		Long colegioId = ContextoColegio.actual();
		if (colegioId == null || colegioId <= 0) {
			throw new RecursoNoEncontradoException("Colegio no encontrado");
		}
		return colegios.findById(colegioId).orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
	}

	private static String limpio(String texto) {
		return texto == null ? null : texto.strip();
	}

	private static void anotar(String campo, String anterior, String nuevo, List<String> antes, List<String> despues) {
		if (Objects.equals(anterior, nuevo)) {
			return;
		}
		antes.add(campo + "=" + (anterior == null ? "(vacío)" : anterior));
		despues.add(campo + "=" + nuevo);
	}
}
