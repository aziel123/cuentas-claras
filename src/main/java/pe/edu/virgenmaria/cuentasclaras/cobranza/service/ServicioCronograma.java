package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CabeceraAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.CronogramaAlumno;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.CuotaVista;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PendienteNivel;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.PendientesGeneracion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoVisibleCuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.LineaSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.LoteSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.LineaSaldoInicialRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.PlanPensionRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lectura de cuotas: el cronograma de un alumno (con VENCIDA calculada con la fecha de Lima) y las matrículas que
 * aún no tienen cronograma.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
public class ServicioCronograma {

	private final CuotaRepository cuotas;

	private final PlanPensionRepository planes;

	private final LineaSaldoInicialRepository lineas;

	private final AnioEscolarRepository anios;

	private final ServicioAlumnos alumnos;

	private final Clock reloj;

	public ServicioCronograma(CuotaRepository cuotas, PlanPensionRepository planes, LineaSaldoInicialRepository lineas,
			AnioEscolarRepository anios, ServicioAlumnos alumnos, Clock reloj) {
		this.cuotas = cuotas;
		this.planes = planes;
		this.lineas = lineas;
		this.anios = anios;
		this.alumnos = alumnos;
		this.reloj = reloj;
	}

	public CronogramaAlumno deAlumno(Long alumnoId) {
		CabeceraAlumno cabecera = alumnos.cabecera(alumnoId);
		LocalDate hoy = LocalDate.now(reloj);
		List<Cuota> delAlumno = cuotas.findByAlumnoIdOrderByFechaVencimientoAscIdAsc(alumnoId);
		Map<Long, String> origenPlan = new HashMap<>();
		delAlumno.stream().map(Cuota::getPlanPensionId).filter(Objects::nonNull).distinct()
				.forEach(id -> origenPlan.put(id, planes.findById(id).map(PlanPension::nombre).orElse("Plan")));
		List<Long> idsLineas = delAlumno.stream().map(Cuota::getLineaSaldoInicialId).filter(Objects::nonNull).toList();
		Map<Long, LineaSaldoInicial> origenSaldo = idsLineas.isEmpty() ? Map.of()
				: lineas.conLote(idsLineas).stream().collect(Collectors.toMap(LineaSaldoInicial::getId, Function.identity()));
		List<CuotaVista> vistas = new ArrayList<>();
		BigDecimal total = Dinero.CERO;
		BigDecimal pagado = Dinero.CERO;
		BigDecimal saldo = Dinero.CERO;
		BigDecimal vencido = Dinero.CERO;
		for (Cuota c : delAlumno) {
			EstadoVisibleCuota estado = c.estadoAl(hoy);
			String origen = c.getPlanPensionId() != null ? origenPlan.get(c.getPlanPensionId())
					: origenSaldo(origenSaldo.get(c.getLineaSaldoInicialId()));
			vistas.add(new CuotaVista(c.getId(), c.getDescripcion(), c.getTipo().etiqueta(), c.getFechaVencimiento(),
					c.getMonto(), c.getMontoDescuento(), c.getMontoPagado(), c.saldo(), estado, origen,
					c.anulacionPendiente()));
			if (!c.anulada()) {
				total = total.add(c.getMonto());
				pagado = pagado.add(c.getMontoPagado());
				saldo = saldo.add(c.saldo());
				if (estado == EstadoVisibleCuota.VENCIDA) {
					vencido = vencido.add(c.saldo());
				}
			}
		}
		return new CronogramaAlumno(cabecera, vistas, total, pagado, saldo, vencido, hoy);
	}

	/** Matrículas activas sin cronograma, por nivel, y si su nivel ya tiene plan aprobado (entonces se generan). */
	public PendientesGeneracion pendientes(Long anioId) {
		List<AnioEscolar> todos = anios.findAllByOrderByAnioDesc();
		Optional<AnioEscolar> elegido = anioId != null
				? Optional.of(anios.findById(anioId).orElseThrow(() -> new RecursoNoEncontradoException("Año escolar no encontrado")))
				: todos.stream().filter(AnioEscolar::enCurso).findFirst().or(() -> todos.stream().findFirst());
		List<AnioOpcion> opciones = todos.stream().map(ServicioPlanesPension::opcion).toList();
		boolean puedeGenerar = SesionActual.tieneAlgunRol("DIRECTOR", "ADMINISTRACION");
		if (elegido.isEmpty()) {
			return new PendientesGeneracion(null, opciones, List.of(), 0, 0, false);
		}
		AnioEscolar anio = elegido.get();
		List<PendienteNivel> niveles = new ArrayList<>();
		long total = 0;
		long generables = 0;
		for (Nivel nivel : Nivel.values()) {
			long cantidad = cuotas.matriculasSinCronograma(anio.getId(), ServicioPlanesPension.grados(nivel)).size();
			String plan = planes.findByAnioEscolarIdAndNivelAndVigenteTrue(anio.getId(), nivel).map(PlanPension::nombre)
					.orElse(null);
			niveles.add(new PendienteNivel(nivel.name(), nivel.etiqueta(), plan, cantidad));
			total += cantidad;
			generables += plan == null ? 0 : cantidad;
		}
		return new PendientesGeneracion(ServicioPlanesPension.opcion(anio), opciones, niveles, total, generables,
				puedeGenerar && !anio.cerrado());
	}

	/** «Saldo inicial · lote 3, confirmado por director el 05/10/2026». */
	private static String origenSaldo(LineaSaldoInicial linea) {
		if (linea == null) {
			return "Saldo inicial";
		}
		LoteSaldoInicial lote = linea.getLote();
		return "Saldo inicial · lote " + lote.getId() + (lote.getConfirmadoPor() == null ? ""
				: ", confirmado por " + lote.getConfirmadoPor() + " el "
						+ Calendario.formatear(lote.getConfirmadoEn().toLocalDate()));
	}
}
