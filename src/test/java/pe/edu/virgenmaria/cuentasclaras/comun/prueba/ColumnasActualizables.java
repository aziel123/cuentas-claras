package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compara las columnas {@code updatable = true} de una entidad con el GRANT UPDATE por columna de
 * {@code scripts/mysql/02-permisos-tablas.sql}: deben coincidir EXACTAMENTE (si Hibernate actualiza una columna sin
 * GRANT, MySQL responde 1143; si el GRANT abre una columna de más, se podría cambiar por SQL).
 */
public final class ColumnasActualizables {

	private ColumnasActualizables() {
	}

	public static Set<String> de(Class<?> entidad) {
		return Stream.concat(Arrays.stream(BaseEntity.class.getDeclaredFields()), Arrays.stream(entidad.getDeclaredFields()))
				.filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isAnnotationPresent(Id.class))
				.filter(f -> !f.isAnnotationPresent(OneToMany.class))
				.filter(ColumnasActualizables::actualizable)
				.map(ColumnasActualizables::columna)
				.collect(Collectors.toCollection(TreeSet::new));
	}

	/** Columnas del {@code GRANT [INSERT, ]UPDATE (...) ON cuentasclaras.<tabla>}. */
	public static Set<String> concedidas(String script, String tabla) {
		Matcher grant = Pattern.compile("GRANT (?:INSERT, )?UPDATE \\(([^)]*)\\)\\s+ON cuentasclaras\\." + tabla + " ")
				.matcher(script);
		if (!grant.find()) {
			throw new AssertionError("Falta el GRANT UPDATE por columna sobre " + tabla);
		}
		return Arrays.stream(grant.group(1).split(",")).map(String::strip).collect(Collectors.toCollection(TreeSet::new));
	}

	private static boolean actualizable(Field campo) {
		Column columna = campo.getAnnotation(Column.class);
		JoinColumn union = campo.getAnnotation(JoinColumn.class);
		if (union != null) {
			return union.updatable();
		}
		return columna == null || columna.updatable();
	}

	private static String columna(Field campo) {
		Column columna = campo.getAnnotation(Column.class);
		if (columna != null && !columna.name().isEmpty()) {
			return columna.name();
		}
		JoinColumn union = campo.getAnnotation(JoinColumn.class);
		if (union != null && !union.name().isEmpty()) {
			return union.name();
		}
		return campo.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
	}
}
