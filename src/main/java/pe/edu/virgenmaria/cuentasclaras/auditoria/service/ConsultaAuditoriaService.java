package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consulta de la bitácora del colegio actual. {@link EventoAuditoria} NO tiene {@code @TenantId}
 * (la cadena es global), así que aquí se filtra por colegio de forma EXPLÍCITA.
 * Los intentos de ingreso con usuarios inexistentes no tienen colegio y no aparecen aquí.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class ConsultaAuditoriaService {

	public static final int TAMANO_PAGINA = 50;

	static final int DIAS_POR_DEFECTO = 7;

	static final int MAXIMO_DIAS = 366;

	static final int MAXIMO_PARA_REVISAR = 20;

	static final Set<AccionAuditoria> ACCIONES_PARA_REVISAR = EnumSet.of(AccionAuditoria.CLAVE_RESTABLECIDA,
			AccionAuditoria.USUARIO_CREADO, AccionAuditoria.ROLES_CAMBIADOS);

	private final EventoAuditoriaRepository eventos;

	private final Clock reloj;

	public ConsultaAuditoriaService(EventoAuditoriaRepository eventos, Clock reloj) {
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/** Rango por defecto: los últimos 7 días, hasta hoy (hora de Lima). */
	public RangoFechas rangoPorDefecto() {
		LocalDate hoy = LocalDate.now(reloj);
		return new RangoFechas(hoy.minusDays(DIAS_POR_DEFECTO - 1L), hoy);
	}

	@Transactional(readOnly = true)
	public Page<EventoVista> listar(LocalDate desde, LocalDate hasta, int pagina) {
		return listar(FiltroBitacora.fechas(desde, hasta), pagina);
	}

	@Transactional(readOnly = true)
	public Page<EventoVista> listar(FiltroBitacora filtro, int pagina) {
		RangoFechas rango = rangoPorDefecto();
		LocalDate inicio = filtro.desde() == null ? rango.desde() : filtro.desde();
		LocalDate fin = filtro.hasta() == null ? rango.hasta() : filtro.hasta();
		if (fin.isBefore(inicio)) {
			throw new ReglaNegocioException("La fecha «hasta» no puede ser anterior a «desde».");
		}
		if (ChronoUnit.DAYS.between(inicio, fin) >= MAXIMO_DIAS) {
			throw new ReglaNegocioException("Consulta como máximo un año a la vez.");
		}
		Long colegioId = colegioActual();
		PageRequest pedido = PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA);
		if (colegioId == null) {
			return Page.empty(pedido);
		}
		return eventos.buscar(colegioId, inicio.atStartOfDay(), fin.atTime(LocalTime.MAX).truncatedTo(ChronoUnit.MICROS),
						filtro.nombreUsuario(), filtro.accion(), filtro.soloRevisar(), AccionAuditoria.siempreRevisar(),
						AccionAuditoria.CON_ROLES, pedido)
				.map(ConsultaAuditoriaService::vista);
	}

	/**
	 * Restablecimientos de clave, altas y cambios de roles del colegio en los últimos 7 días: quién, a quién y
	 * cuándo. Es la vigilancia de Promotoría contra cuentas fantasma o suplantaciones.
	 */
	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional(readOnly = true)
	public List<EventoRevision> paraRevisar() {
		Long colegioId = colegioActual();
		if (colegioId == null) {
			return List.of();
		}
		LocalDateTime desde = LocalDate.now(reloj).minusDays(DIAS_POR_DEFECTO - 1L).atStartOfDay();
		return eventos.findByColegioIdAndAccionInAndOcurridoEnGreaterThanEqualOrderBySecuenciaDesc(colegioId,
						ACCIONES_PARA_REVISAR, desde, Limit.of(MAXIMO_PARA_REVISAR))
				.stream()
				.map(e -> new EventoRevision(e.getOcurridoEn(), e.getNombreUsuario(), e.getAccion().descripcion(),
						idDeUsuario(e.getEntidadId()), e.getValorNuevo() == null ? null : legible(e.getValorNuevo())))
				.toList();
	}

	private static Long idDeUsuario(String entidadId) {
		try {
			return entidadId == null ? null : Long.valueOf(entidadId);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private static Long colegioActual() {
		Long colegioId = ContextoColegio.actual();
		return colegioId == null || colegioId <= 0 ? null : colegioId;
	}

	private static EventoVista vista(EventoAuditoria e) {
		return new EventoVista(e.getSecuencia(), e.getOcurridoEn(), e.getNombreUsuario(), rolesLegibles(e.getRoles()),
				e.getAccion().descripcion(), e.getAccion().requiereRevision(e.getValorNuevo()),
				cambio(e.getValorAnterior(), e.getValorNuevo()), e.getDetalle(), e.getIp());
	}

	/** "anterior → nuevo"; si solo hay uno de los dos, ese valor. En lenguaje claro. */
	static String cambio(String anterior, String nuevo) {
		if (anterior == null && nuevo == null) {
			return null;
		}
		if (anterior == null) {
			return legible(nuevo);
		}
		if (nuevo == null) {
			return "Antes: " + legible(anterior);
		}
		return legible(anterior) + " → " + legible(nuevo);
	}

	/** Cambia los nombres técnicos guardados (roles=CAJA, INTEGRA) por texto para la promotora. */
	static String legible(String valor) {
		String texto = valor.replace("roles=", "Roles: ").replace("INTEGRA", "Íntegra").replace("ALTERADA", "Alterada");
		for (Rol rol : Rol.values()) {
			texto = texto.replaceAll("\\b" + rol.name() + "\\b", rol.etiqueta());
		}
		return texto;
	}

	static String rolesLegibles(String roles) {
		if (roles == null || roles.isBlank()) {
			return null;
		}
		return Arrays.stream(roles.split(","))
				.map(String::strip)
				.map(r -> Arrays.stream(Rol.values()).filter(x -> x.name().equals(r)).findFirst()
						.map(Rol::etiqueta).orElse(r))
				.collect(Collectors.joining(" · "));
	}

	/** Rango de fechas de la consulta (ambos días incluidos). */
	public record RangoFechas(LocalDate desde, LocalDate hasta) {
	}
}
