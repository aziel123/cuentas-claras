package pe.edu.virgenmaria.cuentasclaras.operacion.salud;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.ResolucionRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.model.Respaldo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Correcciones del sprint 8 (S8-B6): «faltan filas» de un colegio que nadie puede resolver todavía (en preparación, sin su
 * titular activado) ya no deja al vigilante en REVISAR para siempre: lo marca mientras es NUEVA (el plazo del respaldo) y
 * después sigue en el correo técnico y en el panel de su colegio. La de un colegio que sí puede resolverla (en producción)
 * o la de toda la base, mientras siga abierta.
 *
 * <p>Escenarios (Dado / Cuando / Entonces):
 * <ul>
 *   <li>Dada una falta de un colegio en preparación de hace 3 días, cuando pregunta el vigilante, entonces no es REVISAR.</li>
 *   <li>Dada la misma falta de hace 2 horas, entonces sí (es nueva).</li>
 *   <li>Dada una falta de un colegio en producción de hace 3 días sin resolver, entonces sí.</li>
 *   <li>Dada una falta de toda la base (anterior a V30), entonces sí.</li>
 *   <li>Dada la falta del colegio en producción resuelta, entonces no.</li>
 * </ul>
 */
class VigilanteRespaldoS8b6Test {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 12, 9, 0);

	private static final LocalDateTime NUEVA_DESDE = AHORA.minusHours(26);

	private static Respaldo falta(long id, LocalDateTime fin, Long... colegios) {
		Respaldo r = mock(Respaldo.class);
		when(r.getId()).thenReturn(id);
		when(r.getFin()).thenReturn(fin);
		List<Long> lista = List.of(colegios);
		when(r.colegiosConFaltantes()).thenReturn(lista);
		when(r.deTodaLaBase()).thenReturn(lista.isEmpty());
		for (long c = 1; c <= 20; c++) {
			when(r.afectaA(c)).thenReturn(lista.isEmpty() || lista.contains(c));
		}
		return r;
	}

	private static ResolucionRespaldo resolucion(long respaldo, long colegio) {
		ResolucionRespaldo r = mock(ResolucionRespaldo.class);
		when(r.getRespaldoId()).thenReturn(respaldo);
		when(r.getColegioId()).thenReturn(colegio);
		return r;
	}

	private static final Set<Long> EN_PRODUCCION = Set.of(1L);

	@Test
	void unaFaltaViejaDeUnColegioQueNoPuedeResolverlaNoDejaAlVigilanteEnRevisar() {
		List<Respaldo> faltas = List.of(falta(10, AHORA.minusDays(3), 5L), falta(11, AHORA.minusDays(2), 5L),
				falta(12, AHORA.minusHours(1), 5L));

		assertThat(PendientesRespaldo.abiertoParaVigilante(faltas, List.of(), EN_PRODUCCION::contains, NUEVA_DESDE))
				.isEmpty();
		// Pero sigue abierta para el correo técnico y el panel de su colegio.
		assertThat(PendientesRespaldo.abiertoMasNuevo(faltas, List.of())).isPresent();
	}

	@Test
	void unaFaltaNuevaDeUnColegioEnPreparacionSiEsRevisar() {
		List<Respaldo> faltas = List.of(falta(20, AHORA.minusHours(2), 5L));

		assertThat(PendientesRespaldo.abiertoParaVigilante(faltas, List.of(), EN_PRODUCCION::contains, NUEVA_DESDE))
				.isPresent();
	}

	@Test
	void unaFaltaDeUnColegioEnProduccionODeTodaLaBaseEsRevisarMientrasSigaAbierta() {
		List<Respaldo> deProduccion = List.of(falta(30, AHORA.minusDays(3), 1L));
		assertThat(PendientesRespaldo.abiertoParaVigilante(deProduccion, List.of(), EN_PRODUCCION::contains, NUEVA_DESDE))
				.isPresent();
		assertThat(PendientesRespaldo.abiertoParaVigilante(deProduccion, List.of(resolucion(30, 1)), EN_PRODUCCION::contains,
				NUEVA_DESDE)).isEmpty();

		List<Respaldo> deTodaLaBase = List.of(falta(40, AHORA.minusDays(3)));
		assertThat(PendientesRespaldo.abiertoParaVigilante(deTodaLaBase, List.of(), EN_PRODUCCION::contains, NUEVA_DESDE))
				.isPresent();
	}
}
