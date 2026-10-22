package pe.edu.virgenmaria.cuentasclaras.comunicados.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.CodigoModularRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.NominaSiagie;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.AdjuntoValidado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.BorradorComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.OpcionSeccionComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.AdjuntosComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.EnvioComunicados;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.PdfEjemplo;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.util.List;

/**
 * Sprint 9, tanda 3 (sección 3.14, decisión 167): los comunicados de la demo de promotores, con datos ficticios. Dirección
 * envía uno a todo el colegio con el PDF de ejemplo (que pasa por el analizador, en su proceso aparte) y su fe de
 * erratas; la tutora, uno a su sección. Y los códigos modulares ficticios (0000001 a 0000003) para la nómina con la marca
 * «DEMO · DATOS FICTICIOS». Si algo falla, la demo sigue sin ellos (queda en el log).
 */
@Component
@Profile("demo")
public class ComunicadosDemo {

	private static final Logger LOG = LoggerFactory.getLogger(ComunicadosDemo.class);

	private final EnvioComunicados envio;

	private final AdjuntosComunicado adjuntos;

	private final PdfEjemplo ejemplo;

	private final NominaSiagie nomina;

	private final PersonaDemo personas;

	public ComunicadosDemo(EnvioComunicados envio, AdjuntosComunicado adjuntos, PdfEjemplo ejemplo, NominaSiagie nomina,
			PersonaDemo personas) {
		this.envio = envio;
		this.adjuntos = adjuntos;
		this.ejemplo = ejemplo;
		this.nomina = nomina;
		this.personas = personas;
	}

	public void cargar(long colegioId, String direccion, String docente) {
		try {
			personas.como(colegioId, direccion, () -> {
				for (Nivel nivel : Nivel.values()) {
					nomina.registrarCodigoModular(new CodigoModularRequest(nivel, "000000" + (nivel.ordinal() + 1), "0"));
				}
				AdjuntoValidado pdf = adjuntos.validar(PdfEjemplo.ARCHIVO, ejemplo.contenido());
				Long dia = envio.enviar(new BorradorComunicado(EmisorRol.DIRECCION, AlcanceComunicado.COLEGIO, null, null,
						null, "Día del Logro (ejemplo de la demo)", "Estimadas familias: el viernes celebraremos el Día del "
								+ "Logro en el patio principal, de 9:00 a 11:30. Los alumnos asisten con el uniforme de "
								+ "educación física. Encontrarán el programa en el PDF adjunto.", false, true), pdf);
				envio.enviarFeDeErratas(dia, new BorradorComunicado(EmisorRol.DIRECCION, AlcanceComunicado.COLEGIO, null,
						null, null, "Fe de erratas: Día del Logro", "Corregimos el horario: el Día del Logro es de 9:30 a "
								+ "12:00. Lo demás no cambia.", false, true), null);
				return null;
			});
			personas.como(colegioId, docente, () -> {
				List<OpcionSeccionComunicado> secciones = envio.opciones().secciones();
				if (!secciones.isEmpty()) {
					envio.enviar(new BorradorComunicado(EmisorRol.DOCENTE, AlcanceComunicado.SECCION, null, null,
							secciones.getFirst().id(), "Salida al museo (ejemplo de la demo)", "Queridas familias: el "
									+ "jueves visitaremos el museo de ciencias. Salimos del colegio a las 9:00 y volvemos "
									+ "a las 12:30. Envíen a sus hijos con lonchera y gorra.", false, true), null);
				}
				return null;
			});
		}
		catch (ReglaNegocioException | IllegalStateException e) {
			LOG.warn("No se cargaron los comunicados de la demo del colegio {}: {}", colegioId, e.getMessage());
		}
	}
}
