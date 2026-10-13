package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.SerieComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Ruc;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.AdaptadorConector;

import java.util.List;

/**
 * Sprint 8, tanda 2 (sección 3.6): el conector OSE visto desde comprobantes.
 * <ul>
 *   <li><b>Probar:</b> el simulado siempre responde (no sale del sistema); Nubefact se prueba con una consulta real a la
 *       ruta y con el token del conector propuesto (si la cuenta no responde o rechaza el token, la prueba falla).</li>
 *   <li><b>Antes de la vigencia</b> ({@code VerificadorSeries} por colegio, decisión 20): ninguna serie del conector puede
 *       haberse usado en ESE colegio con otro proveedor. Un número simulado nunca pasa por uno real, ni al revés. Además,
 *       dos sedes con el mismo RUC no comparten serie (lo exige trg_serie_comprobante_nace al abrirla).</li>
 * </ul>
 */
@Component
public class ConectorOse implements AdaptadorConector {

	private final OseDelColegio ose;

	private final SerieComprobanteRepository series;

	private final SeriesDeOtrasSedes otrasSedes;

	private final ColegioRepository colegios;

	public ConectorOse(OseDelColegio ose, SerieComprobanteRepository series, SeriesDeOtrasSedes otrasSedes,
			ColegioRepository colegios) {
		this.ose = ose;
		this.series = series;
		this.otrasSedes = otrasSedes;
		this.colegios = colegios;
	}

	@Override
	public TipoConector tipo() {
		return TipoConector.OSE;
	}

	@Override
	public boolean admite(ProveedorConector proveedor) {
		return proveedor == ProveedorConector.SIMULADO || proveedor == ProveedorConector.NUBEFACT;
	}

	@Override
	public ResultadoPrueba probar(ConectorVigente candidato) {
		ConfiguracionConector.Ose configuracion = candidato.configuracionDe(ConfiguracionConector.Ose.class);
		// S8-M4: el RUC emisor del conector es el del colegio (que pudo cambiar desde la propuesta) y es un RUC válido.
		String rucColegio = colegios.findById(candidato.colegioId()).map(Colegio::getRuc).orElse(null);
		if (rucColegio == null || !rucColegio.equals(configuracion.rucEmisor()) || !Ruc.valido(configuracion.rucEmisor())) {
			return ResultadoPrueba.fallida("El RUC emisor del conector (" + configuracion.rucEmisor() + ") no es el RUC del "
					+ "colegio: propón el OSE de nuevo con el RUC correcto.");
		}
		// QA-S8-6: ninguna serie la usa otra sede con el mismo RUC.
		List<String> ocupadas = otrasSedes.ocupadas(candidato.colegioId(), configuracion.rucEmisor(), configuracion.series());
		if (!ocupadas.isEmpty()) {
			return ResultadoPrueba.fallida("Las series " + ocupadas + " ya las usa otra sede con el mismo RUC.");
		}
		if (candidato.proveedor() == ProveedorConector.SIMULADO) {
			return ResultadoPrueba.correcta("El comprobante simulado no sale del sistema: no tiene valor ante SUNAT.");
		}
		EmisorElectronico emisor;
		try {
			emisor = ose.nubefactDe(candidato);
		}
		catch (IllegalStateException e) {
			return ResultadoPrueba.fallida("La ruta o el token de Nubefact no son válidos.");
		}
		try {
			ResultadoEnvio respuesta = emisor.consultar(TipoComprobante.BOLETA, configuracion.serieBoleta(), 1);
			// S8-M4: si Nubefact informa el RUC del emisor (la cadena del código QR empieza con él), debe ser el del
			// colegio: un token de la cuenta de otro RUC no pasa la prueba.
			String rucInformado = rucDe(respuesta);
			if (rucInformado != null && !rucInformado.equals(configuracion.rucEmisor())) {
				return ResultadoPrueba.fallida("La cuenta de Nubefact de esa ruta y ese token emite con OTRO RUC ("
						+ rucInformado + "), no con el del colegio.");
			}
			return ResultadoPrueba.correcta("Nubefact respondió a la consulta de prueba con la cuenta indicada"
					+ (rucInformado == null ? " (no informó el RUC: confírmalo con tu contador)" : " del RUC " + rucInformado)
					+ (respuesta == null || respuesta.codigoRespuesta() == null ? "."
							: " (código " + respuesta.codigoRespuesta() + ")."));
		}
		catch (RuntimeException e) {
			return ResultadoPrueba.fallida("Nubefact no aceptó la conexión con esa ruta y ese token ("
					+ e.getClass().getSimpleName() + ").");
		}
	}

	/** El RUC del emisor que informa la respuesta del OSE (primer campo de la cadena del QR), o {@code null}. */
	static String rucDe(ResultadoEnvio respuesta) {
		if (respuesta == null || respuesta.respuesta() == null) {
			return null;
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("cadena_para_codigo_qr\\W+((10|20)[0-9]{9})\\s*\\|")
				.matcher(respuesta.respuesta());
		return m.find() ? m.group(1) : null;
	}

	/** QA-S8-6: antes de aprobar, ninguna serie la usa otra sede con el mismo RUC (sin transacción: ve otros colegios). */
	@Override
	public void validarFueraDeTransaccion(ConectorVigente candidato) {
		ConfiguracionConector.Ose configuracion = candidato.configuracionDe(ConfiguracionConector.Ose.class);
		otrasSedes.exigirLibres(candidato.colegioId(), configuracion.rucEmisor(), configuracion.series());
	}

	@Override
	public void validarAntesDeVigencia(ConectorVigente candidato) {
		ConfiguracionConector.Ose configuracion = candidato.configuracionDe(ConfiguracionConector.Ose.class);
		ProveedorComprobantes proveedor = OseDelColegio.proveedorDe(candidato.proveedor());
		List<String> otras = configuracion.series().stream()
				.map(series::findBySerie).flatMap(java.util.Optional::stream)
				.filter(s -> s.getProveedor() != proveedor).map(SerieComprobante::getSerie).toList();
		if (!otras.isEmpty()) {
			throw new ReglaNegocioException("Las series " + otras + " ya se usaron en el colegio con otro emisor: con "
					+ candidato.proveedor().etiqueta() + " configura series nuevas (por ejemplo B002, F002, BC02 y FC02), "
					+ "para que un número simulado nunca pase por uno real.");
		}
	}
}
