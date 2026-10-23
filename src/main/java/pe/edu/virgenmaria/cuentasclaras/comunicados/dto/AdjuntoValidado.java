package pe.edu.virgenmaria.cuentasclaras.comunicados.dto;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Sprint 9, tanda 3 (sección 3.9, B3): un PDF que el analizador (proceso hijo) aceptó y que el filtro de contenido revisó:
 * el saneado, sus páginas, los dos SHA-256 (los recalcula el padre) y si requiere revisión del otro órgano. Vive en la
 * sesión de quien lo subió entre la vista previa y el envío (el envío vuelve a comprobar su huella).
 */
public record AdjuntoValidado(byte[] saneado, int paginas, String sha256, String sha256Original, boolean requiereRevision)
		implements Serializable {

	public AdjuntoValidado {
		Objects.requireNonNull(saneado, "saneado");
		saneado = saneado.clone();
	}

	@Override
	public byte[] saneado() {
		return saneado.clone();
	}

	public int bytes() {
		return saneado.length;
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof AdjuntoValidado a && Arrays.equals(saneado, a.saneado) && sha256.equals(a.sha256);
	}

	@Override
	public int hashCode() {
		return sha256.hashCode();
	}

	@Override
	public String toString() {
		return "AdjuntoValidado[" + paginas + " páginas, " + saneado.length + " bytes]";
	}
}
