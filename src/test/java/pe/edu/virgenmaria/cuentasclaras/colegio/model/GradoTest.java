package pe.edu.virgenmaria.cuentasclaras.colegio.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Catálogo nacional de grados (EBR). */
class GradoTest {

	@Test
	void cadaGradoPerteneceASuNivel() {
		assertThat(Grado.values()).hasSize(14);
		assertThat(Arrays.stream(Grado.values()).filter(g -> g.nivel() == Nivel.INICIAL)).hasSize(3);
		assertThat(Arrays.stream(Grado.values()).filter(g -> g.nivel() == Nivel.PRIMARIA)).hasSize(6);
		assertThat(Arrays.stream(Grado.values()).filter(g -> g.nivel() == Nivel.SECUNDARIA)).hasSize(5);
		for (Grado grado : Grado.values()) {
			assertThat(grado.name()).startsWith(grado.nivel().name() + "_");
			assertThat(Grado.de(grado.nivel(), grado.numero())).contains(grado);
		}
		assertThat(Grado.INICIAL_3.etiqueta()).isEqualTo("Inicial 3 años");
		assertThat(Grado.PRIMARIA_5.etiqueta()).isEqualTo("5.° Primaria");
		assertThat(Grado.SECUNDARIA_1.edadNormativa()).isEqualTo(12);
		assertThat(Grado.de(Nivel.PRIMARIA, 7)).isEmpty();
	}

	@Test
	void siguienteDeInicial5EsPrimaria1() {
		assertThat(Grado.INICIAL_5.siguiente()).contains(Grado.PRIMARIA_1);
		assertThat(Grado.INICIAL_3.siguiente()).contains(Grado.INICIAL_4);
	}

	@Test
	void siguienteDePrimaria6EsSecundaria1() {
		assertThat(Grado.PRIMARIA_6.siguiente()).contains(Grado.SECUNDARIA_1);
	}

	@Test
	void secundaria5NoTieneSiguiente() {
		assertThat(Grado.SECUNDARIA_5.siguiente()).isEmpty();
	}
}
