package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.VerificacionContacto;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.VerificacionContactoRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnlacesActivacion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Correcciones del sprint 5 (S5-A1): página PÚBLICA del enlace de verificación de un contacto. El token (32 bytes al
 * azar; en la base solo su SHA-256) es lo que autentica y, como confirmación, se escribe el número de documento del
 * apoderado. Al usarlo, ese celular o ese correo queda verificado (si sigue siendo el registrado) y empieza a recibir los
 * avisos. Queda en la bitácora con la IP de quien lo usó.
 */
@Service
public class ServicioVerificacionContacto {

	/** Lo que ve el titular: su nombre y el contacto (enmascarado) que va a confirmar. */
	public record VistaVerificacion(String nombre, String contacto, LocalDateTime venceEn) {
	}

	private static final String NO_SIRVE = "Este enlace ya no sirve (se usó, venció o se reemplazó). Pide al colegio que "
			+ "te envíe otro.";

	private final VerificacionContactoRepository verificaciones;

	private final ApoderadoRepository apoderados;

	private final AuditoriaService auditoria;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public ServicioVerificacionContacto(VerificacionContactoRepository verificaciones, ApoderadoRepository apoderados,
			AuditoriaService auditoria, PlatformTransactionManager transacciones, Clock reloj) {
		this.verificaciones = verificaciones;
		this.apoderados = apoderados;
		this.auditoria = auditoria;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	public Optional<VistaVerificacion> vista(long colegioId, String token) {
		if (!tokenValido(token) || colegioId <= 0) {
			return Optional.empty();
		}
		return ContextoColegio.en(colegioId, () -> Optional.ofNullable(transaccion.execute(t -> {
			LocalDateTime ahora = ahora();
			return verificaciones.bloquearPorHash(EnlacesActivacion.hash(token)).filter(v -> v.vigente(ahora))
					.flatMap(v -> apoderados.findById(v.getApoderadoId()).filter(Apoderado::isActivo)
							.map(a -> new VistaVerificacion(a.getNombres(), enmascarar(v), v.getVenceEn())))
					.orElse(null);
		})));
	}

	public void verificar(long colegioId, String token, String documento) {
		if (!tokenValido(token) || colegioId <= 0) {
			throw new ReglaNegocioException(NO_SIRVE);
		}
		ContextoColegio.en(colegioId, () -> transaccion.executeWithoutResult(t -> {
			LocalDateTime ahora = ahora();
			VerificacionContacto verificacion = verificaciones.bloquearPorHash(EnlacesActivacion.hash(token))
					.filter(v -> v.vigente(ahora)).orElseThrow(() -> new ReglaNegocioException(NO_SIRVE));
			Apoderado apoderado = apoderados.findById(verificacion.getApoderadoId()).filter(Apoderado::isActivo)
					.orElseThrow(() -> new ReglaNegocioException(NO_SIRVE));
			String escrito = documento == null ? "" : documento.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
			if (!escrito.equals(apoderado.getDocumento().numero().toUpperCase(Locale.ROOT))) {
				throw new ReglaNegocioException("El número de documento no corresponde a este enlace.");
			}
			String ip = ipActual();
			verificacion.usar(ahora, ip);
			verificaciones.saveAndFlush(verificacion);
			if (!apoderado.verificarContacto(verificacion.porWhatsapp(), verificacion.getContacto())) {
				throw new ReglaNegocioException("Ese contacto ya no es el registrado en el colegio: este enlace no sirve.");
			}
			apoderados.saveAndFlush(apoderado);
			auditoria.registrar(auditoria.actorPara(colegioId, null, "apoderado:" + apoderado.getId(), "APODERADO"),
					AccionAuditoria.CONTACTO_VERIFICADO, "apoderado", apoderado.getId().toString(), "pendiente",
					"verificado", "El titular de " + enmascarar(verificacion) + " (apoderado " + apoderado.nombreCompleto()
							+ ") lo verificó con su enlace desde la IP " + verificacion.getVerificadoIp()
							+ ". Desde ahora recibe los avisos del colegio.");
		}));
	}

	private static String enmascarar(VerificacionContacto v) {
		return v.porWhatsapp() ? "WhatsApp " + Enmascarar.telefono(v.getContacto())
				: "correo " + Enmascarar.correo(v.getContacto());
	}

	private static boolean tokenValido(String token) {
		return token != null && token.matches("[A-Za-z0-9_-]{43}");
	}

	private static String ipActual() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
			return atributos.getRequest().getRemoteAddr();
		}
		return null;
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
