package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.virgenmaria.cuentasclaras.academico.api.InasistenciasDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA-S9-8: una corrección que llega ENTRE la lectura de la marca de agua y la de las marcas no deja al alumno fuera del
 * primer envío. El primer envío usa el estado de cada alumno en la marca de agua (la última marca de su cadena que no la
 * supera); la corrección sale en el envío siguiente como corrección de lo que la familia sí recibió.
 */
class PrimerEnvioConCorreccionTest extends PruebaAsistencia {

	@Autowired
	private InasistenciasDelDia inasistencias;

	@Autowired
	private org.springframework.transaction.PlatformTransactionManager transacciones;

	private <T> T comoSistema(java.util.function.Supplier<T> operacion) {
		return EjecucionComoSistema.como(ActorSistema.ASISTENCIA, 1L,
				() -> new org.springframework.transaction.support.TransactionTemplate(transacciones).execute(t -> operacion.get()));
	}

	@Test
	void debeAvisarLaFaltaQueHabiaEnLaMarcaDeAguaAunqueSeCorrijaAntesDeLeerLasMarcas() {
		alas(0, 9, 0);
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		// El proceso lee la marca de agua…
		long hasta = comoSistema(() -> inasistencias.ultimaMarcaId());
		// …y antes de leer las marcas, la tutora corrige a Mateo (llegó tarde).
		corregirHoy(datos.mateo(), EstadoAsistencia.TARDE);
		LocalDate hoy = hoy().toLocalDate();
		List<InasistenciasDelDia.Inasistencia> vigentes = comoSistema(() -> inasistencias.delDia(hoy));
		List<InasistenciasDelDia.Inasistencia> enLaMarca = comoSistema(() -> inasistencias.delDiaHasta(hoy, hasta));
		// Con las vigentes, la marca de Mateo supera la marca de agua y quedaba fuera del primer envío.
		assertThat(vigentes).allSatisfy(i -> assertThat(i.marcaId()).isGreaterThan(hasta));
		// En la marca de agua, Mateo sale con la falta que había (y la corrección, después).
		assertThat(enLaMarca).singleElement().satisfies(i -> {
			assertThat(i.alumnoId()).isEqualTo(datos.mateo());
			assertThat(i.marcaId()).isLessThanOrEqualTo(hasta);
			assertThat(i.estado()).containsIgnoringCase("falt");
		});
	}
}
