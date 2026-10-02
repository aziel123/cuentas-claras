package pe.edu.virgenmaria.cuentasclaras.aprobaciones.model;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** Datos de una solicitud como JSON pequeño de texto a texto: {"alumnoId":"12","fecha":"2026-10-01"}. */
public final class DatosSolicitud {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private DatosSolicitud() {
	}

	public static String escribir(Map<String, String> datos) {
		return JSON.writeValueAsString(new LinkedHashMap<>(datos));
	}

	public static Map<String, String> leer(String json) {
		try {
			return JSON.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
			});
		}
		catch (JacksonException e) {
			throw new IllegalStateException("Datos de solicitud ilegibles", e);
		}
	}
}
