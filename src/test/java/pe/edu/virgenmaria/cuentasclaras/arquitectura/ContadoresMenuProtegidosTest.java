package pe.edu.virgenmaria.cuentasclaras.arquitectura;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.web.ContadorMenu;
import pe.edu.virgenmaria.cuentasclaras.seguridad.web.FuenteContador;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoría del PR #19 (hallazgo 1): cada contador del menú declara con {@link FuenteContador} el método de servicio que
 * lo alimenta, y ese método exige {@code @PreAuthorize} con exactamente los roles de su módulo en {@link ModuloApp}.
 * Así el número junto a una opción nunca se calcula para un rol que no puede abrirla.
 */
class ContadoresMenuProtegidosTest {

	private static final Pattern ROLES = Pattern.compile("^hasAnyRole\\(((?:'[A-Z_]+',?)+)\\)$|^hasRole\\('([A-Z_]+)'\\)$");

	@Test
	void cadaContadorTieneSuFuenteProtegidaConLosRolesDeSuModulo() throws Exception {
		List<JavaClass> contadores = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("pe.edu.virgenmaria.cuentasclaras").stream()
				.filter(c -> c.isAssignableTo(ContadorMenu.class) && !c.isInterface()).toList();
		assertThat(contadores).as("hay al menos un contador del menú").isNotEmpty();

		for (JavaClass clase : contadores) {
			Class<?> tipo = clase.reflect();
			FuenteContador fuente = tipo.getAnnotation(FuenteContador.class);
			assertThat(fuente).as(clase.getName() + " declara @FuenteContador").isNotNull();
			Method metodo = fuente.servicio().getMethod(fuente.metodo());
			assertThat(Modifier.isPublic(metodo.getModifiers())).isTrue();
			PreAuthorize regla = metodo.getAnnotation(PreAuthorize.class);
			assertThat(regla).as(fuente.servicio().getSimpleName() + "#" + fuente.metodo() + " lleva @PreAuthorize")
					.isNotNull();

			ModuloApp modulo = modulo(tipo);
			assertThat(roles(regla.value())).as("roles de " + fuente.metodo() + " = roles de " + modulo)
					.isEqualTo(modulo.roles().stream().map(Rol::name).collect(Collectors.toSet()));
		}
	}

	/** El módulo que devuelve el contador, sin crear el bean: se lee de una instancia sin dependencias. */
	private static ModuloApp modulo(Class<?> tipo) throws Exception {
		var constructor = tipo.getDeclaredConstructors()[0];
		Object[] nulos = new Object[constructor.getParameterCount()];
		return ((ContadorMenu) constructor.newInstance(nulos)).modulo();
	}

	private static Set<String> roles(String expresion) {
		Matcher m = ROLES.matcher(expresion.replace(" ", ""));
		assertThat(m.matches()).as("expresión simple de roles: " + expresion).isTrue();
		String lista = m.group(1) != null ? m.group(1) : "'" + m.group(2) + "'";
		return Arrays.stream(lista.split(",")).map(r -> r.replace("'", "")).collect(Collectors.toSet());
	}
}
