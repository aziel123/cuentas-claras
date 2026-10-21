package pe.edu.virgenmaria.cuentasclaras.comun.graficos;

import java.util.List;

/**
 * Puerto: el tablero del inicio de Dirección (lo académico, sus aprobaciones y un resumen de cobranza SIN detalle por
 * alumno). Lo implementa el panel con los puertos de académico, cobranza y la bandeja; solo DIRECTOR.
 */
public interface TableroDireccion {

	Tablero tablero();

	/**
	 * {@code asistencia}: la del día, o {@code null} si hoy nadie registró. {@code seccionesSinDocente}: sus nombres,
	 * para actuar («3.° Primaria A»).
	 */
	record Tablero(List<Kpi> kpis, BarraApilada asistencia, String asistenciaVacia, List<String> seccionesSinDocente,
			BarrasHorizontales morosidad) {

		public Tablero {
			kpis = List.copyOf(kpis);
			seccionesSinDocente = List.copyOf(seccionesSinDocente);
		}
	}
}
