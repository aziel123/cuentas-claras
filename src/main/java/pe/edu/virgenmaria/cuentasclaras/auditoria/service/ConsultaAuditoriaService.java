package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
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
		RangoFechas rango = rangoPorDefecto();
		LocalDate inicio = desde == null ? rango.desde() : desde;
		LocalDate fin = hasta == null ? rango.hasta() : hasta;
		if (fin.isBefore(inicio)) {
			throw new ReglaNegocioException("La fecha «hasta» no puede ser anterior a «desde».");
		}
		if (ChronoUnit.DAYS.between(inicio, fin) >= MAXIMO_DIAS) {
			throw new ReglaNegocioException("Consulta como máximo un año a la vez.");
		}
		Long colegioId = ContextoColegio.actual();
		if (colegioId == null || colegioId <= 0) {
			return Page.empty(PageRequest.of(0, TAMANO_PAGINA));
		}
		return eventos.findByColegioIdAndOcurridoEnBetweenOrderBySecuenciaDesc(colegioId, inicio.atStartOfDay(),
						fin.atTime(LocalTime.MAX).truncatedTo(ChronoUnit.MICROS),
						PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA))
				.map(ConsultaAuditoriaService::vista);
	}

	private static EventoVista vista(EventoAuditoria e) {
		return new EventoVista(e.getSecuencia(), e.getOcurridoEn(), e.getNombreUsuario(), rolesLegibles(e.getRoles()),
				e.getAccion().descripcion(), e.getAccion().requiereAtencion(),
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
