package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.util.List;

/** Pantalla «Pensiones» de un año. {@code anio} es nulo si el colegio aún no tiene años escolares. */
public record ResumenPensiones(AnioOpcion anio, List<AnioOpcion> anios, List<TarjetaNivel> niveles,
		long matriculasSinCronograma, long cuotasGeneradas, boolean puedeGenerar) {
}
