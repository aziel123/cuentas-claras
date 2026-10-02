package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorTest {

	@Test
	void limpiarQuitaInvisiblesYColapsaEspacios() {
		assertThat(Normalizador.limpiar("  Quispe​   Huamán\t\n")).isEqualTo("Quispe Huamán");
		assertThat(Normalizador.limpiar("﻿  ")).isNull();
		assertThat(Normalizador.limpiar(null)).isNull();
		// NFC: la «á» escrita como «a» + tilde combinada queda en un solo caracter.
		assertThat(Normalizador.limpiar("Huamán")).isEqualTo("Huamán").hasSize(6);
	}

	@Test
	void paraBusquedaSinTildesYEnMayusculas() {
		assertThat(Normalizador.paraBusqueda("Quispe", null, " Ñuñez ", "José")).isEqualTo("QUISPE NUNEZ JOSE");
	}

	@Test
	void escaparLikeEscapaComodines() {
		assertThat(Normalizador.escaparLike("50%_a!b")).isEqualTo("50!%!_a!!b");
	}
}
