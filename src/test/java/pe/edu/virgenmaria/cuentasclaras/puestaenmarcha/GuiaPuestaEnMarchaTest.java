package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.GuiaVista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.ListaVerificacion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.model.PasoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.GuiaPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.model.PasoPuestaEnMarcha.*;

/**
 * Sprint 11, tanda 1b (sección 3.14, decisión 206): el siguiente paso, lo que le toca a cada rol, lo que hace la plataforma,
 * el avance y la barra. Pura: arma la lista a mano (como la calcula EstadoPuestaEnMarcha) y aplica el catálogo.
 */
class GuiaPuestaEnMarchaTest {

	private static final Set<Rol> PROMOTORIA = EnumSet.of(Rol.PROMOTOR);

	private static final Set<Rol> DIRECCION = EnumSet.of(Rol.DIRECTOR);

	private static final Set<Rol> ADMINISTRACION = EnumSet.of(Rol.ADMINISTRACION);

	private static final Set<Rol> CAJA = EnumSet.of(Rol.CAJA);

	private static final Set<Rol> DOCENTE = EnumSet.of(Rol.DOCENTE);

	/** Una lista con estos pasos hechos (el resto pendientes; los opcionales, OPCIONAL), año 2026 si el paso 3 está hecho. */
	private static ListaVerificacion lista(PasoPuestaEnMarcha... hechos) {
		Set<PasoPuestaEnMarcha> listos = hechos.length == 0 ? EnumSet.noneOf(PasoPuestaEnMarcha.class) : EnumSet.of(hechos[0], hechos);
		List<PuntoLista> puntos = Arrays.stream(PasoPuestaEnMarcha.values()).map(p -> new PuntoLista(p.clave(), p.titulo(),
				listos.contains(p) ? PuntoLista.Estado.HECHO : (p.obligatorio() ? PuntoLista.Estado.PENDIENTE
						: PuntoLista.Estado.OPCIONAL), p.obligatorio() ? "Conteo de " + p.clave() : null, p.ruta())).toList();
		return new ListaVerificacion(1L, "Colegio", EstadoColegio.PREPARACION, EstadoPuestaEnMarcha.conCatalogo(puntos),
				listos.contains(ANIO) ? 2026 : null);
	}

	@Test
	void colegioRecienCreadoEmpiezaPorLosDatos() {
		GuiaVista guia = GuiaPuestaEnMarcha.guia(lista(), PROMOTORIA);
		assertThat(guia.siguiente().paso()).isEqualTo(DATOS);
		assertThat(guia.siguienteEsTuyo()).isTrue();
		assertThat(guia.siguiente().enlace()).isEqualTo("/colegio/marca");

		assertThat(GuiaPuestaEnMarcha.guia(lista(DATOS), PROMOTORIA).siguiente().paso()).isEqualTo(EQUIPO);
		assertThat(GuiaPuestaEnMarcha.guia(lista(DATOS, EQUIPO), PROMOTORIA).siguiente().paso()).isEqualTo(ANIO);
	}

	@Test
	void conElAnioHechoYSinSeccionesElSiguienteEsElPaso4ConElAnio() {
		GuiaVista guia = GuiaPuestaEnMarcha.guia(lista(DATOS, EQUIPO, ANIO), DIRECCION);
		assertThat(guia.siguiente().paso()).isEqualTo(SECCIONES);
		assertThat(guia.siguiente().accion()).isEqualTo("Crear las secciones del año 2026");
		assertThat(guia.siguiente().conBoton()).isTrue();
	}

	@Test
	void unPasoConSuDependenciaPendienteNuncaEsElSiguienteNiElTuyo() {
		// Sin año: secciones, pensiones y saldo inicial dependen del paso 3; alumnos, de secciones.
		ListaVerificacion sinAnio = lista(DATOS, EQUIPO);
		for (Set<Rol> roles : List.of(PROMOTORIA, DIRECCION, ADMINISTRACION)) {
			GuiaVista guia = GuiaPuestaEnMarcha.guia(sinAnio, roles);
			assertThat(guia.siguiente().paso()).isEqualTo(ANIO);
			if (guia.tuyo() != null) {
				assertThat(guia.tuyo().paso()).isNotIn(SECCIONES, PENSIONES, ALUMNOS, SALDO_INICIAL);
			}
		}
		PuntoLista secciones = sinAnio.punto("secciones").orElseThrow();
		assertThat(secciones.bloqueadoPor()).isEqualTo(ANIO);
		assertThat(GuiaPuestaEnMarcha.vista(secciones, sinAnio, ADMINISTRACION, true).enlace()).isNull();
	}

	@Test
	void aCajaNoLeTocaNadaEnPreparacion() {
		for (ListaVerificacion l : List.of(lista(), lista(DATOS, EQUIPO, ANIO), lista(DATOS, EQUIPO, ANIO, SECCIONES))) {
			GuiaVista guia = GuiaPuestaEnMarcha.guia(l, CAJA);
			assertThat(guia.tuyo()).isNull();
			assertThat(guia.siguiente().conBoton()).isFalse();
			assertThat(guia.verLista()).isFalse();
		}
	}

	@Test
	void aDireccionLeTocaElAvisoDePrivacidadCuandoElSiguienteEsDeAdministracion() {
		// Secciones hechas: el siguiente obligatorio desbloqueado de Administración o Promotoría es el de las pensiones.
		GuiaVista guia = GuiaPuestaEnMarcha.guia(lista(DATOS, EQUIPO, ANIO, SECCIONES), DIRECCION);
		assertThat(guia.siguiente().paso()).isEqualTo(PENSIONES);
		assertThat(guia.siguiente().conBoton()).isFalse();
		assertThat(guia.tuyo().paso()).isEqualTo(PRIVACIDAD);
		assertThat(guia.tuyo().accion()).isEqualTo("Aprobar el aviso de privacidad");
		assertThat(guia.tuyo().enlace()).isEqualTo("/colegio/privacidad");
	}

	@Test
	void laPlataformaVaAparteYNuncaEsElSiguiente() {
		ListaVerificacion casi = lista(DATOS, EQUIPO, ANIO, SECCIONES, PENSIONES, ALUMNOS, SALDO_INICIAL, OSE,
				CUENTA_BANCARIA, PRIVACIDAD, CONSERVACION);
		GuiaVista guia = GuiaPuestaEnMarcha.guia(casi, PROMOTORIA);
		assertThat(guia.siguiente()).isNull();
		assertThat(guia.esperandoPlataforma()).extracting(GuiaVista.PasoGuia::paso)
				.containsExactly(MENSAJERIA, CORREO_CONTROL, RESPALDO);
		assertThat(guia.soloEsperando()).isTrue();
		for (Set<Rol> roles : List.of(PROMOTORIA, DIRECCION, ADMINISTRACION, CAJA, DOCENTE)) {
			GuiaVista otra = GuiaPuestaEnMarcha.guia(lista(), roles);
			assertThat(otra.siguiente().paso()).isNotIn(MENSAJERIA, CORREO_CONTROL, RESPALDO);
		}
	}

	@Test
	void elAvanceCuentaLosObligatoriosYAgregaLosOpcionales() {
		GuiaVista nueva = GuiaPuestaEnMarcha.guia(lista(), PROMOTORIA);
		// Sprint 11, tanda 2 (AP2-9): 16 pasos, 14 obligatorios (la autorización de conservación es el 16).
		assertThat(nueva.textoAvance()).isEqualTo("Tu colegio está en preparación: faltan 14 de 16 pasos (y 2 opcionales).");
		assertThat(nueva.obligatoriosHechos()).isZero();
		assertThat(nueva.maximoBarra()).isEqualTo(14);
		assertThat(nueva.etiquetaBarra()).isEqualTo("0 de 14 pasos obligatorios hechos");

		GuiaVista media = GuiaPuestaEnMarcha.guia(lista(DATOS, EQUIPO, ANIO, SECCIONES, PENSIONES, ALUMNOS, SALDO_INICIAL,
				PASARELA), PROMOTORIA);
		assertThat(media.pendientes()).isEqualTo(7);
		assertThat(media.textoAvance()).isEqualTo("Tu colegio está en preparación: faltan 7 de 16 pasos (y 1 opcional).");
		assertThat(media.etiquetaBarra()).isEqualTo("7 de 14 pasos obligatorios hechos");

		GuiaVista sinOpcionales = GuiaPuestaEnMarcha.guia(lista(DATOS, PASARELA, RECAUDACION), PROMOTORIA);
		assertThat(sinOpcionales.textoAvance()).isEqualTo("Tu colegio está en preparación: faltan 13 de 16 pasos.");
	}

	@Test
	void elDocenteRecibeLaGuiaSinConteos() {
		GuiaVista guia = GuiaPuestaEnMarcha.guia(lista(DATOS, EQUIPO, ANIO), DOCENTE);
		assertThat(guia.conConteos()).isFalse();
		assertThat(guia.siguiente().conteo()).isNull();
		assertThat(guia.proximos()).allMatch(p -> p.conteo() == null);
		assertThat(GuiaPuestaEnMarcha.guia(lista(DATOS, EQUIPO, ANIO), DIRECCION).siguiente().conteo()).isNotNull();
	}

	@Test
	void nuncaHayUnEnlaceAUnaRutaQueDaria403() {
		for (Set<Rol> roles : List.of(PROMOTORIA, DIRECCION, ADMINISTRACION, CAJA, DOCENTE)) {
			ListaVerificacion l = lista(DATOS, EQUIPO, ANIO, SECCIONES);
			for (PuntoLista p : l.puntos()) {
				String enlace = GuiaPuestaEnMarcha.vista(p, l, roles, true).enlace();
				if (enlace != null) {
					assertThat(pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp.puedeEntrar(roles, enlace))
							.as("%s: %s", roles, enlace).isTrue();
				}
			}
		}
	}
}
