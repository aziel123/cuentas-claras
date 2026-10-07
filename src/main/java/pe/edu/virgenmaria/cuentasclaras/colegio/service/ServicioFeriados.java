package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.FeriadoRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.FeriadosVista;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Feriado;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadosNacionales;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Días no laborables EXTRA del colegio (sprint 5, tanda 3; G20 y decisión 59). Los 16 nacionales están en el código y
 * nadie los edita. Los extra:
 * <ul>
 *   <li>los registran SOLO Promotoría o Dirección (Administración y Caja los ven, no los tocan): un feriado retrasa las
 *       alertas de «sin depósito» y de «abono sin pareja», y quien maneja el dinero no debe poder moverlas;</li>
 *   <li>solo para fechas FUTURAS en la hora de Lima (en MySQL lo exige además trg_feriado_registro): nadie «crea» un
 *       feriado para apagar una alerta que ya venció;</li>
 *   <li>se anulan con motivo y solo antes de su fecha; registrar y anular quedan resaltados en la bitácora.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION','CAJA')")
public class ServicioFeriados {

	private final FeriadoRepository feriados;

	private final CalendarioHabil calendario;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ServicioFeriados(FeriadoRepository feriados, CalendarioHabil calendario, AuditoriaService auditoria,
			Clock reloj) {
		this.feriados = feriados;
		this.calendario = calendario;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public FeriadosVista vista(int anio) {
		LocalDate hoy = LocalDate.now(reloj);
		var nacionales = FeriadosNacionales.conNombres(anio).entrySet().stream()
				.map(e -> new FeriadosVista.Nacional(e.getKey(), e.getValue())).toList();
		var extras = feriados.findByFechaBetweenOrderByFechaAscIdAsc(LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31))
				.stream().map(f -> new FeriadosVista.Extra(f.getId(), f.getFecha(), f.getDescripcion(), f.isVigente(),
						f.isVigente() && f.getFecha().isAfter(hoy), f.getCreadoPor(),
						f.isVigente() ? null : "Anulado por " + f.getAnuladoPor() + ": " + f.getMotivoAnulacion()))
				.toList();
		return new FeriadosVista(anio, nacionales, extras, puedeEditar());
	}

	@Transactional
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public Long registrar(FeriadoRequest pedido) {
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate fecha = pedido.fecha();
		if (fecha == null || !fecha.isAfter(hoy)) {
			throw new ReglaNegocioException("Solo puedes registrar un día no laborable desde mañana: un día que ya pasó "
					+ "(o el de hoy) cambiaría alertas que ya se calcularon.");
		}
		if (fecha.isAfter(hoy.plusYears(2))) {
			throw new ReglaNegocioException("Registra días no laborables de los próximos dos años como máximo.");
		}
		if (FeriadosNacionales.es(fecha)) {
			throw new ReglaNegocioException("El " + Calendario.formatear(fecha) + " ya es feriado nacional.");
		}
		if (fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
			throw new ReglaNegocioException("El " + Calendario.formatear(fecha) + " es domingo: ya no es hábil.");
		}
		if (feriados.existsByFechaAndVigenteTrue(fecha)) {
			throw new ReglaNegocioException("El " + Calendario.formatear(fecha) + " ya está registrado.");
		}
		String descripcion = pedido.descripcion() == null ? "" : pedido.descripcion().strip();
		if (descripcion.length() < 5 || descripcion.length() > 80) {
			throw new ReglaNegocioException("Describe el día en 5 a 80 caracteres.");
		}
		Feriado feriado;
		try {
			feriado = feriados.saveAndFlush(Feriado.nuevo(fecha, descripcion));
		}
		catch (DataIntegrityViolationException e) {
			throw new ReglaNegocioException("El " + Calendario.formatear(fecha) + " ya está registrado.");
		}
		calendario.invalidar(ContextoColegio.actual());
		auditoria.registrar(AccionAuditoria.FERIADO_REGISTRADO, "feriado", feriado.getId().toString(), null,
				Calendario.formatear(fecha) + " · " + descripcion, "Día no laborable del colegio: no cuenta como día hábil "
						+ "en las alertas de depósito, abono y verificación, ni salen recordatorios ese día.");
		return feriado.getId();
	}

	@Transactional
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public void anular(Long id, String motivo) {
		Feriado feriado = feriados.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Día no encontrado"));
		String texto = Motivo.exigir(motivo);
		LocalDate hoy = LocalDate.now(reloj);
		if (!feriado.isVigente()) {
			throw new ReglaNegocioException("Ese día ya está anulado.");
		}
		if (!feriado.getFecha().isAfter(hoy)) {
			throw new ReglaNegocioException("Solo se anula antes de su fecha: el "
					+ Calendario.formatear(feriado.getFecha()) + " ya llegó y las alertas ya lo usaron.");
		}
		feriado.anular(texto, usuario(), LocalDateTime.now(reloj));
		feriados.saveAndFlush(feriado);
		calendario.invalidar(ContextoColegio.actual());
		auditoria.registrar(AccionAuditoria.FERIADO_ANULADO, "feriado", id.toString(),
				Calendario.formatear(feriado.getFecha()) + " · " + feriado.getDescripcion(), "ANULADO", "Motivo: " + texto);
	}

	private static String usuario() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	private static boolean puedeEditar() {
		Authentication a = SecurityContextHolder.getContext().getAuthentication();
		return a != null && a.getAuthorities().stream()
				.anyMatch(g -> "ROLE_PROMOTOR".equals(g.getAuthority()) || "ROLE_DIRECTOR".equals(g.getAuthority()));
	}
}
