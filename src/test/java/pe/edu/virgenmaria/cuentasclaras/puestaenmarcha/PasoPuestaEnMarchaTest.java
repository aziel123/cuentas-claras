package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.model.PasoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 1b (sección 3.14): el catálogo de pasos. Mismas claves y orden que {@link EstadoPuestaEnMarcha#PASOS};
 * toda ruta existe en {@link ModuloApp} y la puede abrir el rol que hace el paso (si una pantalla cambia de ruta, el botón
 * del paso apuntaría mal: esta prueba lo detecta); las dependencias no forman ciclos.
 */
class PasoPuestaEnMarchaTest {

	@Test
	void lasClavesYElOrdenSonLosDeLaLista() {
		assertThat(Arrays.stream(PasoPuestaEnMarcha.values()).map(PasoPuestaEnMarcha::clave).toList())
				.isEqualTo(EstadoPuestaEnMarcha.PASOS);
		assertThat(Arrays.stream(PasoPuestaEnMarcha.values()).map(PasoPuestaEnMarcha::numero).toList())
				.isSorted().doesNotHaveDuplicates();
	}

	@Test
	void sonQuincePasosDeLosQueTreceSonObligatorios() {
		assertThat(Arrays.stream(PasoPuestaEnMarcha.values()).filter(p -> p.tipo() != PasoPuestaEnMarcha.Tipo.AVISO))
				.hasSize(PasoPuestaEnMarcha.TOTAL);
		assertThat(Arrays.stream(PasoPuestaEnMarcha.values()).filter(PasoPuestaEnMarcha::obligatorio))
				.hasSize(PasoPuestaEnMarcha.OBLIGATORIOS);
		assertThat(Arrays.stream(PasoPuestaEnMarcha.values()).filter(PasoPuestaEnMarcha::opcional))
				.containsExactly(PasoPuestaEnMarcha.PASARELA, PasoPuestaEnMarcha.RECAUDACION);
	}

	@Test
	void cadaRutaLaPuedeAbrirQuienHaceElPaso() {
		for (PasoPuestaEnMarcha paso : PasoPuestaEnMarcha.values()) {
			if (!paso.loHaceElColegio()) {
				assertThat(paso.ruta()).as("%s lo hace la plataforma: sin botón", paso).isNull();
				assertThat(paso.accion(2026)).as(paso.name()).isNull();
				continue;
			}
			assertThat(paso.roles()).as("%s: quién lo hace", paso).isNotEmpty();
			for (Rol rol : paso.roles()) {
				assertThat(ModuloApp.puedeEntrar(EnumSet.of(rol), paso.ruta())).as("%s: %s abre %s", paso, rol, paso.ruta())
						.isTrue();
			}
			assertThat(paso.accion(2026)).as(paso.name()).doesNotContain("{año}");
			assertThat(paso.accion(null)).as(paso.name()).doesNotContain("{año}").doesNotContain("del año");
		}
	}

	@Test
	void lasDependenciasNoFormanCiclos() {
		for (PasoPuestaEnMarcha paso : PasoPuestaEnMarcha.values()) {
			assertThat(sinCiclo(paso, new HashSet<>())).as(paso.name()).isTrue();
			assertThat(paso.dependeDe()).as("%s depende de pasos anteriores", paso)
					.allMatch(d -> d.numero() < paso.numero());
		}
	}

	@Test
	void laPromotoriaNoPideAprobacionEnLosTextosYDireccionNoApareceComoQuienLeAprueba() {
		// Tanda 1c: lo que hace la Promotoría se aplica directo; «aprueba Dirección» ya no describe un paso de Promotoría.
		for (PasoPuestaEnMarcha paso : PasoPuestaEnMarcha.values()) {
			assertThat(paso.responsable()).as(paso.name()).doesNotContain("aprueba Dirección");
		}
		assertThat(PasoPuestaEnMarcha.EQUIPO.titulo()).isEqualTo("Equipo del colegio").doesNotContain("segregación");
	}

	@Test
	void puedeEntrarUsaLaPrimeraReglaQueCoincide() {
		// /colegio/marca es solo de Promotoría aunque /colegio/** también sea de Dirección (la primera regla gana).
		assertThat(ModuloApp.puedeEntrar(EnumSet.of(Rol.PROMOTOR), "/colegio/marca")).isTrue();
		assertThat(ModuloApp.puedeEntrar(EnumSet.of(Rol.DIRECTOR), "/colegio/marca")).isFalse();
		assertThat(ModuloApp.puedeEntrar(EnumSet.of(Rol.DIRECTOR), "/colegio/anios/7")).isTrue();
		assertThat(ModuloApp.puedeEntrar(EnumSet.of(Rol.CAJA), "/puesta-en-marcha")).isFalse();
		assertThat(ModuloApp.puedeEntrar(EnumSet.of(Rol.ADMINISTRACION), "/pensiones?anio=3")).isTrue();
		assertThat(ModuloApp.puedeEntrar(EnumSet.of(Rol.PROMOTOR), "/no-existe")).isFalse();
		assertThat(ModuloApp.puedeEntrar(Set.of(), "/inicio")).isFalse();
	}

	private static boolean sinCiclo(PasoPuestaEnMarcha paso, Set<PasoPuestaEnMarcha> visitados) {
		if (!visitados.add(paso)) {
			return false;
		}
		for (PasoPuestaEnMarcha d : paso.dependeDe()) {
			if (!sinCiclo(d, new HashSet<>(visitados))) {
				return false;
			}
		}
		return true;
	}
}
