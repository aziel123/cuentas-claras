package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correcciones del sprint 5 (S5-A1 y S5-M5): la forma canónica de un contacto para compararlo. La misma regla está en
 * MySQL ({@code cc_contacto_normal}); {@code PermisosMySqlTest} la prueba allí.
 */
class ContactoNormalTest {

	@Test
	void unAliasDeGmailEsElMismoBuzon() {
		assertThat(ContactoNormal.de("Lucia.Caja+ramos@GoogleMail.com")).isEqualTo("luciacaja@gmail.com");
		assertThat(ContactoNormal.iguales("lucia.caja@gmail.com", "l.u.c.i.a.caja+x@gmail.com")).isTrue();
	}

	@Test
	void elMasSeQuitaEnCualquierDominioPeroLosPuntosSoloEnGmail() {
		assertThat(ContactoNormal.de("rosa+colegio@outlook.com")).isEqualTo("rosa@outlook.com");
		assertThat(ContactoNormal.iguales("rosa.huaman@outlook.com", "rosahuaman@outlook.com")).isFalse();
	}

	@Test
	void unCelularConOSinCodigoDePaisEsElMismo() {
		assertThat(ContactoNormal.de("987 654 321")).isEqualTo("51987654321");
		assertThat(ContactoNormal.iguales("+51987654321", "51-987-654-321")).isTrue();
		assertThat(ContactoNormal.iguales("+51987654321", "+51987654322")).isFalse();
	}

	@Test
	void vacioNoEsIgualANada() {
		assertThat(ContactoNormal.de(null)).isEmpty();
		assertThat(ContactoNormal.iguales(null, null)).isFalse();
		assertThat(ContactoNormal.iguales("", "")).isFalse();
	}
}
