package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 11, tanda 1 (H2 del diseño): los procesos solo recorren los colegios que operan (preparación y producción); la
 * huella diaria y el resumen al correo de control también los suspendidos; el despacho de mensajes, todos menos los
 * suprimidos (y la plataforma al final). Servicio puro.
 */
class RecorridoColegiosTest {

	private final ColegioRepository repositorio = mock(ColegioRepository.class);

	private final RecorridoColegios recorrido = new RecorridoColegios(repositorio);

	@BeforeEach
	void preparar() {
		List<Colegio> clientes = List.of(colegio(1, EstadoColegio.PREPARACION), colegio(2, EstadoColegio.PRODUCCION),
				colegio(3, EstadoColegio.BAJA), colegio(4, EstadoColegio.SUSPENDIDO), colegio(5, EstadoColegio.CERRADO),
				colegio(6, EstadoColegio.SUPRIMIDO));
		when(repositorio.findByTipoAndActivoTrueOrderByIdAsc(TipoColegio.COLEGIO)).thenReturn(clientes);
		Colegio plataforma = colegio(99, EstadoColegio.PRODUCCION);
		when(plataforma.isActivo()).thenReturn(true);
		when(repositorio.findFirstByTipo(TipoColegio.PLATAFORMA)).thenReturn(Optional.of(plataforma));
	}

	@Test
	void losProcesosRecorrenSoloLosQueOperan() {
		assertThat(recorrido.activos()).containsExactly(1L, 2L);
	}

	@Test
	void laBitacoraSumaLosSuspendidos() {
		assertThat(recorrido.conBitacora()).containsExactly(1L, 2L, 4L);
	}

	@Test
	void elDespachoRecorreTodosMenosLosSuprimidosYLaPlataformaAlFinal() {
		assertThat(recorrido.paraDespacho()).containsExactly(1L, 2L, 3L, 4L, 5L);
		assertThat(recorrido.conPlataforma()).containsExactly(1L, 2L, 3L, 4L, 5L, 99L);
	}

	private static Colegio colegio(long id, EstadoColegio estado) {
		Colegio c = mock(Colegio.class);
		when(c.getId()).thenReturn(id);
		when(c.getEstado()).thenReturn(estado);
		return c;
	}
}
