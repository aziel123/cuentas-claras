package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Correcciones del sprint 4 (S4-A1): la muestra fija de una confirmación a ciegas nunca cubre todo el archivo. Puro. */
class MuestraAlAzarTest {

	@Test
	void conUnaLineaNoHayMuestraYConDosSoloUna() {
		assertThat(MuestraAlAzar.elegir(0, 3)).isEmpty();
		assertThat(MuestraAlAzar.elegir(1, 3)).isEmpty();
		assertThat(MuestraAlAzar.numeros(MuestraAlAzar.elegir(2, 3))).hasSize(1);
		assertThat(MuestraAlAzar.numeros(MuestraAlAzar.elegir(3, 3))).hasSize(2);
		assertThat(MuestraAlAzar.numeros(MuestraAlAzar.elegir(4, 3))).hasSize(3);
	}

	@RepeatedTest(20)
	void sonNumerosDistintosOrdenadosYDentroDelArchivo() {
		List<Integer> numeros = MuestraAlAzar.numeros(MuestraAlAzar.elegir(3000, 10));
		assertThat(numeros).hasSize(10).isSorted().doesNotHaveDuplicates().allMatch(n -> n >= 1 && n <= 3000);
		assertThat(MuestraAlAzar.elegir(3000, 10)).matches("^[1-9][0-9]*(,[1-9][0-9]*)*$").hasSizeLessThanOrEqualTo(100);
	}

	@Test
	void elAzarNoSeRepiteNiDependeDeLaFecha() {
		Set<String> muestras = new HashSet<>();
		Set<Long> semillas = new HashSet<>();
		for (int i = 0; i < 30; i++) {
			muestras.add(MuestraAlAzar.elegir(50, 3));
			semillas.add(MuestraAlAzar.semilla());
		}
		assertThat(muestras).hasSizeGreaterThan(20);
		assertThat(semillas).hasSize(30);
	}
}
