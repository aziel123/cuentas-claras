package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.model.DelegacionSiagie;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.repository.DelegacionSiagieRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 10, tanda 3 (M12; decisión 189 ajustada por el coordinador): Dirección delega con su firma la exportación de
 * calificaciones para el SIAGIE a UNA persona de Administración por hasta {@value DelegacionSiagie#MAX_DIAS} días. NUNCA a
 * Caja ni a un docente, ni a una cuenta que combine Administración con Caja o Docente: quien cobra no recibe la nómina de
 * calificaciones de los menores (separación de funciones y Ley 29733, acceso mínimo por rol). En MySQL lo exige también
 * trg_delegacion_siagie_nace. Vence sola (desactivar la cuenta la corta).
 */
@Service
@PreAuthorize("hasRole('DIRECTOR')")
public class DelegacionesSiagie {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/** Una delegación vigente o por venir, para la pantalla. */
	public record DelegacionVista(String persona, String desde, String hasta, String autorizadoPor, boolean vigente) {
	}

	/** Una cuenta que puede recibir la delegación. */
	public record Candidata(Long id, String nombre) {
	}

	private final DelegacionSiagieRepository delegaciones;

	private final UsuarioRepository usuarios;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public DelegacionesSiagie(DelegacionSiagieRepository delegaciones, UsuarioRepository usuarios, FirmaSesion firma,
			AuditoriaService auditoria, Clock reloj) {
		this.delegaciones = delegaciones;
		this.usuarios = usuarios;
		this.firma = firma;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** M12 y decisión 189: activa, del personal, con ADMINISTRACION y sin CAJA ni DOCENTE. */
	public static boolean puedeRecibir(Usuario u) {
		return u.isActivo() && u.getApoderadoId() == null && u.getRoles().contains(Rol.ADMINISTRACION)
				&& !u.getRoles().contains(Rol.CAJA) && !u.getRoles().contains(Rol.DOCENTE);
	}

	@Transactional(readOnly = true)
	public List<Candidata> candidatas() {
		return usuarios.activosConRol(Rol.ADMINISTRACION).stream().filter(DelegacionesSiagie::puedeRecibir)
				.map(u -> new Candidata(u.getId(), u.getNombreCompleto())).toList();
	}

	@Transactional(readOnly = true)
	public List<DelegacionVista> vigentes() {
		LocalDate hoy = LocalDate.now(reloj);
		List<DelegacionSiagie> lista = delegaciones.findByHastaGreaterThanEqualOrderByIdDesc(hoy);
		Map<Long, Usuario> porId = lista.isEmpty() ? Map.of()
				: usuarios.findAllById(lista.stream().map(DelegacionSiagie::getUsuarioId).distinct().toList()).stream()
						.collect(Collectors.toMap(Usuario::getId, Function.identity()));
		return lista.stream().map(d -> new DelegacionVista(porId.containsKey(d.getUsuarioId())
				? porId.get(d.getUsuarioId()).getNombreCompleto() : "—", d.getDesde().format(FECHA),
				d.getHasta().format(FECHA), d.getAutorizadoPor(), d.vigenteEl(hoy))).toList();
	}

	/** Delega desde {@code desde} (hoy o después) hasta {@code hasta} (15 días como máximo), con la firma de Dirección. */
	@Transactional
	public void delegar(Long usuarioId, LocalDate desde, LocalDate hasta) {
		UsuarioAutenticado director = DelegadaSiagie.persona();
		LocalDate hoy = LocalDate.now(reloj);
		if (desde == null || hasta == null || desde.isBefore(hoy)) {
			throw new ReglaNegocioException("La delegación empieza hoy o después.");
		}
		if (hasta.isBefore(desde) || hasta.isAfter(desde.plusDays(DelegacionSiagie.MAX_DIAS))) {
			throw new ReglaNegocioException("La delegación dura como máximo " + DelegacionSiagie.MAX_DIAS + " días.");
		}
		Usuario delegada = usuarios.findById(usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
		if (!puedeRecibir(delegada)) {
			throw new ReglaNegocioException("Solo se delega a una persona activa de Administración, sin Caja ni Docente: "
					+ "quien cobra no recibe las calificaciones de los alumnos.");
		}
		if (delegada.getId().equals(director.usuarioId())) {
			throw new ReglaNegocioException("No te delegas a ti mismo: Dirección ya puede exportar.");
		}
		firma.firmar(ClaveFirma.delegacionSiagie(delegada.getId(), desde));
		DelegacionSiagie d = delegaciones.saveAndFlush(DelegacionSiagie.nueva(delegada.getId(), desde, hasta,
				director.getUsername()));
		auditoria.registrar(AccionAuditoria.DELEGACION_SIAGIE_OTORGADA, "delegacion_siagie", d.getId().toString(), null,
				delegada.getNombreUsuario() + " " + desde + " a " + hasta, director.nombreCompleto() + " delegó con su firma "
						+ "la exportación de calificaciones para el SIAGIE a " + delegada.getNombreCompleto() + " del "
						+ desde.format(FECHA) + " al " + hasta.format(FECHA) + ".");
	}
}
