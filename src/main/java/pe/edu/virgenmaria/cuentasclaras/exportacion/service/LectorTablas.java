package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Sprint 11, tanda 2 (sección 3.9, AP-12): la lectura en flujo de las tablas de la exportación. Es la excepción a la regla
 * «solo el verificador usa JDBC» (ArchUnit): una exportación completa recorre ~90 tablas sin entidades para todas. CADA
 * consulta filtra por el colegio de forma explícita (el {@code WHERE} de {@link TablasExportacion.Tabla}), con el id como
 * parámetro; los nombres de tabla y columna vienen de la lista fija y se validan. Corre con {@code sistema.exportacion}
 * ({@code cc_sistema}), en lectura.
 */
@Component
public class LectorTablas {

	private static final int LOTE = 500;

	private final JdbcTemplate jdbc;

	public LectorTablas(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Las columnas exportables de una tabla, en su orden (sin binarias ni las de {@code sinColumnas} ni {@code NUNCA}). */
	public List<String> columnas(TablasExportacion.Tabla tabla) {
		exigirNombre(tabla.nombre());
		return jdbc.query("SELECT * FROM " + tabla.nombre() + " WHERE 1 = 0", r -> {
			ResultSetMetaData meta = r.getMetaData();
			List<String> columnas = new ArrayList<>();
			for (int i = 1; i <= meta.getColumnCount(); i++) {
				String nombre = meta.getColumnLabel(i).toLowerCase(java.util.Locale.ROOT);
				int tipo = meta.getColumnType(i);
				boolean binaria = tipo == Types.BLOB || tipo == Types.BINARY || tipo == Types.VARBINARY
						|| tipo == Types.LONGVARBINARY;
				if (!binaria && !tabla.sinColumnas().contains(nombre) && !TablasExportacion.NUNCA.contains(nombre)) {
					columnas.add(nombre);
				}
			}
			return columnas;
		});
	}

	/** Recorre las filas de la tabla del colegio, en flujo, en el orden de {@code id} si la tabla lo tiene. */
	public void recorrer(TablasExportacion.Tabla tabla, List<String> columnas, long colegioId, Consumer<List<Object>> fila) {
		exigirNombre(tabla.nombre());
		columnas.forEach(LectorTablas::exigirNombre);
		String orden = columnas.contains("id") ? " ORDER BY id" : "";
		jdbc.query(con -> {
			var sentencia = con.prepareStatement("SELECT " + String.join(", ", columnas) + " FROM " + tabla.nombre()
					+ " WHERE " + tabla.where() + orden, java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
			sentencia.setFetchSize(LOTE);
			sentencia.setLong(1, colegioId);
			return sentencia;
		}, (org.springframework.jdbc.core.RowCallbackHandler) r -> {
			List<Object> valores = new ArrayList<>(columnas.size());
			for (int i = 1; i <= columnas.size(); i++) {
				valores.add(r.getObject(i));
			}
			fila.accept(valores);
		});
	}

	/** Los PDF de los comunicados del colegio (id y contenido), en flujo. */
	public void adjuntos(long colegioId, BiConsumer<Long, byte[]> adjunto) {
		jdbc.query("SELECT id, contenido FROM comunicado_adjunto WHERE colegio_id = ? ORDER BY id",
				(org.springframework.jdbc.core.RowCallbackHandler) r -> adjunto.accept(r.getLong(1), r.getBytes(2)), colegioId);
	}

	/** Dónde está una exportación por la huella de su enlace (la entrega es pública: el colegio sale de aquí). */
	public Optional<Map<String, Object>> exportacionPorToken(String tokenHash) {
		return jdbc.queryForList("SELECT id, colegio_id FROM exportacion_colegio WHERE token_hash = ?", tokenHash).stream()
				.findFirst();
	}

	/** Las tablas del colegio con filas, para la constancia (conteo por tabla). */
	public long contar(TablasExportacion.Tabla tabla, long colegioId) {
		exigirNombre(tabla.nombre());
		Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla.nombre() + " WHERE " + tabla.where(), Long.class,
				colegioId);
		return n == null ? 0 : n;
	}

	private static void exigirNombre(String nombre) {
		if (!TablasExportacion.NOMBRE.matcher(nombre).matches()) {
			throw new IllegalArgumentException("Nombre inválido: " + nombre);
		}
	}
}
