package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import java.util.Map;

/**
 * Puerto: cuántos alumnos tiene matriculados (matrícula activa) cada sección de un año. Lo define {@code colegio} y
 * lo implementa {@code alumnos}, así las dependencias van en un solo sentido ({@code colegio ← alumnos}).
 */
public interface ConteoMatriculas {

	/** @return id de sección → matrículas activas (las secciones sin alumnos no aparecen) */
	Map<Long, Long> porSeccion(Long anioEscolarId);
}
