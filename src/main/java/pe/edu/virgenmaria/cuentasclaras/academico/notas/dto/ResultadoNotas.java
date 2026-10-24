package pe.edu.virgenmaria.cuentasclaras.academico.notas.dto;

/** Sprint 10, tanda 2: lo que guardó un lote (cuántas notas nuevas, cambios y «sin nota»). */
public record ResultadoNotas(Long loteId, int secuencia, int nuevas, int cambios, int sinNota) {

	public int filas() {
		return nuevas + cambios + sinNota;
	}

	public String resumen() {
		return nuevas + " nuevas, " + cambios + " cambios y " + sinNota + " sin nota";
	}
}
