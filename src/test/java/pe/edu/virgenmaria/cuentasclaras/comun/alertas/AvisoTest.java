package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 6, tanda 2: el aviso al celular lleva una referencia corta y estable (cabe en la clave del mensaje) y, como dato,
 * solo un monto, una hora o una fecha: nunca un nombre ni texto escrito por una persona (hallazgo 2).
 */
class AvisoTest {

	@Test
	void laReferenciaEsCortaYSinCaracteresRaros() {
		assertThat(new Aviso(TipoAviso.CIERRE_CON_DIFERENCIA, "C:15").referencia()).isEqualTo("C:15");
		assertThatThrownBy(() -> new Aviso(TipoAviso.OTRA_CRITICA, "con espacio"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Aviso(TipoAviso.OTRA_CRITICA, "x".repeat(41)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Aviso(TipoAviso.OTRA_CRITICA, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void elDatoEsSoloUnMontoUnaHoraOUnaFecha() {
		assertThat(new Aviso(TipoAviso.CIERRE_CON_DIFERENCIA, "C:1", "-S/ 1,250.00").dato()).isEqualTo("-S/ 1,250.00");
		assertThat(new Aviso(TipoAviso.CIERRE_NO_REALIZADO, "K:1", "19:00").dato()).isEqualTo("19:00");
		assertThat(new Aviso(TipoAviso.CAJA_SIN_CERRAR, "K:1", "15/04/2027").dato()).isEqualTo("15/04/2027");
		for (String nombre : new String[] { "Lucía Ramos", "faltó porque", "<script>", "S/ 50 (Quispe)" }) {
			assertThatThrownBy(() -> new Aviso(TipoAviso.OTRA_CRITICA, "X:1", nombre)).as(nombre)
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void difundeLasCriticasYLasDosAtencionDeLaDecision70() {
		AlertaRevision critica = new AlertaRevision(AlertaRevision.Gravedad.CRITICA, "Caja", "x", null,
				new Aviso(TipoAviso.OTRA_CRITICA, "A:1"));
		AlertaRevision atencion = new AlertaRevision(AlertaRevision.Gravedad.ATENCION, "Caja", "x", null,
				new Aviso(TipoAviso.OTRA_CRITICA, "A:2"));
		AlertaRevision cierre = new AlertaRevision(AlertaRevision.Gravedad.ATENCION, "Caja", "x", null,
				new Aviso(TipoAviso.CIERRE_NO_REALIZADO, "K:1", "19:00", Set.of("caja")));
		AlertaRevision sinAviso = new AlertaRevision(AlertaRevision.Gravedad.CRITICA, "Caja", "x", null);
		assertThat(critica.difundible()).isTrue();
		assertThat(atencion.difundible()).isFalse();
		assertThat(cierre.difundible()).isTrue();
		assertThat(cierre.aviso().excluidos()).containsExactly("caja");
		assertThat(sinAviso.difundible()).isFalse();
		assertThat(sinAviso.aviso()).isNull();
	}

	@Test
	void losTextosFijosNoLlevanMarcadoresNiEnlaces() {
		for (TipoAviso tipo : TipoAviso.values()) {
			assertThat(tipo.texto()).as(tipo.name()).doesNotContain("{{", "http", "/").isNotBlank();
		}
	}
}
