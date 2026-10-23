package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.CambioDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.CorreccionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.RegistroTardioRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 9, tanda 2 (E5, E6, E10, E52): el mismo día corrige el docente (hasta el segundo aviso) y la familia recibe la
 * corrección si el aviso ya salió; otro día, o después del cierre, solo con la aprobación de OTRA persona de Dirección.
 */
class CorreccionAsistenciaTest extends PruebaAsistencia {

	@Test
	void despuesDelAvisoLaFamiliaRecibeLaCorreccion() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		alas(0, 10, 30);
		assertThat(pasadaDeAvisos()).isPositive();
		assertThat(parametros("ASISTENCIA_DIA")).singleElement().asString().contains("Mateo: falta");
		alas(0, 11, 0);
		corregirHoy(datos.mateo(), EstadoAsistencia.PRESENTE);
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "ASISTENCIA_CORREGIDA_DESPUES_DEL_AVISO").get("VALOR_NUEVO"))
				.isEqualTo("PRESENTE");
		alas(0, 16, 0);
		assertThat(pasadaDeAvisos()).isPositive();
		assertThat(parametros("ASISTENCIA_CORREGIDA")).singleElement().asString().contains("Mateo")
				.contains("presente").contains("falta");
	}

	@Test
	void quedaEnLaListaDeDireccion() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		alas(0, 9, 0);
		corregirHoy(datos.valeria(), EstadoAsistencia.TARDE);
		alas(0, 11, 0);
		corregirHoy(datos.mateo(), EstadoAsistencia.PRESENTE);
		List<CambioDelDia> cambios = como(direccion(), () -> direccion.cambiosDelDia(null));
		assertThat(cambios).hasSize(2);
		assertThat(cambios).filteredOn(CambioDelDia::despuesDelAviso).singleElement()
				.satisfies(c -> {
					assertThat(c.antes()).isEqualTo("Falta");
					assertThat(c.ahora()).isEqualTo("Presente");
					assertThat(c.motivo()).isEqualTo(EscenarioAsistencia.MOTIVO);
				});
	}

	@Test
	void otroDiaExigeAprobacion() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		Long marca = EscenarioAsistencia.marcaVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES);
		alas(1, 8, 0);
		assertThatThrownBy(() -> como(tutora(), () -> asistencia.corregirHoy(marca, new CorreccionRequest(
				EstadoAsistencia.PRESENTE, null, EscenarioAsistencia.MOTIVO)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("pide la corrección");
		Long solicitud = como(tutora(), () -> asistencia.pedirCorreccion(marca, new CorreccionRequest(
				EstadoAsistencia.PRESENTE, null, EscenarioAsistencia.MOTIVO)));
		// Mientras no se apruebe, nada cambia.
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES)).isEqualTo("FALTA");
		// Quien pide no aprueba: el docente ni siquiera ve la bandeja.
		assertThatThrownBy(() -> como(tutora(), () -> bandeja.aprobar(solicitud, null)))
				.isInstanceOf(AccessDeniedException.class);
		EscenarioAprobaciones.aprueba(direccion(), bandeja, jdbc, "marca_asistencia", marca);
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES))
				.isEqualTo("PRESENTE");
		assertThat(jdbc.queryForObject("SELECT origen FROM marca_asistencia WHERE solicitud_id = ?", String.class,
				solicitud)).isEqualTo("CORRECCION_APROBADA");
		assertThat(jdbc.queryForObject("SELECT creado_por FROM marca_asistencia WHERE solicitud_id = ?", String.class,
				solicitud)).isEqualTo("director");
	}

	@Test
	void despuesDelCierreVaPorSolicitud() {
		registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		Long marca = EscenarioAsistencia.marcaVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES);
		alas(0, 16, 1);
		assertThatThrownBy(() -> como(tutora(), () -> asistencia.corregirHoy(marca, new CorreccionRequest(
				EstadoAsistencia.PRESENTE, null, EscenarioAsistencia.MOTIVO)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("pide la corrección");
		HojaDelDia hoja = como(tutora(), () -> asistencia.hoja(datos.seccion()));
		assertThat(hoja.correccionAbierta()).isFalse();
		assertThat(como(tutora(), () -> asistencia.pedirCorreccion(marca, new CorreccionRequest(EstadoAsistencia.PRESENTE,
				null, EscenarioAsistencia.MOTIVO)))).isPositive();
	}

	@Test
	void elRegistroTardioPasaPorLaBandeja() {
		alas(1, 8, 0);
		LocalDate lunesPasado = EscenarioAsistencia.MARTES;
		Long solicitud = como(tutora(), () -> asistencia.pedirRegistroTardio(datos.seccion(),
				new RegistroTardioRequest(lunesPasado, "No hubo internet: se anotó en papel")));
		assertThatThrownBy(() -> como(tutora(), () -> asistencia.hojaTardia(datos.seccion(), solicitud)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no está aprobado");
		EscenarioAprobaciones.aprueba(direccion(), bandeja, jdbc, "seccion", datos.seccion());
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		HojaDelDia hoja = como(tutora(), () -> asistencia.hojaTardia(datos.seccion(), solicitud));
		assertThat(hoja.fecha()).isEqualTo(lunesPasado);
		como(tutora(), () -> asistencia.guardar(datos.seccion(), new pe.edu.virgenmaria.cuentasclaras.academico.dto
				.HojaRequest(EscenarioAsistencia.hoja(Map.of(datos.lucas(), EstadoAsistencia.FALTA), List.of(datos.mateo(),
						datos.valeria(), datos.lucas())).marcas(), solicitud)));
		assertThat(jdbc.queryForObject("SELECT origen FROM registro_asistencia WHERE fecha = ?", String.class, lunesPasado))
				.isEqualTo("TARDIO");
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.lucas(), lunesPasado)).isEqualTo("FALTA");
		// Un día ya registrado no se vuelve a pedir.
		assertThatThrownBy(() -> como(tutora(), () -> asistencia.pedirRegistroTardio(datos.seccion(),
				new RegistroTardioRequest(lunesPasado, "No hubo internet: se anotó en papel"))))
				.isInstanceOf(ReglaNegocioException.class);
	}
}
