package pe.edu.virgenmaria.cuentasclaras.familias.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.SesionApoderado;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AnulacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AplicacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.OrigenPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAplicacion;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AnulacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoVisibleCuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.DescuentoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.EstadoCuentaFamilia;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioRenovacionFamilia;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Estado de cuenta y comprobantes de la familia en el portal (sprint 5, pantallas 2 y 3). La familia sale SIEMPRE de la
 * cuenta en sesión. Muestra todo, también lo anulado y lo descontado, con el ROL de quien lo aprobó (nunca su usuario):
 * si alguien inventa una deuda o hace desaparecer un pago, la familia lo ve.
 */
@Service
@PreAuthorize("hasRole('APODERADO')")
@Transactional(readOnly = true)
public class ConsultaEstadoCuentaFamilia {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final SesionApoderado sesion;

	private final AlumnoRepository alumnos;

	private final CuotaRepository cuotas;

	private final PagoRepository pagos;

	private final AplicacionPagoRepository aplicaciones;

	private final AnulacionPagoRepository anulaciones;

	private final DescuentoRepository descuentos;

	private final RolesDeQuienAprobo roles;

	private final Clock reloj;

	public ConsultaEstadoCuentaFamilia(SesionApoderado sesion, AlumnoRepository alumnos, CuotaRepository cuotas,
			PagoRepository pagos, AplicacionPagoRepository aplicaciones, AnulacionPagoRepository anulaciones,
			DescuentoRepository descuentos, RolesDeQuienAprobo roles, Clock reloj) {
		this.sesion = sesion;
		this.alumnos = alumnos;
		this.cuotas = cuotas;
		this.pagos = pagos;
		this.aplicaciones = aplicaciones;
		this.anulaciones = anulaciones;
		this.descuentos = descuentos;
		this.roles = roles;
		this.reloj = reloj;
	}

	public EstadoCuentaFamilia deMiFamilia() {
		Apoderado apoderado = sesion.apoderado();
		Long familiaId = apoderado.getFamilia().getId();
		LocalDate hoy = LocalDate.now(reloj);
		List<Pago> suyos = pagos.findByFamiliaIdOrderByIdDesc(familiaId);
		Map<Long, AnulacionPago> anuladas = suyos.isEmpty() ? Map.of()
				: anulaciones.findByPagoIdIn(suyos.stream().map(Pago::getId).toList()).stream()
						.collect(Collectors.toMap(a -> a.getPago().getId(), Function.identity(), (a, b) -> a));
		// Qué pagos vigentes tocaron cada cuota (para «B001-00000231 · 05/10/2026 · Yape · en caja»).
		Map<Long, List<String>> pagadaCon = new HashMap<>();
		if (!suyos.isEmpty()) {
			for (AplicacionPago a : aplicaciones.dePagos(suyos.stream().map(Pago::getId).toList())) {
				if (a.getTipo() == TipoAplicacion.APLICACION && a.getPago().vigente()) {
					pagadaCon.computeIfAbsent(a.getCuota().getId(), k -> new ArrayList<>()).add(resumen(a.getPago()));
				}
			}
		}
		List<EstadoCuentaFamilia.Hijo> hijos = new ArrayList<>();
		List<Cuota> todas = new ArrayList<>();
		for (Alumno alumno : alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(familiaId)) {
			List<Cuota> delAlumno = cuotas.findByAlumnoIdOrderByFechaVencimientoAscIdAsc(alumno.getId());
			if (delAlumno.isEmpty() && !alumno.activo()) {
				continue;
			}
			todas.addAll(delAlumno);
			Map<Integer, List<Cuota>> porAnio = new TreeMap<>(Comparator.reverseOrder());
			delAlumno.forEach(c -> porAnio.computeIfAbsent(c.getAnioEscolar().getAnio(), k -> new ArrayList<>()).add(c));
			List<EstadoCuentaFamilia.Anio> anios = new ArrayList<>();
			porAnio.forEach((anio, lista) -> anios.add(new EstadoCuentaFamilia.Anio(anio,
					lista.stream().map(c -> fila(c, hoy, pagadaCon)).toList(),
					Dinero.sumar(lista.stream().filter(c -> !c.anulada()).map(Cuota::saldo).toList()))));
			hijos.add(new EstadoCuentaFamilia.Hijo(alumno.getId(),
					ServicioRenovacionFamilia.nombreDePila(alumno.getNombres()) + " " + alumno.getApellidoPaterno(), anios));
		}
		List<EstadoCuentaFamilia.PagoFila> filasPago = suyos.stream().map(p -> {
			AnulacionPago anulacion = anuladas.get(p.getId());
			return new EstadoCuentaFamilia.PagoFila(p.getComprobante().getId(), p.getComprobante().numeroCompleto(),
					p.getFecha(), p.getMedio().etiqueta(), registradoPor(p), p.getTotal(), p.vigente(),
					anulacion == null ? null : "Anulado. Motivo: " + anulacion.getMotivo() + ". Aprobó: "
							+ roles.de(anulacion.getAprobadoPor()));
		}).toList();
		Map<Long, String> nombres = new LinkedHashMap<>();
		hijos.forEach(h -> nombres.put(h.alumnoId(), h.nombre()));
		List<EstadoCuentaFamilia.DescuentoFila> filasDescuento = nombres.keySet().stream()
				.flatMap(id -> descuentos.findByAlumnoIdOrderByIdDesc(id).stream())
				.filter(d -> d.getEstado() == EstadoDescuento.APROBADO)
				.map(d -> new EstadoCuentaFamilia.DescuentoFila(nombres.get(d.getAlumno().getId()), d.getTipo().etiqueta(),
						d.valorTexto(), d.getTotalEstimado(), d.getEstado().etiqueta(), d.getEstado().variante(),
						roles.de(d.getResueltoPor())))
				.toList();
		List<Cuota> vigentes = todas.stream().filter(c -> !c.anulada()).toList();
		return new EstadoCuentaFamilia(apoderado.getFamilia().getNombre(),
				Dinero.sumar(vigentes.stream().map(Cuota::saldo).toList()),
				Dinero.sumar(vigentes.stream().filter(c -> c.vencidaAl(hoy)).map(Cuota::saldo).toList()), hijos,
				filasPago, filasDescuento);
	}

	/** Los comprobantes de la familia (pantalla 3): los mismos pagos, del más reciente al más antiguo. */
	public List<EstadoCuentaFamilia.PagoFila> comprobantes() {
		return deMiFamilia().pagos();
	}

	private EstadoCuentaFamilia.CuotaFila fila(Cuota c, LocalDate hoy, Map<Long, List<String>> pagadaCon) {
		EstadoVisibleCuota estado = c.estadoAl(hoy);
		String anulacion = c.anulada() ? "Anulada. Motivo: " + c.getAnulacionMotivo() + ". Aprobó: "
				+ roles.de(c.getAnulacionAprobadaPor()) : null;
		return new EstadoCuentaFamilia.CuotaFila(c.getId(), c.getDescripcion(), c.getFechaVencimiento(), c.getMonto(),
				c.getMontoDescuento(), c.getMontoPagado(), c.saldo(), estado.etiqueta(), estado.variante(),
				pagadaCon.getOrDefault(c.getId(), List.of()), anulacion);
	}

	private static String resumen(Pago p) {
		return p.getComprobante().numeroCompleto() + " · " + p.getFecha().format(FECHA) + " · " + p.getMedio().etiqueta()
				+ " · " + registradoPor(p);
	}

	/** Quién lo registró, sin nombres: en caja, pago en línea o banco. */
	static String registradoPor(Pago p) {
		return p.getOrigen() == OrigenPago.PASARELA ? "pago en línea"
				: p.getOrigen() == OrigenPago.RECAUDACION ? "banco" : "en caja";
	}
}
