package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.config.PropiedadesComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.AfectacionIgv;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.SerieComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ModoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.ConectoresColegio;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Sprint 8, tanda 2 (sección 3.6, H4 y decisión 20): el OSE de cada colegio sale de su conector. Las series y la
 * afectación son las del conector; el emisor de un comprobante es el de SU proveedor; un conector real no reutiliza series
 * usadas con el simulado en ese colegio; la nota de crédito de un comprobante simulado sale con una serie simulada aunque
 * el colegio ya tenga su OSE real; y Nubefact se prueba con la ruta y el token del conector.
 */
class ConectorOseTest {

	private static final PropiedadesComprobantes ENTORNO = new PropiedadesComprobantes(ProveedorComprobantes.SIMULADO,
			AfectacionIgv.INAFECTO, "B001", "F001", "BC01", "FC01");

	private final ConectoresColegio conectores = mock(ConectoresColegio.class);

	private final SerieComprobanteRepository series = mock(SerieComprobanteRepository.class);

	@SuppressWarnings("unchecked")
	private final ObjectProvider<EmisorElectronico> sinEmisorDelEntorno = mock(ObjectProvider.class);

	private final OseDelColegio ose = new OseDelColegio(conectores, ENTORNO, sinEmisorDelEntorno);

	private static ConectorVigente nubefact() {
		return new ConectorVigente(7L, 0L, 3L, TipoConector.OSE, ProveedorConector.NUBEFACT, ModoConector.REAL,
				"0f0e0d0c-0b0a-4908-8706-050403020100", new ConfiguracionConector.Ose("https://api.nubefact.com/api/v1/x",
						"20600000013", "B002", "F002", "BC02", "FC02", "EXONERADO"),
				Map.of(ConectorVigente.TOKEN, "token-de-pruebas-0123456789"));
	}

	@Test
	void lasSeriesYLaAfectacionSonLasDelConectorYSinConectorLasDelEntorno() {
		when(conectores.vigente(TipoConector.OSE)).thenReturn(Optional.empty());
		assertThat(ose.actual().serieDe(TipoComprobante.BOLETA)).isEqualTo("B001");
		assertThat(ose.actual().simulado()).isTrue();

		when(conectores.vigente(TipoConector.OSE)).thenReturn(Optional.of(nubefact()));
		OseDelColegio.OseVigente vigente = ose.actual();
		assertThat(vigente.serieDe(TipoComprobante.BOLETA)).isEqualTo("B002");
		assertThat(vigente.serieDe(TipoComprobante.FACTURA)).isEqualTo("F002");
		assertThat(vigente.proveedor()).isEqualTo(ProveedorComprobantes.NUBEFACT);
		assertThat(vigente.afectacion()).isEqualTo(AfectacionIgv.EXONERADO);
	}

	@Test
	void laNotaDeUnComprobanteSimuladoSaleConSerieSimuladaAunqueHayaOseReal() {
		when(conectores.vigente(TipoConector.OSE)).thenReturn(Optional.of(nubefact()));
		when(conectores.osesDelColegio()).thenReturn(List.of(new ConectoresColegio.OseRegistrado(ProveedorConector.NUBEFACT,
				nubefact().configuracionDe(ConfiguracionConector.Ose.class))));

		assertThat(ose.serieNotaPara(ProveedorComprobantes.NUBEFACT, TipoComprobante.BOLETA)).isEqualTo("BC02");
		assertThat(ose.serieNotaPara(ProveedorComprobantes.SIMULADO, TipoComprobante.BOLETA)).isEqualTo("BC01");
		assertThat(ose.serieNotaPara(ProveedorComprobantes.SIMULADO, TipoComprobante.FACTURA)).isEqualTo("FC01");
		assertThat(ose.seriesConocidas()).contains("B001", "BC01", "B002", "FC02");
	}

	@Test
	void unComprobanteSimuladoSeEnviaAlSimuladoYUnoRealSinCuentaEspera() {
		when(conectores.vigente(TipoConector.OSE)).thenReturn(Optional.empty());
		when(conectores.compartida()).thenReturn(true);

		assertThat(ose.emisorPara(ProveedorComprobantes.SIMULADO).proveedor()).isEqualTo(ProveedorComprobantes.SIMULADO);
		assertThatThrownBy(() -> ose.emisorPara(ProveedorComprobantes.NUBEFACT)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("se reintentará");
	}

	@Test
	void unOseRealNoReutilizaSeriesDelSimulado() {
		ConectorOse adaptador = new ConectorOse(ose, series);
		when(series.findBySerie(any())).thenReturn(Optional.empty());
		when(series.findBySerie("B002")).thenReturn(Optional.of(SerieComprobante.nueva(TipoComprobante.BOLETA, "B002",
				ProveedorComprobantes.SIMULADO)));

		assertThatThrownBy(() -> adaptador.validarAntesDeVigencia(nubefact())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("B002").hasMessageContaining("series nuevas");

		when(series.findBySerie("B002")).thenReturn(Optional.of(SerieComprobante.nueva(TipoComprobante.BOLETA, "B002",
				ProveedorComprobantes.NUBEFACT)));
		adaptador.validarAntesDeVigencia(nubefact());
	}

	@Test
	void nubefactSePruebaConLaRutaYElTokenDelConector() {
		OseDelColegio probado = spy(ose);
		EmisorElectronico responde = mock(EmisorElectronico.class);
		when(responde.consultar(any(), any(), anyInt())).thenReturn(new ResultadoEnvio(EstadoEnvio.PENDIENTE,
				"No existe", null, null, "24", null));
		doReturn(responde).when(probado).nubefactDe(any());
		ConectorOse adaptador = new ConectorOse(probado, series);

		assertThat(adaptador.probar(nubefact()).ok()).isTrue();

		EmisorElectronico rechaza = mock(EmisorElectronico.class);
		when(rechaza.consultar(any(), any(), anyInt())).thenThrow(new IllegalStateException("Nubefact respondió 401"));
		doReturn(rechaza).when(probado).nubefactDe(any());
		assertThat(adaptador.probar(nubefact()).ok()).isFalse();
		assertThat(adaptador.probar(nubefact()).detalle()).doesNotContain("token-de-pruebas");
		// El simulado no sale del sistema.
		assertThat(adaptador.probar(new ConectorVigente(8L, 0L, 3L, TipoConector.OSE, ProveedorConector.SIMULADO,
				ModoConector.SIMULADO, "0f0e0d0c-0b0a-4908-8706-050403020101", nubefact().configuracion(), Map.of())).ok())
				.isTrue();
	}

	@Test
	void unaRutaFueraDeLaListaCerradaNoSePrueba() {
		ConectorVigente hacia = new ConectorVigente(9L, 0L, 3L, TipoConector.OSE, ProveedorConector.NUBEFACT,
				ModoConector.REAL, "0f0e0d0c-0b0a-4908-8706-050403020102", new ConfiguracionConector.Ose(
						"https://servidor-propio.example.com/ose", "20600000013", "B002", "F002", "BC02", "FC02", "INAFECTO"),
				Map.of(ConectorVigente.TOKEN, "token-de-pruebas-0123456789"));

		assertThat(new ConectorOse(ose, series).probar(hacia).ok()).isFalse();
	}
}
