package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

import java.util.List;

/**
 * Sprint 9, tanda 3 (sección 3.11): «Datos que faltan para el SIAGIE»: por sección, cuántos alumnos sin sexo y sin DNI ni
 * código de estudiante (con sus nombres, solo para Dirección), y los niveles sin código modular.
 */
public record DatosFaltantes(List<Seccion> secciones, List<String> nivelesSinCodigo) {

	public record Seccion(Long seccionId, String etiqueta, int alumnos, List<String> sinSexo, List<String> sinDocumento) {

		public int faltantes() {
			return (int) java.util.stream.Stream.concat(sinSexo.stream(), sinDocumento.stream()).distinct().count();
		}
	}
}
