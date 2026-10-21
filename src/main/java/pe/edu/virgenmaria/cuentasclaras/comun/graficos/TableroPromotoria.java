package pe.edu.virgenmaria.cuentasclaras.comun.graficos;

/**
 * Puerto: el tablero del inicio de Promotoría (cifras y gráficos de cobranza). Lo implementa el panel con los puertos de
 * caja y cobranza; su {@code @PreAuthorize} es solo PROMOTOR (los montos no los ve Caja).
 */
public interface TableroPromotoria {

	Tablero tablero();

	record Tablero(java.util.List<Kpi> kpis, BarrasVerticales diaria, BarrasHorizontales morosidad,
			BarraApilada medios) {

		public Tablero {
			kpis = java.util.List.copyOf(kpis);
		}
	}
}
