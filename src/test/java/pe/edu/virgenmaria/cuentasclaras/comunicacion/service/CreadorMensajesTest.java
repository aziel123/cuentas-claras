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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 5: el destino de un mensaje es SIEMPRE el contacto registrado; la clave es idempotente; un contacto que también
 * es del personal no recibe avisos sin la aprobación de otra persona (G6); sin WhatsApp, por correo.
 */
class CreadorMensajesTest {

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

	@Test
	void destinoEsElContactoRegistrado() {
		List<Mensaje> creados = creador.paraApoderado(apoderado(3, "+51987654321", "rosa@correo.pe", null), PAGO, false,
				null);

		assertThat(creados).singleElement().satisfies(m -> {
			assertThat(m.getCanal()).isEqualTo(CanalMensaje.WHATSAPP);
			assertThat(m.getDestino()).isEqualTo("+51987654321");
			assertThat(m.getDestinatarioTipo()).isEqualTo(DestinatarioTipo.APODERADO);
			assertThat(m.getApoderadoId()).isEqualTo(3L);
			assertThat(m.getFamiliaId()).isEqualTo(7L);
			assertThat(m.getClave()).isEqualTo("PAGO_REGISTRADO:pago:15:APODERADO:3:WHATSAPP");
			assertThat(m.parametrosLista()).containsExactlyElementsOf(PAGO.parametros());
		});
		// Ambos canales (anulación y descuento): también al correo registrado.
		assertThat(creador.paraApoderado(apoderado(4, "+51912345678", "pedro@correo.pe", null), PAGO, true, null))
				.extracting(Mensaje::getDestino).containsExactly("+51912345678", "pedro@correo.pe");
	}

	@Test
	void claveRepetidaNoDuplica() {
		Apoderado rosa = apoderado(3, "+51987654321", null, null);
		Mensaje primero = creador.paraApoderado(rosa, PAGO, false, null).getFirst();
		Mensaje segundo = creador.paraApoderado(rosa, PAGO, false, null).getFirst();

		assertThat(segundo).isSameAs(primero);
		verify(mensajes, org.mockito.Mockito.times(1)).saveAndFlush(any(Mensaje.class));
	}

	@Test
	void contactoDelPersonalSinAprobacionNoRecibeMensajes() {
		when(usuarios.esContactoDelPersonal("+51966000111")).thenReturn(true);

		assertThat(creador.paraApoderado(apoderado(3, "+51966000111", null, null), PAGO, true, null)).isEmpty();
		verify(mensajes, never()).saveAndFlush(any(Mensaje.class));
		// Si otra persona aprobó ese contacto (personal que también es madre o padre), sí recibe.
		assertThat(creador.paraApoderado(apoderado(5, "+51966000111", null, 44L), PAGO, true, null)).hasSize(1);
	}

	@Test
	void sinWhatsappVaPorCorreo() {
		assertThat(creador.paraApoderado(apoderado(3, null, "rosa@correo.pe", null), PAGO, false, null))
				.singleElement().satisfies(m -> {
					assertThat(m.getCanal()).isEqualTo(CanalMensaje.CORREO);
					assertThat(m.getDestino()).isEqualTo("rosa@correo.pe");
				});
		// Con el conector de WhatsApp apagado, si hay correo, sale por correo.
		when(whatsapp.getIfAvailable()).thenReturn(null);
		assertThat(creador.paraApoderado(apoderado(4, "+51912345678", "pedro@correo.pe", null), PAGO, false, null))
				.extracting(Mensaje::getCanal).containsExactly(CanalMensaje.CORREO);
	}

	@Test
	void losParametrosNuncaLlevanElEnlaceDeActivacion() {
		CreadorMensajes.Contenido conEnlace = new CreadorMensajes.Contenido(TipoMensaje.PAGO_REGISTRADO,
				PlantillaMensaje.PAGO_REGISTRADO, List.of("https://colegio.pe/activar/1/abc"), "pago", 1L);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> creador.paraApoderado(
				apoderado(3, "+51987654321", null, null), conEnlace, false, null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
