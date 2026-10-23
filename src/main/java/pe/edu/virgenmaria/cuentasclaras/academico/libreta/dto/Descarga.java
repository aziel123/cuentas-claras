package pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto;

import java.time.LocalDate;

/**
 * Sprint 10, tanda 3: lo que devuelve una descarga de libretas. Con el PDF y su nombre; o, si la huella no coincide (A3,
 * decisión 188), sin PDF y con la fecha en que el colegio la entregará a más tardar («en revisión»: nunca «sin libreta»).
 *
 * @param omitidas las libretas de una sección que no salieron porque su huella no coincide (siguen en revisión)
 */
public record Descarga(byte[] pdf, String nombreArchivo, LocalDate enRevisionHasta, int omitidas, Long alumnoId,
		Long familiaId, int libretas) {

	public static Descarga pdf(byte[] pdf, String nombreArchivo, Long alumnoId, Long familiaId, int libretas,
			int omitidas) {
		return new Descarga(pdf, nombreArchivo, null, omitidas, alumnoId, familiaId, libretas);
	}

	public static Descarga enRevision(LocalDate hasta, Long alumnoId, Long familiaId) {
		return new Descarga(null, null, hasta, 0, alumnoId, familiaId, 0);
	}

	public boolean enRevision() {
		return pdf == null;
	}
}
