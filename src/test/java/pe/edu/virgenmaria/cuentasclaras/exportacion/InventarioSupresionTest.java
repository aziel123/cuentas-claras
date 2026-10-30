package pe.edu.virgenmaria.cuentasclaras.exportacion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.TablasExportacion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 2 (AP2-11): nada de un colegio queda fuera del inventario. Toda tabla con {@code colegio_id} de
 * {@code information_schema} está en {@code exportacion/tablas.txt} o en {@code exclusiones.txt} («no se exporta», con su
 * motivo), y en {@code scripts/fin-de-colegio/inventario.txt} o en {@code exclusiones.txt} («no se suprime»). Una tabla
 * nueva sin clasificar rompe el CI. Y al revés: cada tabla de las listas existe.
 */
@PruebaIntegracion
class InventarioSupresionTest {

	private static final Path FIN = Path.of("scripts", "fin-de-colegio");

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void todaTablaConColegioEstaClasificada() throws Exception {
		Set<String> conColegio = new TreeSet<>(jdbc.queryForList("SELECT LOWER(table_name) FROM information_schema.columns "
				+ "WHERE LOWER(column_name) = 'colegio_id' AND LOWER(table_schema) = 'public'", String.class));
		Set<String> todas = new TreeSet<>(jdbc.queryForList("SELECT LOWER(table_name) FROM information_schema.tables "
				+ "WHERE LOWER(table_schema) = 'public'", String.class));
		assertThat(conColegio).contains("alumno", "pago", "evento_auditoria", "exportacion_colegio", "acta_supresion");

		Set<String> exportadas = TablasExportacion.cargar().stream().map(TablasExportacion.Tabla::nombre)
				.collect(Collectors.toCollection(TreeSet::new));
		Set<String> inventario = tablas(Files.readAllLines(FIN.resolve("inventario.txt"), StandardCharsets.UTF_8), null);
		List<String> exclusiones = Files.readAllLines(FIN.resolve("exclusiones.txt"), StandardCharsets.UTF_8);
		Set<String> noSeExportan = tablas(exclusiones, "no se exporta");
		Set<String> noSeSuprimen = tablas(exclusiones, "no se suprime");

		for (String tabla : conColegio) {
			assertThat(exportadas.contains(tabla) || noSeExportan.contains(tabla))
					.as("«%s» tiene colegio_id: va en exportacion/tablas.txt o en exclusiones.txt (no se exporta)", tabla)
					.isTrue();
			assertThat(inventario.contains(tabla) || noSeSuprimen.contains(tabla))
					.as("«%s» tiene colegio_id: va en inventario.txt o en exclusiones.txt (no se suprime)", tabla).isTrue();
			assertThat(exportadas.contains(tabla) && noSeExportan.contains(tabla)).as("«%s» en dos listas", tabla).isFalse();
			assertThat(inventario.contains(tabla) && noSeSuprimen.contains(tabla)).as("«%s» en dos listas", tabla).isFalse();
		}
		assertThat(todas).as("las tablas de las listas existen").containsAll(exportadas).containsAll(inventario)
				.containsAll(noSeExportan).containsAll(noSeSuprimen);
		// La bitácora no se borra (se tacha) y las actas tampoco.
		assertThat(inventario).doesNotContain("evento_auditoria", "acta_supresion", "huella_bitacora");
	}

	/** Las tablas de un archivo «tabla | …»; con {@code tipo}, solo las líneas cuya segunda columna es esa. */
	private static Set<String> tablas(List<String> lineas, String tipo) {
		Set<String> tablas = new TreeSet<>();
		for (String linea : lineas) {
			String l = linea.strip();
			if (l.isEmpty() || l.startsWith("#")) {
				continue;
			}
			String[] partes = l.split("\\|");
			if (tipo == null || (partes.length > 1 && partes[1].strip().equals(tipo))) {
				tablas.add(partes[0].strip().toLowerCase(Locale.ROOT));
			}
		}
		return tablas;
	}
}
