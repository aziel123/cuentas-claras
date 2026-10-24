package pe.edu.virgenmaria.cuentasclaras.comunicados.inicial;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.CodigoModularRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.NominaSiagie;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.AdjuntoValidado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.BorradorComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.OpcionSeccionComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;
import pe.edu.virgenmaria.cuentasclaras.comunicados.repository.ComunicadoRepository;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.AdjuntosComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.EnvioComunicados;
import pe.edu.virgenmaria.cuentasclaras.comunicados.service.PdfEjemplo;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

import java.util.List;

/**
 * Los comunicados de ejemplo, enviados por las personas del colegio con los mismos servicios (el PDF pasa por el
 * analizador en su proceso aparte y todo PDF lo aprueba el otro órgano en la bandeja). Los usan la demo pública
 * ({@link ComunicadosDemo}) y el arranque de desarrollo ({@code AcademicoDemoDev}). Las reglas de negocio que fallen las
 * maneja quien llama.
 */
@Component
@Profile({ "dev", "demo" })
public class EjemploComunicados {

	private final EnvioComunicados envio;

	private final AdjuntosComunicado adjuntos;

	private final PdfEjemplo ejemplo;

	private final NominaSiagie nomina;

	private final PersonaDemo personas;

	private final BandejaAprobaciones bandeja;

	private final ComunicadoRepository comunicados;

	public EjemploComunicados(EnvioComunicados envio, AdjuntosComunicado adjuntos, PdfEjemplo ejemplo, NominaSiagie nomina,
			PersonaDemo personas, BandejaAprobaciones bandeja, ComunicadoRepository comunicados) {
		this.envio = envio;
		this.adjuntos = adjuntos;
		this.ejemplo = ejemplo;
		this.nomina = nomina;
		this.personas = personas;
		this.bandeja = bandeja;
		this.comunicados = comunicados;
	}

	/** Dirección registra los códigos modulares ficticios (0000001 a 0000003) de la nómina del SIAGIE. */
	public void codigosModulares(long colegioId, String direccion) {
		personas.como(colegioId, direccion, () -> {
			for (Nivel nivel : Nivel.values()) {
				nomina.registrarCodigoModular(new CodigoModularRequest(nivel, "000000" + (nivel.ordinal() + 1), "0"));
			}
			return null;
		});
	}

	/** Dirección envía a todo el colegio el «Día del Logro» con el PDF de ejemplo: queda POR_APROBAR. @return su id */
	public Long diaDelLogro(long colegioId, String direccion) {
		return personas.como(colegioId, direccion, () -> {
			AdjuntoValidado pdf = adjuntos.validar(PdfEjemplo.ARCHIVO, ejemplo.contenido());
			return envio.enviar(new BorradorComunicado(EmisorRol.DIRECCION, AlcanceComunicado.COLEGIO, null, null, null,
					"Día del Logro (ejemplo de la demo)", "Estimadas familias: el viernes celebraremos el Día del Logro en el "
							+ "patio principal, de 9:00 a 11:30. Los alumnos asisten con el uniforme de educación física. "
							+ "Encontrarán el programa en el PDF adjunto.", false, true), pdf);
		});
	}

	/** Quien aprueba (el otro órgano) publica el comunicado en la bandeja, con su firma. */
	public void aprobar(long colegioId, String quien, Long comunicadoId) {
		Long solicitud = personas.como(colegioId, quien, () -> comunicados.findById(comunicadoId).orElseThrow()
				.getSolicitudId());
		personas.como(colegioId, quien, () -> {
			bandeja.aprobar(solicitud, null);
			return null;
		});
	}

	/** Dirección corrige el horario del «Día del Logro» con una fe de erratas. */
	public void feDeErratasDelDiaDelLogro(long colegioId, String direccion, Long original) {
		personas.como(colegioId, direccion, () -> {
			envio.enviarFeDeErratas(original, new BorradorComunicado(EmisorRol.DIRECCION, AlcanceComunicado.COLEGIO, null,
					null, null, "Fe de erratas: Día del Logro", "Corregimos el horario: el Día del Logro es de 9:30 a "
							+ "12:00. Lo demás no cambia.", false, true), null);
			return null;
		});
	}

	/** La tutora envía a su primera sección la «Salida al museo» (sin PDF: sale de inmediato). @return su id o null */
	public Long salidaAlMuseo(long colegioId, String docente) {
		return personas.como(colegioId, docente, () -> {
			List<OpcionSeccionComunicado> secciones = envio.opciones().secciones();
			if (secciones.isEmpty()) {
				return null;
			}
			return envio.enviar(new BorradorComunicado(EmisorRol.DOCENTE, AlcanceComunicado.SECCION, null, null,
					secciones.getFirst().id(), "Salida al museo (ejemplo de la demo)", "Queridas familias: el jueves "
							+ "visitaremos el museo de ciencias. Salimos del colegio a las 9:00 y volvemos a las 12:30. "
							+ "Envíen a sus hijos con lonchera y gorra.", false, true), null);
		});
	}
}
