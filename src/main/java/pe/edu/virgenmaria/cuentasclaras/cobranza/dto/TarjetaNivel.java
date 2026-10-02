package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.util.List;

/** Un nivel en el resumen de pensiones: plan vigente, borradores por aprobar y matrículas sin cronograma. */
public record TarjetaNivel(String nivel, String etiqueta, PlanResumen vigente, List<PlanResumen> borradores,
		long sinCronograma, boolean puedeProponer) {
}
