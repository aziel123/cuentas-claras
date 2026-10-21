package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsignacionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsignacionesVista;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsistenciaDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionConAsignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.service.AsistenciaDireccion;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MorosidadGrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Porcentaje;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.BarraApilada;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.Geometria;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.Kpi;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.Kpi.Tendencia;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.TableroDireccion;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Plural;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Rediseño de la interfaz: el tablero del inicio de Dirección. Lo académico (secciones, docentes asignados y las secciones
 * sin docente), la asistencia del día en números, sus aprobaciones pendientes y la morosidad por NIVEL (sin detalle por
 * alumno ni por sección). Sin consultas propias: usa los servicios de académico, cobranza y la bandeja, que filtra
 * {@code @TenantId} por el colegio en sesión. Solo DIRECTOR.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('DIRECTOR')")
public class GraficosDireccion implements TableroDireccion {

	private final Asignaciones asignaciones;

	private final AsistenciaDireccion asistencia;

	private final CifrasCobranza cobranza;

	private final BandejaAprobaciones bandeja;

	private final Clock reloj;

	public GraficosDireccion(Asignaciones asignaciones, AsistenciaDireccion asistencia, CifrasCobranza cobranza,
			BandejaAprobaciones bandeja, Clock reloj) {
		this.asignaciones = asignaciones;
		this.asistencia = asistencia;
		this.cobranza = cobranza;
		this.bandeja = bandeja;
		this.reloj = reloj;
	}

	@Override
	public Tablero tablero() {
		LocalDate hoy = LocalDate.now(reloj);
		AsignacionesVista vista = asignaciones.vista(null);
		List<SeccionConAsignaciones> activas = vista.secciones().stream().filter(SeccionConAsignaciones::activa).toList();
		List<String> sinDocente = activas.stream()
				.filter(s -> s.asignaciones().stream().noneMatch(AsignacionVista::vigente))
				.map(SeccionConAsignaciones::seccion).toList();
		long docentes = activas.stream().flatMap(s -> s.asignaciones().stream()).filter(AsignacionVista::vigente)
				.map(AsignacionVista::docenteUsuario).distinct().count();
		long pendientes = bandeja.contarPorResolver();
		AsistenciaDelDia delDia = asistencia.resumenDelDia();
		Integer anio = cobranza.anio(null).map(AnioOpcion::anio).orElse(null);
		List<MorosidadGrado> morosidad = cobranza.anio(null).map(a -> cobranza.morosidadPorGrado(a.id(), hoy))
				.orElse(List.of());

		List<Kpi> kpis = new ArrayList<>();
		kpis.add(new Kpi("Secciones" + (vista.anio() == null ? "" : " " + vista.anio()), String.valueOf(activas.size()),
				activas.isEmpty() ? "Aún no hay secciones activas" : Plural.de(activas.size() - sinDocente.size(),
						"sección con docente", "secciones con docente"), Tendencia.NINGUNA));
		kpis.add(new Kpi("Docentes asignados", String.valueOf(docentes),
				docentes == 0 ? "Nadie enseña aún en una sección" : "Con al menos una sección vigente", Tendencia.NINGUNA,
				null, "/academico/asignaciones", "Ver docentes y secciones"));
		kpis.add(new Kpi("Secciones sin docente", String.valueOf(sinDocente.size()),
				sinDocente.isEmpty() ? "Todas las secciones tienen docente" : "Asígnales docente para que registren la "
						+ "asistencia", Tendencia.NINGUNA));
		kpis.add(new Kpi("Pendientes por aprobar", String.valueOf(pendientes),
				pendientes == 0 ? "Nada espera tu aprobación"
						: Plural.de(pendientes, "solicitud espera", "solicitudes esperan") + " tu decisión",
				Tendencia.NINGUNA, null, "/aprobaciones", "Ir a Aprobaciones"));
		kpis.add(GraficosCobranza.kpiMorosidad(morosidad));

		return new Tablero(kpis, delDia.total() == 0 ? null : asistencia(delDia),
				"Hoy (" + Calendario.formatear(hoy) + ") todavía no se registró la asistencia en ninguna sección. Los docentes "
						+ "la registran desde su inicio; aparecerá aquí en cuanto lo hagan.",
				sinDocente, GraficosCobranza.morosidadPorNivel(morosidad, anio));
	}

	/** La asistencia del día como barra apilada, en un orden fijo: presentes, tarde, faltas y justificadas. */
	static BarraApilada asistencia(AsistenciaDelDia d) {
		long[] cantidades = { d.presentes(), d.tarde(), d.faltas(), d.justificadas() };
		String[] etiquetas = { "Presentes", "Llegaron tarde", "Faltas", "Faltas justificadas" };
		BigDecimal total = BigDecimal.valueOf(d.total());
		List<BarraApilada.Segmento> segmentos = new ArrayList<>();
		BigDecimal x = BigDecimal.ZERO;
		for (int i = 0; i < cantidades.length; i++) {
			BigDecimal ancho = new BigDecimal(Geometria.porcentaje(BigDecimal.valueOf(cantidades[i]), total));
			OptionalLong p = Porcentaje.de(cantidades[i], d.total());
			segmentos.add(new BarraApilada.Segmento(i + 1, etiquetas[i], Geometria.numero(x), Geometria.numero(ancho),
					Plural.de(cantidades[i], "alumno", "alumnos"), p.isPresent() ? p.getAsLong() + " %" : "—",
					null));
			x = x.add(ancho);
		}
		String resumen = "Hoy se registró la asistencia de " + Plural.de(d.total(), "alumno", "alumnos") + " en "
				+ Plural.de(d.secciones(), "sección", "secciones") + ": presentes " + segmentos.get(0).porcentaje()
				+ ", tarde " + segmentos.get(1).porcentaje() + ", faltas " + segmentos.get(2).porcentaje()
				+ " y justificadas " + segmentos.get(3).porcentaje() + ".";
		return new BarraApilada("grafico-asistencia", "Asistencia del día · " + Calendario.formatear(d.fecha()), resumen,
				Plural.de(d.total(), "alumno", "alumnos"), segmentos, List.of("Estado", "Alumnos", "Parte"),
				"Hoy todavía no se registró la asistencia.");
	}
}
