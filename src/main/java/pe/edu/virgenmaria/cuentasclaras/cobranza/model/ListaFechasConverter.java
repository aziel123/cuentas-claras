package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Vencimientos del plan en una sola columna: fechas ISO separadas por comas (12 × 10 + 11 = 131 ≤ 140 caracteres).
 * Es un valor del plan que se lee y escribe completo; evita una tabla hija que necesitaría DELETE.
 */
@Converter
public class ListaFechasConverter implements AttributeConverter<List<LocalDate>, String> {

	@Override
	public String convertToDatabaseColumn(List<LocalDate> fechas) {
		return fechas == null ? null : fechas.stream().map(LocalDate::toString).collect(Collectors.joining(","));
	}

	@Override
	public List<LocalDate> convertToEntityAttribute(String texto) {
		if (texto == null || texto.isBlank()) {
			return List.of();
		}
		return Arrays.stream(texto.split(",")).map(String::strip).map(LocalDate::parse).toList();
	}
}
