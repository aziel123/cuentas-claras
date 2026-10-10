package pe.edu.virgenmaria.cuentasclaras.operacion.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.ComparacionRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.ResolucionRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.Respaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.repository.ResolucionRespaldoRepository;
import pe.edu.virgenmaria.cuentasclaras.operacion.repository.RespaldoRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Correcciones del sprint 7 (QA-S7-1): la alerta «Faltan filas» de los respaldos ya no la borra un segundo respaldo; la
 * resuelve una PERSONA de Promotoría con motivo, después de revisar con el responsable técnico qué pasó
 * (incidente-auditoria.md). Reglas:
 * <ul>
 *   <li>solo Promotoría ({@code @PreAuthorize} y, en MySQL, {@code trg_resolucion_respaldo_registro});</li>
 *   <li>nunca el operador de los respaldos: ni {@code cc_respaldo} (la base), ni la cuenta cuyo correo es el del operador
 *       de las alertas técnicas ({@code CC_OPERADOR_CORREO}): quien opera los respaldos no cierra sus propias alertas;</li>
 *   <li>con motivo (10 a 500 caracteres) y la firma de su sesión ({@code respaldo:{id}:RESUELTO});</li>
 *   <li>sobre el ÚLTIMO respaldo con FALTAN_FILAS: cierra esa alerta y las anteriores;</li>
 *   <li>queda en la bitácora (resaltado) con las diferencias y el motivo, en la misma transacción.</li>
 * </ul>
 * La alerta es de toda la base (como el respaldo): la resuelve Promotoría de cualquier colegio de la instalación.
 */
@Service
public class ResolucionesRespaldo {

	private final RespaldoRepository respaldos;

	private final ResolucionRespaldoRepository resoluciones;

	private final UsuarioRepository usuarios;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final PropiedadesMonitoreo propiedades;

	private final Clock reloj;

	public ResolucionesRespaldo(RespaldoRepository respaldos, ResolucionRespaldoRepository resoluciones,
			UsuarioRepository usuarios, FirmaSesion firma, AuditoriaService auditoria, PropiedadesMonitoreo propiedades,
			Clock reloj) {
		this.respaldos = respaldos;
		this.resoluciones = resoluciones;
		this.usuarios = usuarios;
		this.firma = firma;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	/** Resuelve la alerta del último respaldo con FALTAN_FILAS. @return el archivo del respaldo resuelto */
	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional
	public String resolver(String motivo) {
		UsuarioAutenticado persona = persona();
		String texto = Motivo.exigir(motivo);
		Respaldo respaldo = respaldos.findFirstByComparacionOrderByIdDesc(ComparacionRespaldo.FALTAN_FILAS)
				.orElseThrow(() -> new ReglaNegocioException("No hay ninguna alerta de filas faltantes que resolver."));
		if (resoluciones.findFirstByOrderByRespaldoIdDesc().filter(r -> r.getRespaldoId() >= respaldo.getId())
				.isPresent()) {
			throw new ReglaNegocioException("La alerta del respaldo " + respaldo.getArchivo() + " ya se resolvió.");
		}
		Usuario usuario = usuarios.findById(persona.usuarioId())
				.orElseThrow(() -> new AccessDeniedException("Se requiere una cuenta del personal"));
		String operador = propiedades.operadorCorreo() == null ? "" : propiedades.operadorCorreo().strip();
		if (!operador.isEmpty() && usuario.getCorreo() != null
				&& operador.toLowerCase(Locale.ROOT).equals(usuario.getCorreo().strip().toLowerCase(Locale.ROOT))) {
			throw new ReglaNegocioException("Tu cuenta recibe las alertas técnicas de los respaldos (eres su operador): la "
					+ "resuelve otra persona de Promotoría.");
		}
		firma.firmar(ClaveFirma.respaldoResuelto(respaldo.getId()));
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		resoluciones.saveAndFlush(ResolucionRespaldo.de(respaldo, persona.colegioId(), persona.usuarioId(),
				persona.getUsername(), texto, ahora));
		auditoria.registrar(AccionAuditoria.RESPALDO_FALTAN_FILAS_RESUELTO, "respaldo", respaldo.getId().toString(),
				ComparacionRespaldo.FALTAN_FILAS.name(), "RESUELTO", "Respaldo " + respaldo.getArchivo() + " (del "
						+ respaldo.getFin().toLocalDate() + "): " + respaldo.getDiferencias() + ". Resuelta por "
						+ persona.getUsername() + ". Motivo: " + texto);
		return respaldo.getArchivo();
	}

	private static UsuarioAutenticado persona() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere un usuario en sesión");
	}
}
