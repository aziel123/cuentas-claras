package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import org.springframework.context.ApplicationEventPublisher;
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
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	private final UsuarioRepository usuarios;

	private final ApplicationEventPublisher eventos;

	/** Sprint 7, tanda 2: la firma de la sesión de quien resuelve (sección 3.4). */
	private final FirmaSesion firmaSesion;

	public ServicioFeriados(FeriadoRepository feriados, CalendarioHabil calendario, AuditoriaService auditoria,
			Clock reloj, UsuarioRepository usuarios, ApplicationEventPublisher eventos, FirmaSesion firmaSesion) {
		this.firmaSesion = firmaSesion;
		this.usuarios = usuarios;
		this.eventos = eventos;
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
						f.isVigente() ? null : "Anulado por " + f.getAnuladoPor() + ": " + f.getMotivoAnulacion(),
						f.isVigente() && f.isPendiente(), f.isVigente() && f.isPendiente() && f.getFecha().isAfter(hoy)
								&& !f.getCreadoPor().equals(usuarioOVacio()), f.getAprobadoPor()))
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
		exigirTopes(fecha, null);
		Feriado feriado;
		try {
			feriado = feriados.saveAndFlush(Feriado.nuevo(fecha, descripcion));
		}
		catch (DataIntegrityViolationException e) {
			throw new ReglaNegocioException("El " + Calendario.formatear(fecha) + " ya está registrado.");
		}
		String quien = usuario();
		auditoria.registrar(AccionAuditoria.FERIADO_PROPUESTO, "feriado", feriado.getId().toString(), null,
				Calendario.formatear(fecha) + " · " + descripcion, "Día no laborable PROPUESTO por " + quien + ": no cuenta "
						+ "como hábil hasta que lo apruebe otra persona de Promotoría o Dirección. Se avisó por mensaje a "
						+ "Promotoría.");
		eventos.publishEvent(new FeriadoPropuesto(feriado.getId(), fecha, descripcion, quien));
		return feriado.getId();
	}

	/**
	 * S5-M3: OTRA persona aprueba el día propuesto (si lo propuso Promotoría, lo aprueba Dirección, y al revés). Recién
	 * entonces deja de contar como hábil. Se vuelven a revisar los topes.
	 */
	@Transactional
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public void aprobar(Long id) {
		Feriado feriado = feriados.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Día no encontrado"));
		LocalDate hoy = LocalDate.now(reloj);
		if (!feriado.isVigente() || !feriado.isPendiente()) {
			throw new ReglaNegocioException("Ese día no está por aprobar.");
		}
		if (!feriado.getFecha().isAfter(hoy)) {
			throw new ReglaNegocioException("Ese día ya llegó: no se aprueba un día no laborable del pasado.");
		}
		String quien = usuario();
		if (quien.equals(feriado.getCreadoPor())) {
			throw new ReglaNegocioException("No puedes aprobar un día que tú propusiste: debe aprobarlo otra persona.");
		}
		Set<Rol> dePropone = usuarios.findByNombreUsuario(feriado.getCreadoPor()).map(u -> (Set<Rol>) u.getRoles())
				.orElse(Set.of());
		Rol otro = dePropone.contains(Rol.PROMOTOR) && !dePropone.contains(Rol.DIRECTOR) ? Rol.DIRECTOR
				: dePropone.contains(Rol.DIRECTOR) && !dePropone.contains(Rol.PROMOTOR) ? Rol.PROMOTOR : null;
		if (otro != null && !tieneRol(otro)) {
			throw new ReglaNegocioException("Lo propuso " + (otro == Rol.DIRECTOR ? "Promotoría" : "Dirección")
					+ ": debe aprobarlo " + (otro == Rol.DIRECTOR ? "Dirección." : "Promotoría."));
		}
		exigirTopes(feriado.getFecha(), feriado.getId());
		firmaSesion.firmar(ClaveFirma.feriado(feriado.getId()));
		feriado.aprobar(quien, LocalDateTime.now(reloj));
		feriados.saveAndFlush(feriado);
		calendario.invalidar(ContextoColegio.actual());
		auditoria.registrar(AccionAuditoria.FERIADO_APROBADO, "feriado", id.toString(), "PROPUESTO", "APROBADO",
				Calendario.formatear(feriado.getFecha()) + " · " + feriado.getDescripcion() + ". Propuesto por "
						+ feriado.getCreadoPor() + ", aprobado por " + quien + ". Ya no cuenta como hábil en las alertas de "
						+ "depósito, abono y verificación, ni salen recordatorios ese día.");
	}

	/**
	 * S5-M3: como máximo {@value #MAXIMO_POR_MES} días extra por mes (propuestos o aprobados) y no más de
	 * {@value #MAXIMO_SEGUIDOS} días hábiles seguidos (los fines de semana y los feriados nacionales no cortan la racha).
	 */
	private void exigirTopes(LocalDate fecha, Long propio) {
		LocalDate inicio = fecha.withDayOfMonth(1);
		List<Feriado> cercanos = feriados.findByVigenteTrueAndFechaBetween(inicio.minusMonths(1),
				inicio.plusMonths(2).minusDays(1)).stream().filter(f -> !f.getId().equals(propio)).toList();
		long delMes = cercanos.stream().filter(f -> f.getFecha().getMonth() == fecha.getMonth()
				&& f.getFecha().getYear() == fecha.getYear()).count();
		if (delMes >= MAXIMO_POR_MES) {
			throw new ReglaNegocioException("Ya hay " + delMes + " días no laborables del colegio en ese mes: el máximo es "
					+ MAXIMO_POR_MES + ".");
		}
		Set<LocalDate> extras = new HashSet<>();
		cercanos.forEach(f -> extras.add(f.getFecha()));
		int seguidos = 1 + racha(fecha, extras, -1) + racha(fecha, extras, 1);
		if (seguidos > MAXIMO_SEGUIDOS) {
			throw new ReglaNegocioException("Con ese día quedarían " + seguidos + " días hábiles seguidos sin trabajar: el "
					+ "máximo es " + MAXIMO_SEGUIDOS + ".");
		}
	}

	private static int racha(LocalDate fecha, Set<LocalDate> extras, int paso) {
		int cuenta = 0;
		LocalDate d = fecha.plusDays(paso);
		for (int i = 0; i < 15; i++, d = d.plusDays(paso)) {
			if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY || FeriadosNacionales.es(d)) {
				continue;
			}
			if (!extras.contains(d)) {
				break;
			}
			cuenta++;
		}
		return cuenta;
	}

	static final int MAXIMO_POR_MES = 3;

	static final int MAXIMO_SEGUIDOS = 2;

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

	private static String usuarioOVacio() {
		Authentication a = SecurityContextHolder.getContext().getAuthentication();
		return a == null ? "" : a.getName();
	}

	private static boolean tieneRol(Rol rol) {
		Authentication a = SecurityContextHolder.getContext().getAuthentication();
		return a != null && a.getAuthorities().stream().anyMatch(g -> ("ROLE_" + rol.name()).equals(g.getAuthority()));
	}

	private static boolean puedeEditar() {
		Authentication a = SecurityContextHolder.getContext().getAuthentication();
		return a != null && a.getAuthorities().stream()
				.anyMatch(g -> "ROLE_PROMOTOR".equals(g.getAuthority()) || "ROLE_DIRECTOR".equals(g.getAuthority()));
	}
}
