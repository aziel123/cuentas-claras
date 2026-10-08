package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.DestinatarioTipo;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.repository.MensajeRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * QA sprint 5 (tanda 1): bordes del outbox. Dado un apoderado con contactos de distintos tipos, cuando se crea un aviso
 * financiero o su respaldo, entonces el aviso llega al contacto registrado y legítimo, una sola vez.
 */
class CreadorMensajesQaTest {

	private final MensajeRepository mensajes = mock(MensajeRepository.class);

	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

	@SuppressWarnings("unchecked")
	private final ObjectProvider<ProveedorWhatsApp> whatsapp = mock(ObjectProvider.class);

	private final Map<String, Mensaje> guardados = new HashMap<>();

	private CreadorMensajes creador;

	private static final CreadorMensajes.Contenido PAGO = new CreadorMensajes.Contenido(TipoMensaje.PAGO_REGISTRADO,
			PlantillaMensaje.PAGO_REGISTRADO, List.of("S/ 450.00", "Pensión de marzo de Mateo", "B001-00000001",
					"02/10/2026 10:00", "Efectivo", "Lucía R."), "pago", 15L);

	@BeforeEach
	void preparar() {
		creador = new CreadorMensajes(mensajes, usuarios, whatsapp);
		when(whatsapp.getIfAvailable()).thenReturn(mock(ProveedorWhatsApp.class));
		when(mensajes.findByClave(anyString())).thenAnswer(i -> Optional.ofNullable(guardados.get(i.<String>getArgument(0))));
		when(mensajes.saveAndFlush(any(Mensaje.class))).thenAnswer(i -> {
			Mensaje m = i.getArgument(0);
			guardados.put(m.getClave(), m);
			return m;
		});
	}

	private static Apoderado apoderado(long id, String celular, String correo, Long contactoAprobado) {
		Apoderado a = mock(Apoderado.class);
		Familia familia = mock(Familia.class);
		when(familia.getId()).thenReturn(7L);
		when(a.getId()).thenReturn(id);
		when(a.getFamilia()).thenReturn(familia);
		when(a.getTelefonoWhatsapp()).thenReturn(celular);
		when(a.getCorreo()).thenReturn(correo);
		when(a.getContactoSolicitudId()).thenReturn(contactoAprobado);
		when(a.isActivo()).thenReturn(true);
		// Correcciones del sprint 5: los contactos ya los verificó su titular (S5-A1) y, si hubo solicitud aprobada, la
		// aprobación vale para ESOS contactos (S5-M1).
		when(a.telefonoVerificado()).thenReturn(celular != null);
		when(a.correoVerificado()).thenReturn(correo != null);
		if (contactoAprobado != null) {
			when(a.getContactoAprobadoTelefono()).thenReturn(celular);
			when(a.getContactoAprobadoCorreo()).thenReturn(correo);
		}
		return a;
	}

	/**
	 * Dado que un empleado registró SU celular como el del apoderado (G6) y la familia tiene además su propio correo,
	 * cuando se registra un pago, entonces el aviso debe salir al correo de la familia: omitir el WhatsApp no puede
	 * dejar a la familia sin ningún aviso (el pago se registra igual y nadie se entera).
	 */
	@Test
	void debeAvisarPorCorreoCuandoElCelularRegistradoEsDelPersonalSinAprobacion() {
		when(usuarios.esContactoDelPersonal("+51966000111")).thenReturn(true);
		when(usuarios.esContactoDelPersonal("rosa@correo.pe")).thenReturn(false);

		List<Mensaje> creados = creador.paraApoderado(apoderado(3, "+51966000111", "rosa@correo.pe", null), PAGO, false,
				null);

		assertThat(creados).singleElement().satisfies(m -> {
			assertThat(m.getCanal()).isEqualTo(CanalMensaje.CORREO);
			assertThat(m.getDestino()).isEqualTo("rosa@correo.pe");
		});
	}

	@Test
	void debeAvisarPorCorreoCuandoElCelularEsDelPersonalYElAvisoVaPorAmbosCanales() {
		when(usuarios.esContactoDelPersonal("+51966000111")).thenReturn(true);

		List<Mensaje> creados = creador.paraApoderado(apoderado(3, "+51966000111", "rosa@correo.pe", null), PAGO, true,
				null);

		assertThat(creados).extracting(Mensaje::getCanal).containsExactly(CanalMensaje.CORREO);
	}

	@Test
	void debeCrearElRespaldoPorCorreoDeUnWhatsappFallidoAlMismoApoderado() {
		Apoderado rosa = apoderado(3, "+51987654321", "rosa@correo.pe", null);
		Mensaje whatsappFallido = creador.paraApoderado(rosa, PAGO, false, null).getFirst();

		Optional<Mensaje> respaldo = creador.respaldo(whatsappFallido, rosa, null);

		assertThat(respaldo).hasValueSatisfying(m -> {
			assertThat(m.getCanal()).isEqualTo(CanalMensaje.CORREO);
			assertThat(m.getDestino()).isEqualTo("rosa@correo.pe");
			assertThat(m.getDestinatarioTipo()).isEqualTo(DestinatarioTipo.APODERADO);
			assertThat(m.getApoderadoId()).isEqualTo(3L);
			assertThat(m.getClave()).isEqualTo("PAGO_REGISTRADO:pago:15:APODERADO:3:CORREO");
			assertThat(m.parametrosLista()).containsExactlyElementsOf(PAGO.parametros());
		});
		// Una sola vez.
		assertThat(creador.respaldo(whatsappFallido, rosa, null)).isEmpty();
	}

	@Test
	void noDebeDuplicarElCorreoSiElAvisoYaSalioPorAmbosCanales() {
		Apoderado rosa = apoderado(3, "+51987654321", "rosa@correo.pe", null);
		List<Mensaje> ambos = creador.paraApoderado(rosa, PAGO, true, null);

		assertThat(creador.respaldo(ambos.getFirst(), rosa, null)).isEmpty();
		assertThat(guardados).hasSize(2);
	}

	@Test
	void noDebeCrearRespaldoDeUnCorreoNiSinCorreoRegistrado() {
		Apoderado soloCorreo = apoderado(3, null, "rosa@correo.pe", null);
		Mensaje correo = creador.paraApoderado(soloCorreo, PAGO, false, null).getFirst();
		assertThat(creador.respaldo(correo, soloCorreo, null)).isEmpty();

		Apoderado soloCelular = apoderado(4, "+51912345678", null, null);
		Mensaje whatsappFallido = creador.paraApoderado(soloCelular, PAGO, false, null).getFirst();
		assertThat(creador.respaldo(whatsappFallido, soloCelular, null)).isEmpty();
	}

	@Test
	void noDebeEnviarElRespaldoAUnCorreoDelPersonalSinAprobacion() {
		when(usuarios.esContactoDelPersonal("caja@colegio.pe")).thenReturn(true);
		Apoderado rosa = apoderado(3, "+51987654321", "caja@colegio.pe", null);
		Mensaje whatsappFallido = creador.paraApoderado(rosa, PAGO, false, null).getFirst();

		assertThat(creador.respaldo(whatsappFallido, rosa, null)).isEmpty();
	}

	@Test
	void laClaveDelRecordatorioDistingueCadaFechaDeVencimiento() {
		Apoderado rosa = apoderado(3, "+51987654321", null, null);
		CreadorMensajes.Contenido recordatorio = new CreadorMensajes.Contenido(TipoMensaje.RECORDATORIO_VENCIMIENTO,
				PlantillaMensaje.RECORDATORIO, List.of("la Pensión marzo 2027 de Mateo", "31/03/2027", "S/ 450.00",
						"A0001"), "familia", 7L);

		Mensaje marzo = creador.paraApoderado(rosa, recordatorio, false, "2027-03-31").getFirst();
		Mensaje abril = creador.paraApoderado(rosa, recordatorio, false, "2027-04-30").getFirst();
		Mensaje marzoOtraVez = creador.paraApoderado(rosa, recordatorio, false, "2027-03-31").getFirst();

		assertThat(marzo.getClave()).isNotEqualTo(abril.getClave()).endsWith(":2027-03-31");
		assertThat(marzoOtraVez).isSameAs(marzo);
		assertThat(guardados).hasSize(2);
	}
}
