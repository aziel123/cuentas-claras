package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CambioCorreoControl;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.CambioCorreoControlRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.ConfiguracionColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Correcciones del sprint 8 (S8-A2): la Promotoría aprueba o rechaza, con la firma de su sesión, el cambio del correo
 * externo de control que propuso el operador de la plataforma para su colegio en producción. El correo de control es el
 * testigo independiente del colegio (recibe la huella de la bitácora y el resumen diario con montos): el operador ya no lo
 * cambia solo. Al aprobar, en la misma transacción: aviso al correo ANTERIOR, al institucional y a toda la Promotoría
 * (antes del cambio), el cambio de las dos filas y el aviso al correo NUEVO.
 * <p>
 * {@code configuracion_colegio} y {@code cambio_correo_control} solo las escribe {@code cc_sistema}: la aprobación va por
 * la ruta de identidad con el contexto de la persona (la bitácora dice quién aprobó). En MySQL,
 * trg_cambio_correo_control_estado y trg_configuracion_colegio_cambio exigen que sea una Promotoría activa de ese colegio,
 * con su firma ({@code cambio_correo_control:{id}:APROBADO}, 5 minutos).
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class CorreoDeControl {

	/** Lo que ve el colegio: el correo de control actual y, si hay, el cambio que propuso el operador. */
	public record VistaCorreoControl(String actual, Optional<Propuesta> propuesta) {
	}

	/** El cambio propuesto (el colegio ve los correos completos: es su testigo). */
	public record Propuesta(Long id, String anterior, String nuevo, String motivo, String propuestoPor,
			LocalDateTime propuestoEn) {
	}

	private final CambioCorreoControlRepository cambios;

	private final ConfiguracionColegioRepository configuracion;

	private final ColegioService colegios;

	private final FirmaSesion firma;

	private final EjecucionIdentidad identidad;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public CorreoDeControl(CambioCorreoControlRepository cambios, ConfiguracionColegioRepository configuracion,
			ColegioService colegios, FirmaSesion firma, EjecucionIdentidad identidad, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, PlatformTransactionManager transacciones, Clock reloj) {
		this.cambios = cambios;
		this.configuracion = configuracion;
		this.colegios = colegios;
		this.firma = firma;
		this.identidad = identidad;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	/** El correo de control del colegio de la sesión y el cambio por aprobar, si hay. */
	public VistaCorreoControl vista() {
		Long colegioId = ContextoColegio.actual();
		return transaccion.execute(t -> new VistaCorreoControl(configuracion.findByColegioIdAndClave(colegioId,
				ConfiguracionColegio.HUELLA_CORREO_EXTERNO).map(ConfiguracionColegio::getValor).orElse(null),
				cambios.findFirstByEstado(CambioCorreoControl.Estado.PROPUESTO).map(c -> new Propuesta(c.getId(),
						c.getCorreoAnterior(), c.getCorreoNuevo(), c.getMotivo(), c.getPropuestoPor(), c.getCreadoEn()))));
	}

	/** La Promotoría aprueba el cambio con su firma: desde ahora el correo nuevo recibe la huella y el resumen. */
	@PreAuthorize("hasRole('PROMOTOR')")
	public void aprobar(Long id) {
		String quien = persona();
		Long colegioId = ContextoColegio.actual();
		if (!colegios.enProduccion(colegioId)) {
			throw new ReglaNegocioException("En preparación el correo de control lo registra el operador de la plataforma.");
		}
		exigirPropuesto(id);
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioCorreoControl(id,
				CambioCorreoControl.Estado.APROBADO)));
		identidad.ejecutar(() -> {
			CambioCorreoControl cambio = cambios.bloquear(id)
					.orElseThrow(() -> new ReglaNegocioException("Ese cambio del correo de control no existe."));
			LocalDateTime ahora = LocalDateTime.now(reloj);
			cambio.aprobar(quien, ahora);
			List<String> parametros = List.of(Enmascarar.correo(cambio.getCorreoNuevo()), quien + " (Promotoría)");
			// Antes del cambio: al correo anterior (el registrado ahora), al institucional y a toda la Promotoría.
			eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CORREO_CONTROL_CAMBIADO, parametros,
					"cambio_correo_control", cambio.getId(), "A", null, true, true, true));
			// La propuesta queda APROBADA (con su firma) antes de las filas: trg_configuracion_colegio_* la exige.
			cambios.saveAndFlush(cambio);
			for (String clave : List.of(ConfiguracionColegio.HUELLA_CORREO_EXTERNO,
					ConfiguracionColegio.RESUMEN_CORREO_EXTERNO)) {
				ConfiguracionColegio fila = configuracion.findByColegioIdAndClave(colegioId, clave).orElse(null);
				if (fila == null) {
					configuracion.saveAndFlush(ConfiguracionColegio.nueva(colegioId, clave, cambio.getCorreoNuevo(), quien,
							ahora));
				}
				else {
					fila.cambiar(cambio.getCorreoNuevo(), quien, ahora);
					configuracion.saveAndFlush(fila);
				}
			}
			// Y al correo nuevo (ya registrado).
			eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CORREO_CONTROL_CAMBIADO, parametros,
					"cambio_correo_control", cambio.getId(), "N", null, false, true, false));
			auditoria.registrar(AccionAuditoria.CORREO_CONTROL_APROBADO, "cambio_correo_control", id.toString(),
					cambio.getCorreoAnterior() == null ? null : Enmascarar.correo(cambio.getCorreoAnterior()),
					Enmascarar.correo(cambio.getCorreoNuevo()), quien + " aprobó con su firma el correo externo de control "
							+ "que propuso el operador " + cambio.getPropuestoPor() + " (motivo: " + cambio.getMotivo()
							+ "). Se avisó al correo anterior, al nuevo, al institucional y a toda la Promotoría.");
		});
	}

	/** La Promotoría rechaza el cambio con su firma: sigue el correo de control anterior. */
	@PreAuthorize("hasRole('PROMOTOR')")
	public void rechazar(Long id, String motivo) {
		String quien = persona();
		String texto = motivo == null ? "" : motivo.strip();
		if (texto.length() < 10 || texto.length() > 500) {
			throw new ReglaNegocioException("Escribe el motivo del rechazo (de 10 a 500 caracteres).");
		}
		exigirPropuesto(id);
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioCorreoControl(id,
				CambioCorreoControl.Estado.RECHAZADO)));
		identidad.ejecutar(() -> {
			CambioCorreoControl cambio = cambios.bloquear(id)
					.orElseThrow(() -> new ReglaNegocioException("Ese cambio del correo de control no existe."));
			cambio.rechazar(quien, LocalDateTime.now(reloj));
			cambios.saveAndFlush(cambio);
			auditoria.registrar(AccionAuditoria.CORREO_CONTROL_RECHAZADO, "cambio_correo_control", id.toString(),
					"PROPUESTO", "RECHAZADO", quien + " rechazó con su firma el correo externo de control que propuso el "
							+ "operador " + cambio.getPropuestoPor() + ". Motivo: " + texto + ". Sigue el correo anterior.");
		});
	}

	/** Antes de firmar (la firma es única por clave): el cambio existe en este colegio y sigue por resolver. */
	private void exigirPropuesto(Long id) {
		CambioCorreoControl.Estado estado = transaccion.execute(t -> cambios.findById(id)
				.orElseThrow(() -> new ReglaNegocioException("Ese cambio del correo de control no existe.")).getEstado());
		if (estado != CambioCorreoControl.Estado.PROPUESTO) {
			throw new ReglaNegocioException("Este cambio del correo de control ya no está por resolver.");
		}
	}

	private static String persona() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario.getUsername();
		}
		throw new AccessDeniedException("Se requiere una persona en sesión");
	}
}
