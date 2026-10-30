package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import java.util.List;
import java.util.Objects;

/**
 * Sprint 11, tanda 1 (sección 9): el aviso a los operadores activos de la plataforma, en el colegio de PLATAFORMA (su
 * contexto). La mensajería lo escucha ({@code comunicacion.service.AvisosPlataforma}) y crea un mensaje a cada operador
 * activo, en la MISMA transacción. Solo lleva el colegio, el cambio y quién lo hizo: nunca contactos ni datos de familias
 * (decisión 113). Si no hay a quién avisar, lanza la excepción y no se guarda.
 *
 * @param plataformaId el colegio de plataforma (debe ser el del contexto)
 * @param tipo         qué pasó (elige la plantilla)
 * @param parametros   los parámetros propios de la plantilla ({{1}} es el nombre de la plataforma: el colegio afectado
 *                     va dentro de los parámetros)
 * @param entidad      la entidad que lo origina
 * @param entidadId    su id
 * @param sufijo       distingue dos avisos de la misma entidad
 */
public record AvisoOperadores(Long plataformaId, AvisoPlataforma.Tipo tipo, List<String> parametros, String entidad,
		Long entidadId, String sufijo) {

	public AvisoOperadores {
		Objects.requireNonNull(plataformaId, "plataformaId");
		Objects.requireNonNull(tipo, "tipo");
		Objects.requireNonNull(entidad, "entidad");
		Objects.requireNonNull(entidadId, "entidadId");
		parametros = parametros == null ? List.of() : List.copyOf(parametros);
	}
}
