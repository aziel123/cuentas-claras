package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

import java.time.LocalDate;

/** Sprint 10, tanda 1: una versión del catálogo nacional (código, nombre, normas y desde cuándo vale). */
public record VersionDelCatalogo(Long id, String codigo, String nombre, String normas, LocalDate vigenteDesde,
		String reemplaza, String nota) {
}
