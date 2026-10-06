package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import java.util.List;

/** La pantalla principal de recaudación: lotes recientes (por confirmar primero) y las líneas por revisar. */
public record RecaudacionLista(List<LoteResumen> porConfirmar, List<LoteResumen> lotes,
		List<LineaExcepcionResumen> excepciones, String banco, String modalidad) {
}
