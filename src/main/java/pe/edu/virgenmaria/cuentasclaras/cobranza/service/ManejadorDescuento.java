package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AjusteCuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.CalculadoraDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Descuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.AjusteCuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.DescuentoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aprobación de un descuento en la bandeja (otra persona de Promotoría o Dirección). Bloquea las cuotas (por id) y
 * después el descuento, y VUELVE A CALCULAR: si algo cambió desde el pedido (una cuota se pagó, otro descuento la tocó),
 * no se aplica. Aprueba el descuento con flush (el trigger del ajuste lo exige APROBADO), inserta un ajuste por cuota,
 * refleja el libro de descuentos en cada cuota y audita cuota por cuota.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorDescuento implements ManejadorSolicitud {

	/** Sprint 4: cuotas con un pago en línea en curso (puerto opcional que implementa pasarela). */
	private org.springframework.beans.factory.ObjectProvider<CuotasEnPagoEnLinea> pagosEnLinea;

	@org.springframework.beans.factory.annotation.Autowired
	void setPagosEnLinea(org.springframework.beans.factory.ObjectProvider<CuotasEnPagoEnLinea> pagosEnLinea) {
		this.pagosEnLinea = pagosEnLinea;
	}

	private void exigirSinPagoEnLinea(java.util.Collection<Long> cuotaIds) {
		CuotasEnPagoEnLinea puerto = pagosEnLinea == null ? null : pagosEnLinea.getIfAvailable();
		if (puerto != null && puerto.algunaEnCurso(cuotaIds)) {
			throw new pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException(CuotasEnPagoEnLinea.MENSAJE);
		}
	}


	static final String CAMBIO = "El descuento cambió desde que se pidió";

	private final DescuentoRepository descuentos;

	private final AjusteCuotaRepository ajustes;

	private final CuotaRepository cuotas;

	private final AlumnoRepository alumnos;

	private final MatriculaRepository matriculas;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	private final org.springframework.context.ApplicationEventPublisher eventos;

	public ManejadorDescuento(DescuentoRepository descuentos, AjusteCuotaRepository ajustes, CuotaRepository cuotas,
			AlumnoRepository alumnos, MatriculaRepository matriculas, AuditoriaService auditoria, Clock reloj,
			org.springframework.context.ApplicationEventPublisher eventos) {
		this.eventos = eventos;
		this.descuentos = descuentos;
		this.ajustes = ajustes;
		this.cuotas = cuotas;
		this.alumnos = alumnos;
		this.matriculas = matriculas;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.DESCUENTO;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		Long id = solicitud.getEntidadId();
		// Orden de bloqueos: cuotas (por id) y después el descuento.
		List<Long> ids = Arrays.stream(descuentos.cuotasDe(id)
				.orElseThrow(() -> new ReglaNegocioException("El descuento de la solicitud no existe.")).split(","))
				.filter(s -> !s.isBlank()).map(Long::valueOf).sorted().toList();
		List<Cuota> bloqueadas = cuotas.bloquear(ids);
		exigirSinPagoEnLinea(ids);
		Descuento descuento = descuentos.bloquear(id).orElseThrow();
		if (descuento.getEstado() != EstadoDescuento.SOLICITADO) {
			throw new ReglaNegocioException("El descuento ya fue " + descuento.getEstado().etiqueta().toLowerCase() + ".");
		}
		if (bloqueadas.size() != ids.size()) {
			throw new ReglaNegocioException(CAMBIO + ": alguna cuota ya no existe. Recházalo.");
		}
		Map<Cuota, BigDecimal> calculo = new LinkedHashMap<>();
		for (Cuota cuota : bloqueadas) {
			if (!cuota.admiteCobro()) {
				throw new ReglaNegocioException(CAMBIO + ": la cuota «" + cuota.getDescripcion() + "» ya no está pendiente "
						+ "(está " + cuota.getEstado().name().toLowerCase(java.util.Locale.ROOT)
						+ "). Recházalo y, si corresponde, pide otro.");
			}
			BigDecimal ajuste = CalculadoraDescuento.ajuste(cuota.getMonto(), descuento.getModalidad(),
					descuento.getValor());
			if (ajuste.compareTo(cuota.saldo()) > 0) {
				throw new ReglaNegocioException(CAMBIO + ": en «" + cuota.getDescripcion() + "» ya falta pagar solo "
						+ Dinero.formatear(cuota.saldo()) + ". Recházalo y, si corresponde, pide otro.");
			}
			calculo.put(cuota, ajuste);
		}
		BigDecimal total = Dinero.sumar(calculo.values());
		if (total.compareTo(descuento.getTotalEstimado()) != 0) {
			throw new ReglaNegocioException(CAMBIO + " (ahora sería " + Dinero.formatear(total) + " y se pidió "
					+ Dinero.formatear(descuento.getTotalEstimado()) + "). Recházalo y pide otro.");
		}
		descuento.aprobar(aprobador, ahora());
		descuentos.saveAndFlush(descuento);
		for (Map.Entry<Cuota, BigDecimal> e : calculo.entrySet()) {
			Cuota cuota = e.getKey();
			String antes = cuota.getEstado().name() + " · saldo " + Dinero.formatear(cuota.saldo());
			ajustes.save(AjusteCuota.de(descuento, cuota, e.getValue()));
			cuota.reflejarDescuentos(Objects.requireNonNullElse(ajustes.sumaDeCuota(cuota.getId()), Dinero.CERO));
			cuotas.saveAndFlush(cuota);
			auditoria.registrar(AccionAuditoria.DESCUENTO_APROBADO, "cuota", cuota.getId().toString(), antes,
					cuota.getEstado().name() + " · saldo " + Dinero.formatear(cuota.saldo()),
					descuento.getTipo().etiqueta() + " " + descuento.valorTexto() + " (descuento " + descuento.getId()
							+ "): " + cuota.getDescripcion() + " de " + cuota.getAlumno().nombreCompleto() + " −"
							+ Dinero.formatear(e.getValue()) + ". Pedido por " + solicitud.getSolicitadoPor()
							+ ", aprobado por " + aprobador + ". Sustento: " + descuento.getSustento());
		}
		eventos.publishEvent(new DescuentoAprobado(descuento.getId(), descuento.getAlumno().getId(),
				calculo.keySet().stream().map(Cuota::getId).toList(), total, aprobador));
	}

	@Override
	public void alRechazar(SolicitudCambio solicitud) {
		Descuento descuento = descuentos.bloquear(solicitud.getEntidadId()).orElseThrow();
		if (descuento.getEstado() != EstadoDescuento.SOLICITADO) {
			return;
		}
		descuento.rechazar(solicitud.getResueltoPor(), ahora());
		descuentos.saveAndFlush(descuento);
		auditoria.registrar(AccionAuditoria.DESCUENTO_RECHAZADO, "descuento", descuento.getId().toString(),
				EstadoDescuento.SOLICITADO.name(), EstadoDescuento.RECHAZADO.name(), descuento.getTipo().etiqueta() + " "
						+ descuento.valorTexto() + " para " + descuento.getAlumno().nombreCompleto() + ". Motivo del rechazo: "
						+ solicitud.getComentario());
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		Descuento d = descuentos.findById(solicitud.getEntidadId()).orElse(null);
		if (d == null) {
			return List.of();
		}
		List<String> lineas = new ArrayList<>();
		Alumno alumno = d.getAlumno();
		lineas.add("Alumno: " + alumno.nombreCompleto() + " (" + alumno.getFamilia().getNombre() + ")");
		List<String> hermanos = matriculas.delAnioParaAlumnos(d.getAnioEscolar().getId(),
				alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(alumno.getFamilia().getId()).stream().map(Alumno::getId)
						.toList()).stream().filter(Matricula::activa).map(m -> m.getAlumno().nombreCompleto()).distinct()
				.toList();
		lineas.add("Hermanos matriculados en " + d.getAnioEscolar().getAnio() + ": " + hermanos.size()
				+ (hermanos.isEmpty() ? "" : " (" + String.join(", ", hermanos) + ")"));
		lineas.add(d.getTipo().etiqueta() + ": " + d.valorTexto());
		for (Long cuotaId : d.cuotaIds()) {
			cuotas.findById(cuotaId).ifPresent(c -> {
				BigDecimal ajuste = CalculadoraDescuento.ajuste(c.getMonto(), d.getModalidad(), d.getValor());
				lineas.add(c.getDescripcion() + " (vence " + Calendario.formatear(c.getFechaVencimiento()) + "): "
						+ Dinero.formatear(c.saldo()) + " → " + Dinero.formatear(c.saldo().subtract(ajuste)));
			});
		}
		lineas.add("Se dejará de cobrar " + Dinero.formatear(d.getTotalEstimado()));
		lineas.add("Sustento: " + d.getSustento());
		return lineas;
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
