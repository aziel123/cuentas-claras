package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.api.PendienteExterno;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.api.PendientesExternos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoLote;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoPlan;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.LoteSaldoInicialRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.PlanPensionRepository;

/**
 * Sprint 11, tanda 1b (F-D5): los planes de pensiones enviados y los lotes de saldo inicial enviados, para la bandeja de
 * Aprobaciones. Quien envió o editó el plan (o armó o envió el lote) lo ve, pero no como suyo para resolver. Se resuelven
 * en su pantalla: aquí no se aprueba nada.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class PendientesCobranza implements PendientesExternos {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final PlanPensionRepository planes;

	private final LoteSaldoInicialRepository lotes;

	public PendientesCobranza(PlanPensionRepository planes, LoteSaldoInicialRepository lotes) {
		this.planes = planes;
		this.lotes = lotes;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PendienteExterno> pendientes(String usuario) {
		Set<Rol> roles = PendientesExternos.rolesActuales();
		boolean aprueba = roles.contains(Rol.PROMOTOR) || roles.contains(Rol.DIRECTOR);
		List<PendienteExterno> lista = new ArrayList<>();
		planes.findByEstadoOrderByIdAsc(EstadoPlan.ENVIADO).forEach(p -> {
			String enlace = "/pensiones/planes/" + p.getId();
			if (PendientesExternos.puedeAbrir(enlace)) {
				lista.add(new PendienteExterno("PLAN_PENSION", "Plan de pensiones de " + p.getNivel().etiqueta() + " "
						+ p.getAnioEscolar().getAnio(), PendientesExternos.detalle("Enviado", p.getEnviadoPor(),
								p.getEnviadoEn()), p.getEnviadoPor(), p.getEnviadoEn(), enlace,
						aprueba && !p.participantes().contains(usuario)));
			}
		});
		lotes.findAllByOrderByCreadoEnDescIdDesc().stream().filter(l -> l.getEstado() == EstadoLote.ENVIADO).forEach(l -> {
			String enlace = "/pensiones/saldo-inicial/" + l.getId();
			if (PendientesExternos.puedeAbrir(enlace)) {
				boolean participo = usuario.equals(l.getCreadoPor()) || usuario.equals(l.getEnviadoPor());
				lista.add(new PendienteExterno("SALDO_INICIAL", "Saldo inicial al " + l.getFechaCorte().format(FECHA),
						PendientesExternos.detalle("Enviado", l.getEnviadoPor(), l.getEnviadoEn()), l.getEnviadoPor(),
						l.getEnviadoEn(), enlace, aprueba && !participo));
			}
		});
		return lista;
	}
}
