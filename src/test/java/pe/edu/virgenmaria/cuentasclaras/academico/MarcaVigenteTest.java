package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Sprint 9, tanda 2 (E8): corregir es agregar una marca; la vigente es la última de la cadena y las demás quedan. */
class MarcaVigenteTest extends PruebaAsistencia {

	@Test
	void laVigenteEsLaUltimaDeLaCadena() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		corregirHoy(datos.mateo(), EstadoAsistencia.PRESENTE);
		corregirHoy(datos.mateo(), EstadoAsistencia.TARDE);
		List<String> cadena = jdbc.queryForList("SELECT estado FROM marca_asistencia WHERE alumno_id = ? ORDER BY id",
				String.class, datos.mateo());
		assertThat(cadena).containsExactly("FALTA", "PRESENTE", "TARDE");
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES)).isEqualTo("TARDE");
		HojaDelDia hoja = como(tutora(), () -> asistencia.hoja(datos.seccion()));
		assertThat(hoja.alumnos()).filteredOn(a -> a.alumnoId() == datos.mateo()).singleElement()
				.satisfies(a -> assertThat(a.estado()).isEqualTo("TARDE"));
		assertThat(hoja.tardes()).isEqualTo(1);
		assertThat(hoja.faltas()).isZero();
		// Cada corrección reemplaza a UNA marca (cadena lineal).
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT reemplaza_id) FROM marca_asistencia WHERE alumno_id = ? "
				+ "AND reemplaza_id IS NOT NULL", Long.class, datos.mateo())).isEqualTo(2);
	}
}
