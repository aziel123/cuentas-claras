package pe.edu.virgenmaria.cuentasclaras.comunicacion.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.HuellaGuardada;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.EnlaceActivacion;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 5 (G12): el mensaje solo cambia su estado de envío y entrega; su destino, su texto (plantilla y parámetros), su
 * destinatario y su origen no cambian ni por la entidad ({@code updatable = false}, sin setters) ni por SQL (GRANT por
 * columna: 1143). El enlace no cambia su mensaje ni su propósito. La huella diaria es de solo inserción.
 */
class InmutabilidadMensajesTest {

	private static String permisos() throws IOException {
		return Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"));
	}

	@Test
	void lasColumnasActualizablesDelMensajeSonExactamenteLasDelGrant() throws IOException {
		assertThat(ColumnasActualizables.de(Mensaje.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "mensaje"))
				.doesNotContain("destino", "parametros", "plantilla", "tipo", "canal", "apoderado_id", "familia_id",
						"usuario_id", "clave", "entidad", "entidad_id", "respaldo_de_id");
	}

	@Test
	void elEnlaceNoCambiaSuMensajeNiSuProposito() throws IOException {
		assertThat(ColumnasActualizables.de(EnlaceActivacion.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "enlace_activacion"))
				.doesNotContain("mensaje_id", "proposito", "hash_token", "usuario_id", "vence_en");
	}

	@Test
	void laHuellaEsDeSoloInsercion() throws IOException {
		assertThat(permisos()).contains("GRANT INSERT ON cuentasclaras.huella_bitacora TO 'cc_sistema'@'%';")
				.doesNotContainPattern("GRANT [^;]*UPDATE[^;]*ON cuentasclaras\\.huella_bitacora")
				.doesNotContainPattern("GRANT [^;]*DELETE[^;]*ON cuentasclaras\\.(mensaje|huella_bitacora)");
		assertThat(Arrays.stream(HuellaGuardada.class.getDeclaredMethods()).map(Method::getName))
				.contains("impedirEdicion", "impedirBorrado");
	}

	@Test
	void elMensajeNoTieneSettersYNoRetrocede() {
		assertThat(Arrays.stream(Mensaje.class.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
				.map(Method::getName)).noneMatch(n -> n.startsWith("set"));
		Mensaje m = Mensaje.nuevo("k", TipoMensaje.PAGO_REGISTRADO, PlantillaMensaje.PAGO_REGISTRADO,
				new Mensaje.Destinatario(DestinatarioTipo.APODERADO, 1L, 2L, null, CanalMensaje.WHATSAPP, "+51987654321"),
				List.of("a"), "pago", 3L, null, null);
		LocalDateTime ahora = LocalDateTime.of(2026, 10, 5, 10, 0);
		m.marcarEnviado(ProveedorMensajeria.WHATSAPP_CLOUD, "wamid.1", ahora);
		assertThat(m.leido(ahora)).isTrue();
		assertThat(m.entregado(ahora)).isFalse();
		assertThat(m.getEstado()).isEqualTo(EstadoMensaje.LEIDO);
		assertThatThrownBy(() -> m.marcarEnviado(ProveedorMensajeria.SIMULADO, "otro", ahora))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> m.adelantar(ahora)).isInstanceOf(IllegalStateException.class);
	}
}
