package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.NumeroOperacion;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.DepositoCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.ReembolsoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AutoaprobacionException;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CategoriaExplicacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.MovimientoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ReglaPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.MovimientoBancarioRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.PartidaConciliacionRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.ObjetoAbierto;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LoteRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ControlParticipantes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Revisión de las diferencias de la conciliación (sprint 4, tanda 3). Solo Administración actúa; Promotoría y Dirección
 * ven. Todo exige el extracto CONFIRMADO a ciegas (así nadie empareja contra un extracto que todavía puede cambiar) y
 * nunca lo hace quien cobró, registró o depositó lo emparejado (ni quien preparó su cuenta): la base lo vuelve a exigir.
 * <ul>
 *   <li>{@link #confirmarSugerida}: una pareja SUGERIDA (mismo monto, fecha cercana, candidato único) es correcta.</li>
 *   <li>{@link #descartar}: «No es»; libera el movimiento y el objeto, y ese par no se vuelve a proponer.</li>
 *   <li>{@link #emparejarManual}: una persona elige el objeto de un movimiento sin pareja, con nota.</li>
 *   <li>{@link #explicar}: un movimiento ajeno a la cobranza (intereses, transferencia propia...), con nota.</li>
 * </ul>
 * Todo queda resaltado en la bitácora y en el resumen de Promotoría.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioPartidas {

	/** Días antes y después del movimiento en que se buscan objetos para emparejar a mano. */
	static final int DIAS_POSIBLES = 15;

	private final PartidaConciliacionRepository partidas;

	private final MovimientoBancarioRepository movimientos;

	private final ObjetosConciliables objetos;

	private final PagoRepository pagos;

	private final DepositoCajaRepository depositos;

	private final ReembolsoRepository reembolsos;

	private final LoteRecaudacionRepository lotes;

	private final ControlParticipantes participantes;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public ServicioPartidas(PartidaConciliacionRepository partidas, MovimientoBancarioRepository movimientos,
			ObjetosConciliables objetos, PagoRepository pagos, DepositoCajaRepository depositos,
			ReembolsoRepository reembolsos, LoteRecaudacionRepository lotes, ControlParticipantes participantes,
			AuditoriaService auditoria, ApplicationEventPublisher eventos, Clock reloj) {
		this.partidas = partidas;
		this.movimientos = movimientos;
		this.objetos = objetos;
		this.pagos = pagos;
		this.depositos = depositos;
		this.reembolsos = reembolsos;
		this.lotes = lotes;
		this.participantes = participantes;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	@Transactional(noRollbackFor = AutoaprobacionException.class)
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void confirmarSugerida(Long partidaId) {
		PartidaConciliacion partida = partidas.bloquear(partidaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Pareja no encontrada"));
		if (partida.getEstado() != EstadoPartida.PROPUESTA) {
			throw new ReglaNegocioException("Esta pareja ya se resolvió.");
		}
		if (partida.getRegla() != ReglaPartida.SUGERIDA) {
			throw new ReglaNegocioException("Esta pareja no es una sugerida: las exactas las confirma el sistema al "
					+ "confirmarse el extracto.");
		}
		MovimientoBancario movimiento = partida.getMovimiento();
		exigirExtractoConfirmado(movimiento);
		String usuario = usuario();
		exigirOtraPersona(partida.getObjetoTipo(), partida.objetoId(), usuario, partidaId);
		partida.confirmar(usuario, ahora());
		partidas.saveAndFlush(partida);
		auditoria.registrar(AccionAuditoria.PARTIDA_SUGERIDA_CONFIRMADA, "partida_conciliacion", partidaId.toString(),
				EstadoPartida.PROPUESTA.name(), EstadoPartida.CONFIRMADA.name(), usuario + " confirmó que el "
						+ movimiento.getTipo().etiqueta().toLowerCase(Locale.ROOT) + " del "
						+ Calendario.formatear(movimiento.getFecha()) + " por " + Dinero.formatear(movimiento.getMonto())
						+ " («" + movimiento.getDescripcion() + "», operación " + texto(movimiento.getNumeroOperacion())
						+ ") es el " + partida.getObjetoTipo().etiqueta().toLowerCase(Locale.ROOT) + " "
						+ partida.objetoId() + " por " + Dinero.formatear(partida.getMontoObjeto()) + " (operación "
						+ texto(operacionDe(partida.getObjetoTipo(), partida.objetoId())) + ")"
						+ avisos(partida, operacionDe(partida.getObjetoTipo(), partida.objetoId())) + ".");
		eventos.publishEvent(new PartidaConfirmada(partida.getColegioId(), partidaId));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void descartar(Long partidaId, String nota) {
		PartidaConciliacion partida = partidas.bloquear(partidaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Pareja no encontrada"));
		if (partida.getEstado() != EstadoPartida.PROPUESTA) {
			throw new ReglaNegocioException("Esta pareja ya se resolvió: una pareja confirmada no se descarta.");
		}
		if (partida.getRegla() == ReglaPartida.EXACTA) {
			throw new ReglaNegocioException("Una pareja exacta (misma operación y monto) no se descarta: la confirma el "
					+ "sistema al confirmarse el extracto.");
		}
		String motivo = Motivo.exigir(nota);
		String usuario = usuario();
		partida.descartar(usuario, ahora());
		partidas.saveAndFlush(partida);
		MovimientoBancario m = partida.getMovimiento();
		auditoria.registrar(AccionAuditoria.PARTIDA_DESCARTADA, "partida_conciliacion", partidaId.toString(),
				EstadoPartida.PROPUESTA.name(), EstadoPartida.DESCARTADA.name(), usuario + " descartó la pareja "
						+ partida.getRegla().etiqueta().toLowerCase(Locale.ROOT) + " del " + m.getTipo().etiqueta()
								.toLowerCase(Locale.ROOT) + " del " + Calendario.formatear(m.getFecha()) + " por "
						+ Dinero.formatear(m.getMonto()) + " con el " + partida.getObjetoTipo().etiqueta()
								.toLowerCase(Locale.ROOT) + " " + partida.objetoId() + ". Nota: " + motivo);
	}

	/**
	 * Empareja a mano un movimiento sin pareja con un objeto abierto (de la lista de posibles), con nota. Queda
	 * CONFIRMADA en el acto (el extracto ya está confirmado) y resaltada para Promotoría.
	 */
	@Transactional(noRollbackFor = AutoaprobacionException.class)
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void emparejarManual(Long movimientoId, ObjetoPartida tipo, Long objetoId, String nota) {
		MovimientoBancario movimiento = movimientoSinPareja(movimientoId);
		if (tipo == null || objetoId == null || tipo == ObjetoPartida.EXPLICACION) {
			throw new ReglaNegocioException("Elige con qué se empareja el movimiento.");
		}
		ObjetoAbierto objeto = posiblesDe(movimiento).stream().filter(o -> o.tipo() == tipo && o.id().equals(objetoId))
				.findFirst().orElseThrow(() -> new ReglaNegocioException("Eso ya no se puede emparejar con este movimiento "
						+ "(ya tiene pareja o no es del mismo tipo ni de fechas cercanas). Recarga la pantalla."));
		String motivo = Motivo.exigir(nota);
		String usuario = usuario();
		exigirOtraPersona(tipo, objetoId, usuario, null);
		PartidaConciliacion partida = partidas.save(PartidaConciliacion.proponer(movimiento, tipo, objetoId, objeto.monto(),
				ReglaPartida.MANUAL, motivo));
		partida.confirmar(usuario, ahora());
		partidas.saveAndFlush(partida);
		List<String> avisos = new ArrayList<>();
		if (movimiento.getNumeroOperacion() != null && objeto.operacion() != null
				&& !movimiento.getNumeroOperacion().equals(objeto.operacion())) {
			avisos.add(NumeroOperacion.parecidos(movimiento.getNumeroOperacion(), objeto.operacion())
					? "NÚMERO PARECIDO (difiere en un carácter)" : "operación distinta");
		}
		if (partida.getDiferencia().signum() != 0) {
			avisos.add("diferencia de " + Dinero.formatear(partida.getDiferencia()));
		}
		auditoria.registrar(AccionAuditoria.PARTIDA_MANUAL_REGISTRADA, "partida_conciliacion", partida.getId().toString(),
				null, ReglaPartida.MANUAL.name() + " · " + tipo.name(), usuario + " emparejó a mano el "
						+ movimiento.getTipo().etiqueta().toLowerCase(Locale.ROOT) + " del "
						+ Calendario.formatear(movimiento.getFecha()) + " por " + Dinero.formatear(movimiento.getMonto())
						+ " («" + movimiento.getDescripcion() + "», operación " + texto(movimiento.getNumeroOperacion())
						+ ") con: " + objeto.detalle() + " por " + Dinero.formatear(objeto.monto()) + " (operación "
						+ texto(objeto.operacion()) + ")" + (avisos.isEmpty() ? "" : ". Atención: " + String.join("; ",
								avisos)) + ". Nota: " + motivo);
		eventos.publishEvent(new PartidaConfirmada(partida.getColegioId(), partida.getId()));
	}

	/** Un movimiento ajeno a la cobranza, con su categoría y una nota. Queda CONFIRMADO y resaltado para Promotoría. */
	@Transactional
	@PreAuthorize("hasRole('ADMINISTRACION')")
	public void explicar(Long movimientoId, CategoriaExplicacion categoria, String nota) {
		MovimientoBancario movimiento = movimientoSinPareja(movimientoId);
		if (categoria == null) {
			throw new ReglaNegocioException("Elige qué es el movimiento (intereses, transferencia propia...).");
		}
		if (!categoriasPara(movimiento.getTipo()).contains(categoria)) {
			throw new ReglaNegocioException("«" + categoria.etiqueta() + "» no corresponde a un "
					+ movimiento.getTipo().etiqueta().toLowerCase(Locale.ROOT) + ".");
		}
		String motivo = Motivo.exigir(nota);
		String usuario = usuario();
		PartidaConciliacion partida = partidas.save(PartidaConciliacion.explicar(movimiento, categoria, motivo));
		partida.confirmar(usuario, ahora());
		partidas.saveAndFlush(partida);
		auditoria.registrar(AccionAuditoria.MOVIMIENTO_EXPLICADO, "partida_conciliacion", partida.getId().toString(),
				null, ReglaPartida.EXPLICADA.name() + " · " + categoria.name(), usuario + " explicó el "
						+ movimiento.getTipo().etiqueta().toLowerCase(Locale.ROOT) + " del "
						+ Calendario.formatear(movimiento.getFecha()) + " por " + Dinero.formatear(movimiento.getMonto())
						+ " («" + movimiento.getDescripcion() + "») como «" + categoria.etiqueta() + "». Nota: " + motivo);
	}

	/** Lo que se puede emparejar a mano con ese movimiento: abierto, del mismo tipo y de fechas cercanas. */
	@Transactional(readOnly = true)
	public List<ObjetoAbierto> posibles(Long movimientoId) {
		MovimientoBancario movimiento = movimientos.findById(movimientoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Movimiento no encontrado"));
		return posiblesDe(movimiento);
	}

	/** Las categorías que corresponden a un abono o a un cargo. */
	public static Set<CategoriaExplicacion> categoriasPara(TipoMovimiento tipo) {
		return tipo == TipoMovimiento.ABONO
				? EnumSet.of(CategoriaExplicacion.INTERESES, CategoriaExplicacion.TRANSFERENCIA_PROPIA,
						CategoriaExplicacion.APORTE, CategoriaExplicacion.OTRO_INGRESO)
				: EnumSet.of(CategoriaExplicacion.TRANSFERENCIA_PROPIA, CategoriaExplicacion.COMISION_BANCARIA,
						CategoriaExplicacion.IMPUESTO_ITF, CategoriaExplicacion.OTRO_EGRESO);
	}

	private List<ObjetoAbierto> posiblesDe(MovimientoBancario m) {
		return objetos.abiertos(m.getFecha().minusDays(DIAS_POSIBLES), m.getFecha().plusDays(DIAS_POSIBLES)).stream()
				.filter(o -> o.tipo().movimiento() == m.getTipo())
				.sorted(Comparator.comparing((ObjetoAbierto o) -> o.monto().subtract(m.getMonto()).abs())
						.thenComparing(o -> Math.abs(o.fecha().toEpochDay() - m.getFecha().toEpochDay())))
				.limit(30).toList();
	}

	private MovimientoBancario movimientoSinPareja(Long movimientoId) {
		MovimientoBancario movimiento = movimientos.findById(movimientoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Movimiento no encontrado"));
		exigirExtractoConfirmado(movimiento);
		if (partidas.findByMovimientoVigente(movimientoId).isPresent()) {
			throw new ReglaNegocioException("Este movimiento ya tiene pareja (propuesta o confirmada). Recarga la pantalla.");
		}
		return movimiento;
	}

	private static void exigirExtractoConfirmado(MovimientoBancario movimiento) {
		if (movimiento.getExtracto().getEstado() != EstadoExtracto.CONFIRMADO) {
			throw new ReglaNegocioException("Primero Promotoría o Dirección confirman el extracto a ciegas: recién entonces "
					+ "se emparejan sus movimientos.");
		}
	}

	/**
	 * Quien cobró, registró o depositó lo emparejado (o subió el lote de recaudación), o quien preparó su cuenta, no da
	 * por buena su pareja: el intento queda en la bitácora.
	 */
	private void exigirOtraPersona(ObjetoPartida tipo, Long objetoId, String usuario, Long partidaId) {
		Set<String> responsables = new HashSet<>();
		switch (tipo) {
			case PAGO -> pagos.findById(objetoId).ifPresent(p -> {
				responsables.add(p.getCajero());
				responsables.add(p.getCreadoPor());
			});
			case DEPOSITO -> depositos.findById(objetoId).ifPresent(d -> {
				responsables.add(d.getCreadoPor());
				responsables.add(d.getCaja().getCajero());
			});
			case REEMBOLSO -> reembolsos.findById(objetoId).ifPresent(r -> responsables.add(r.getCreadoPor()));
			case LOTE_RECAUDACION -> lotes.findById(objetoId).ifPresent(t -> responsables.add(t.getCreadoPor()));
			default -> {
				// La liquidación la registra el sistema (por la API de la pasarela).
			}
		}
		if (participantes.ampliar(responsables).contains(usuario)) {
			auditoria.registrar(AccionAuditoria.AUTOAPROBACION_RECHAZADA, "partida_conciliacion",
					partidaId == null ? null : partidaId.toString(), null, tipo.name() + " " + objetoId,
					"Intentó dar por buena la pareja del extracto de algo que cobró, registró o depositó (o que hizo una "
							+ "cuenta que preparó). Se rechazó.");
			throw new AutoaprobacionException("No puedes confirmar la pareja de algo que tú cobraste, registraste o "
					+ "depositaste: debe hacerlo otra persona de Administración.");
		}
	}

	/** Los avisos de una pareja no exacta: operación distinta, número parecido (posible número inventado), diferencia. */
	private static String avisos(PartidaConciliacion p, String operacionObjeto) {
		List<String> avisos = new ArrayList<>();
		String delBanco = p.getMovimiento().getNumeroOperacion();
		if (delBanco != null && operacionObjeto != null && !delBanco.equals(operacionObjeto)) {
			avisos.add(NumeroOperacion.parecidos(delBanco, operacionObjeto) ? "NÚMERO PARECIDO (difiere en un carácter)"
					: "operación distinta");
		}
		if (p.getDiferencia().signum() != 0) {
			avisos.add("diferencia de " + Dinero.formatear(p.getDiferencia()));
		}
		return avisos.isEmpty() ? "" : ". Atención: " + String.join("; ", avisos);
	}

	private String operacionDe(ObjetoPartida tipo, Long id) {
		return switch (tipo) {
			case PAGO -> pagos.findById(id).map(pe.edu.virgenmaria.cuentasclaras.caja.model.Pago::getNumeroOperacion)
					.orElse(null);
			case DEPOSITO -> depositos.findById(id).map(pe.edu.virgenmaria.cuentasclaras.caja.model.DepositoCaja
					::getNumeroOperacion).orElse(null);
			case REEMBOLSO -> reembolsos.findById(id).map(pe.edu.virgenmaria.cuentasclaras.caja.model.Reembolso
					::getNumeroOperacion).orElse(null);
			default -> null;
		};
	}

	private static String texto(String operacion) {
		return operacion == null ? "sin número" : operacion;
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	private static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null) {
			throw new AccessDeniedException("Se requiere un usuario en sesión");
		}
		return autenticacion.getName();
	}
}
