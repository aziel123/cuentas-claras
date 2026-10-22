package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import java.util.List;

/**
 * Sprint 9, tanda 3 (E19): una comprobación más de «Verificar integridad»: lo guardado en una tabla coincide con la huella
 * que quedó en la bitácora encadenada (por ejemplo, el texto de un comunicado enviado). La implementa el módulo dueño del
 * dato ({@code comunicados}), así {@code auditoria} no depende de él. Corre en el colegio del contexto.
 */
public interface VerificacionDeContenido {

	/** Lo que no coincide, en lenguaje claro y sin datos personales («El comunicado 12 cambió después de enviarse»). */
	List<String> problemas();
}
