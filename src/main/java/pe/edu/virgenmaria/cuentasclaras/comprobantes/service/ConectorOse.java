package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.SerieComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
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

	public ConectorOse(OseDelColegio ose, SerieComprobanteRepository series) {
		this.ose = ose;
		this.series = series;
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
		if (candidato.proveedor() == ProveedorConector.SIMULADO) {
			return ResultadoPrueba.correcta("El comprobante simulado no sale del sistema: no tiene valor ante SUNAT.");
		}
		ConfiguracionConector.Ose configuracion = candidato.configuracionDe(ConfiguracionConector.Ose.class);
		EmisorElectronico emisor;
		try {
			emisor = ose.nubefactDe(candidato);
		}
		catch (IllegalStateException e) {
			return ResultadoPrueba.fallida("La ruta o el token de Nubefact no son válidos.");
		}
		try {
			ResultadoEnvio respuesta = emisor.consultar(TipoComprobante.BOLETA, configuracion.serieBoleta(), 1);
			return ResultadoPrueba.correcta("Nubefact respondió a la consulta de prueba con la cuenta indicada"
					+ (respuesta == null || respuesta.codigoRespuesta() == null ? "."
							: " (código " + respuesta.codigoRespuesta() + ")."));
		}
		catch (RuntimeException e) {
			return ResultadoPrueba.fallida("Nubefact no aceptó la conexión con esa ruta y ese token ("
					+ e.getClass().getSimpleName() + ").");
		}
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
