package pe.edu.virgenmaria.cuentasclaras.comunicados.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;

import java.util.List;

/**
 * Sprint 9, tanda 3: lo que la persona en sesión puede elegir al escribir: con qué rol firma (los suyos), a qué alcance
 * (el docente, solo sus secciones) y si puede marcarlo urgente.
 */
public record OpcionesComunicado(List<EmisorRol> emisores, List<AlcanceComunicado> alcances, List<Nivel> niveles,
		List<Grado> grados, List<OpcionSeccionComunicado> secciones, boolean urgentePermitido, boolean puedeEscribir) {
}
