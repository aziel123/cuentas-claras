package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglasSegregacionTest {

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "PROMOTOR", "DIRECTOR", "ADMINISTRACION" })
	void cajaNoSeCombinaConDirectorPromotorNiAdministracion(Rol otro) {
		assertThatThrownBy(() -> ReglasSegregacion.validar(EnumSet.of(Rol.CAJA, otro)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("quien cobra no aprueba");
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = "APODERADO", mode = EnumSource.Mode.EXCLUDE)
	void apoderadoNoSeCombinaConRolesDelPersonal(Rol personal) {
		assertThatThrownBy(() -> ReglasSegregacion.validar(EnumSet.of(Rol.APODERADO, personal)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Apoderado");
	}

	@ParameterizedTest
	@MethodSource("combinacionesPermitidas")
	void combinacionesPermitidasSeAceptan(Set<Rol> roles) {
		assertThatCode(() -> ReglasSegregacion.validar(roles)).doesNotThrowAnyException();
	}

	static Stream<Set<Rol>> combinacionesPermitidas() {
		return Stream.concat(
				EnumSet.allOf(Rol.class).stream().map(EnumSet::of),
				Stream.of(
						EnumSet.of(Rol.PROMOTOR, Rol.DIRECTOR),
						EnumSet.of(Rol.DIRECTOR, Rol.DOCENTE),
						EnumSet.of(Rol.CAJA, Rol.DOCENTE)));
	}

	@Test
	void direccionNoSeCombinaConAdministracion() {
		assertThatThrownBy(() -> ReglasSegregacion.validar(EnumSet.of(Rol.DIRECTOR, Rol.ADMINISTRACION)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("quien registra descuentos no los aprueba");
	}

	@Test
	void sinRolesEsRechazado() {
		assertThatThrownBy(() -> ReglasSegregacion.validar(EnumSet.noneOf(Rol.class)))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> ReglasSegregacion.validar(null))
				.isInstanceOf(ReglaNegocioException.class);
	}
}
