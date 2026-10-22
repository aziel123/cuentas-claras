package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 3 (decisión 165, B14): el catálogo cerrado de la nómina para el SIAGIE no tiene ningún contacto, ningún
 * dato de los apoderados ni nada de cobranza, y el perfil provisional usa solo campos del catálogo.
 */
class CampoNominaSinContactosNiCobranzaTest {

	@Test
	void elCatalogoNoTieneContactosNiCobranza() {
		for (CampoNomina campo : CampoNomina.values()) {
			String nombre = campo.name().toLowerCase(Locale.ROOT);
			assertThat(nombre).as(campo.name()).doesNotContain("telefono", "celular", "correo", "direccion", "apoderado",
					"padre", "madre", "deuda", "monto", "saldo", "pago", "cuota", "pension");
		}
	}

	@Test
	void elPerfilProvisionalUsaSoloElCatalogo() {
		PerfilNomina perfil = PerfilNomina.leer(PerfilNomina.PROVISIONAL);
		assertThat(perfil.esProvisional()).isTrue();
		assertThat(perfil.columnas()).isNotEmpty();
		assertThat(perfil.columnas()).allMatch(c -> Arrays.asList(CampoNomina.values()).contains(c.campo()));
		assertThat(perfil.columnas().stream().map(PerfilNomina.Columna::campo)).contains(CampoNomina.NUMERO_DOCUMENTO,
				CampoNomina.SEXO, CampoNomina.SITUACION);
	}
}
