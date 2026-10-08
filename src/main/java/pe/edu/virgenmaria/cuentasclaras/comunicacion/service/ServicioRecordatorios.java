package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioRenovacionFamilia;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recordatorios de vencimiento (sprint 5, tanda 3; decisiones 44 y 45). Por cada familia y fecha de vencimiento, con
 * todas sus cuotas por pagar de esa fecha, hay DOS mensajes como máximo y nada más:
 * <ul>
 *   <li>{@code RECORDATORIO_VENCIMIENTO} 3 días antes; si ese día es domingo o feriado, se adelanta al día de mensajes
 *       anterior (lunes a sábado, sin feriados nacionales ni del colegio);</li>
 *   <li>{@code CUOTA_VENCIDA} el día hábil siguiente al vencimiento, si sigue por pagar.</li>
 * </ul>
 * Van al responsable de pago de los alumnos, salvo que haya apagado sus recordatorios desde el portal (los avisos de
 * pago, anulación y descuento NO se apagan). La clave del mensaje es idempotente por familia, tipo y fecha: correr el
 * proceso dos veces no duplica nada. El texto no menciona evaluaciones ni amenaza (INDECOPI) y nombra al alumno solo por
 * su nombre de pila (Ley 29733). La hora de salida (08:00 a 20:00) la cuida el despacho.
 */
@Service
@PreAuthorize("hasRole('SISTEMA_MENSAJERIA')")
public class ServicioRecordatorios {

	/** Días antes del vencimiento. */
	public static final int DIAS_ANTES = 3;

	/** QA-S5-4: días que el «cuota vencida» puede salir tarde si su día se volvió no laborable o el proceso no corrió. */
	static final int PONERSE_AL_DIA = 5;

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final CuotaRepository cuotas;

	private final CreadorMensajes creador;

	private final CalendarioHabil calendario;

	private final AuditoriaService auditoria;

	private final MensajeRepository mensajes;

	public ServicioRecordatorios(CuotaRepository cuotas, CreadorMensajes creador, CalendarioHabil calendario,
			AuditoriaService auditoria, MensajeRepository mensajes) {
		this.mensajes = mensajes;
		this.cuotas = cuotas;
		this.creador = creador;
		this.calendario = calendario;
		this.auditoria = auditoria;
	}

	/** Lo que toca enviar hoy en el colegio actual. @return cuántos mensajes se crearon (o ya existían) */
	@Transactional(propagation = Propagation.MANDATORY)
	public int preparar(LocalDate hoy) {
		if (!calendario.admiteMensajes(hoy)) {
			return 0; // domingo o feriado: lo de hoy salió el día de mensajes anterior
		}
		int creados = 0;
		// Antes: el día de mensajes en o antes de (vencimiento - 3). Con feriados seguidos puede adelantarse varios días.
		// QA-S5-4: si ese día ya pasó sin enviarlo (un no laborable registrado tarde lo movió a un día que ya pasó, o el
		// proceso no corrió), sale HOY mientras la cuota no haya vencido. La clave idempotente evita duplicados.
		Map<Grupo, List<Cuota>> antes = agrupar(cuotas.porPagarQueVencenEntre(hoy.plusDays(1), hoy.plusDays(DIAS_ANTES + 10))
				.stream().filter(c -> !calendario.diaDeMensajesEnOAntes(c.getFechaVencimiento().minusDays(DIAS_ANTES))
						.isAfter(hoy))
				.toList());
		for (var e : antes.entrySet()) {
			creados += enviar(e.getKey(), e.getValue(), TipoMensaje.RECORDATORIO_VENCIMIENTO);
		}
		// Después: el día hábil siguiente al vencimiento (QA-S5-4: o el primer día de mensajes después, hasta 5 días).
		Map<Grupo, List<Cuota>> despues = agrupar(cuotas.porPagarQueVencenEntre(hoy.minusDays(20), hoy.minusDays(1))
				.stream().filter(c -> {
					LocalDate toca = calendario.siguienteDiaHabil(c.getFechaVencimiento());
					return !toca.isAfter(hoy) && !hoy.isAfter(toca.plusDays(PONERSE_AL_DIA));
				}).toList());
		for (var e : despues.entrySet()) {
			creados += enviar(e.getKey(), e.getValue(), TipoMensaje.CUOTA_VENCIDA);
		}
		if (creados > 0) {
			auditoria.registrar(AccionAuditoria.RECORDATORIOS_ENVIADOS, "mensaje", null, null, creados + " mensajes",
					"Recordatorios del " + hoy.format(FECHA) + ": " + antes.size() + " familia(s) con un vencimiento próximo y "
							+ despues.size() + " con un vencimiento de ayer por pagar (de lunes a sábado, de 8:00 a 20:00).");
		}
		return creados;
	}

	/** Una familia y una fecha de vencimiento. */
	record Grupo(Long familiaId, LocalDate vencimiento) {
	}

	private static Map<Grupo, List<Cuota>> agrupar(List<Cuota> lista) {
		return lista.stream().filter(c -> c.saldo().signum() > 0).collect(Collectors.groupingBy(
				c -> new Grupo(c.getAlumno().getFamilia().getId(), c.getFechaVencimiento()), LinkedHashMap::new,
				Collectors.toList()));
	}

	private int enviar(Grupo grupo, List<Cuota> lista, TipoMensaje tipo) {
		// Ya salió (o está por salir) el de esta familia, tipo y fecha: no se cuenta de nuevo (QA-S5-4 lo reintenta a diario).
		if (mensajes.existsByTipoAndFamiliaIdAndClaveEndingWith(tipo, grupo.familiaId(), ":" + grupo.vencimiento())) {
			return 0;
		}
		Set<Apoderado> responsables = new LinkedHashSet<>();
		lista.forEach(c -> responsables.add(c.getAlumno().getResponsablePago()));
		String concepto = concepto(lista);
		String fecha = grupo.vencimiento().format(FECHA);
		String monto = Dinero.formatear(Dinero.sumar(lista.stream().map(Cuota::saldo).toList()));
		List<String> parametros = tipo == TipoMensaje.RECORDATORIO_VENCIMIENTO
				? List.of(concepto, fecha, monto, lista.stream().map(c -> CodigoPago.deAlumno(c.getAlumno().getId()))
						.distinct().collect(Collectors.joining(" o ")))
				: List.of(Character.toUpperCase(concepto.charAt(0)) + concepto.substring(1), fecha, monto);
		PlantillaMensaje plantilla = tipo == TipoMensaje.RECORDATORIO_VENCIMIENTO ? PlantillaMensaje.RECORDATORIO
				: PlantillaMensaje.CUOTA_VENCIDA;
		CreadorMensajes.Contenido contenido = new CreadorMensajes.Contenido(tipo, plantilla, parametros, "familia",
				grupo.familiaId());
		int creados = 0;
		for (Apoderado a : responsables) {
			if (a.isActivo() && a.isRecordatoriosActivos()) {
				creados += creador.paraApoderado(a, contenido, false, grupo.vencimiento().toString()).size();
			}
		}
		return creados;
	}

	/** «la Pensión octubre 2026 de Ana» o «2 cuotas (Pensión octubre 2026 de Ana, Pensión octubre 2026 de Luis)». */
	static String concepto(List<Cuota> lista) {
		List<String> partes = new ArrayList<>();
		for (Cuota c : lista) {
			partes.add(c.getDescripcion() + " de " + ServicioRenovacionFamilia.nombreDePila(c.getAlumno().getNombres()));
		}
		return partes.size() == 1 ? "la " + partes.getFirst()
				: partes.size() + " cuotas (" + String.join(", ", partes) + ")";
	}
}
