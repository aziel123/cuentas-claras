package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sprint 9, tanda 2: la hoja del día en un solo POST (una firma por registro, A5). En el formulario cada alumno manda
 * {@code estado-{alumnoId}} (PRESENTE, FALTA o TARDE) y, si llegó tarde, {@code hora-{alumnoId}} (HH:mm, opcional): un
 * toque por alumno, sin JavaScript. No hay campo {@code fuente} (A15).
 */
public record HojaRequest(@NotNull(message = "La hoja llegó vacía.") @Valid List<MarcaPedida> marcas, Long solicitudId) {

	public static final String ESTADO = "estado-";

	public static final String HORA = "hora-";

	public HojaRequest {
		marcas = marcas == null ? new ArrayList<>() : marcas;
	}

	/** Lee la hoja de los parámetros del formulario (lo que no es {@code estado-} o {@code hora-} se ignora). */
	public static HojaRequest deFormulario(Map<String, String> parametros, Long solicitudId) {
		List<MarcaPedida> marcas = new ArrayList<>();
		for (Map.Entry<String, String> p : parametros.entrySet()) {
			if (!p.getKey().startsWith(ESTADO)) {
				continue;
			}
			String id = p.getKey().substring(ESTADO.length());
			try {
				Long alumnoId = Long.valueOf(id);
				EstadoAsistencia estado = EstadoAsistencia.valueOf(p.getValue());
				String hora = parametros.get(HORA + id);
				LocalTime llegada = estado == EstadoAsistencia.TARDE && hora != null && !hora.isBlank()
						? LocalTime.parse(hora.strip()) : null;
				marcas.add(new MarcaPedida(alumnoId, estado, llegada));
			}
			catch (IllegalArgumentException | DateTimeParseException e) {
				throw new ReglaNegocioException("La hoja tiene un dato que no se entiende: vuelve a abrirla.");
			}
		}
		return new HojaRequest(marcas, solicitudId);
	}
}
