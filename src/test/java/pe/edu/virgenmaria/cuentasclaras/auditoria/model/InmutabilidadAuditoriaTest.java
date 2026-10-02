package pe.edu.virgenmaria.cuentasclaras.auditoria.model;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import pe.edu.virgenmaria.cuentasclaras.CuentasClarasApplication;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.SelladorAuditoria;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaJpa;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La bitácora es de solo inserción, también para el propio código de la aplicación.
 */
@PruebaJpa
@Import({ AuditoriaService.class, SelladorAuditoria.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InmutabilidadAuditoriaTest {

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaccion;

	@PersistenceContext
	private EntityManager em;

	private Long idEvento;

	@BeforeEach
	void registrarUnEvento() {
		LimpiezaBaseDatos.limpiar(jdbc);
		idEvento = auditoria.registrar(Actor.sistema(1L), AccionAuditoria.USUARIO_CREADO, "usuario", "1", null,
				"activo", "original").getId();
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void editarUnEventoCargadoNoCambiaLaBase() {
		transaccion.executeWithoutResult(estado -> {
			EventoAuditoria evento = em.find(EventoAuditoria.class, idEvento);
			ReflectionTestUtils.setField(evento, "detalle", "alterado");
			ReflectionTestUtils.setField(evento, "valorNuevo", "inactivo");
			em.flush();
		});

		assertThat(detalleEnBase()).isEqualTo("original");
		assertThat(jdbc.queryForObject("SELECT valor_nuevo FROM evento_auditoria WHERE id = ?", String.class, idEvento))
				.isEqualTo("activo");
	}

	@Test
	void borrarConEntityManagerLanzaExcepcion() {
		assertThatThrownBy(() -> transaccion.executeWithoutResult(
				estado -> em.remove(em.find(EventoAuditoria.class, idEvento))))
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessageContaining("solo inserción");

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE id = ?", Long.class, idEvento))
				.isEqualTo(1);
	}

	@Test
	void actualizarConJpqlLanzaExcepcion() {
		assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> em
				.createQuery("update EventoAuditoria e set e.detalle = 'alterado' where e.id = :id")
				.setParameter("id", idEvento)
				.executeUpdate()))
				.isInstanceOf(RuntimeException.class)
				.satisfies(e -> assertThat(e.toString().toLowerCase(Locale.ROOT)).contains("immutable"));

		assertThat(detalleEnBase()).isEqualTo("original");
	}

	@Test
	void repositorioNoTieneMetodosDeBorradoNiEdicion() {
		assertThat(CrudRepository.class.isAssignableFrom(EventoAuditoriaRepository.class)).isFalse();
		assertThat(Arrays.stream(EventoAuditoriaRepository.class.getMethods()).map(Method::getName))
				.isNotEmpty()
				.noneMatch(nombre -> nombre.startsWith("delete") || nombre.startsWith("remove")
						|| nombre.startsWith("update") || nombre.startsWith("saveAll"));
	}

	@Test
	void noExistenEndpointsPostPutDeleteSobreAuditoriaSalvoVerificar() throws Exception {
		Set<String> permitidos = Set.of("POST /auditoria/verificar-integridad");
		ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false);
		escaner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

		List<String> controladores = new ArrayList<>();
		List<String> violaciones = new ArrayList<>();
		for (BeanDefinition definicion : escaner.findCandidateComponents(
				ClassUtils.getPackageName(CuentasClarasApplication.class))) {
			Class<?> clase = Class.forName(definicion.getBeanClassName());
			controladores.add(clase.getSimpleName());
			RequestMapping deClase = AnnotatedElementUtils.findMergedAnnotation(clase, RequestMapping.class);
			String[] prefijos = deClase == null || deClase.path().length == 0 ? new String[] { "" } : deClase.path();
			for (Method metodo : clase.getDeclaredMethods()) {
				RequestMapping mapeo = AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class);
				if (mapeo == null) {
					continue;
				}
				String[] rutas = mapeo.path().length == 0 ? new String[] { "" } : mapeo.path();
				RequestMethod[] verbos = mapeo.method().length == 0 ? RequestMethod.values() : mapeo.method();
				for (String prefijo : prefijos) {
					for (String ruta : rutas) {
						String completa = prefijo + ruta;
						if (!completa.startsWith("/auditoria")) {
							continue;
						}
						for (RequestMethod verbo : verbos) {
							String endpoint = verbo + " " + completa;
							if (verbo != RequestMethod.GET && verbo != RequestMethod.HEAD
									&& verbo != RequestMethod.OPTIONS && !permitidos.contains(endpoint)) {
								violaciones.add(clase.getSimpleName() + "." + metodo.getName() + ": " + endpoint);
							}
						}
					}
				}
			}
		}

		assertThat(controladores).as("el escaneo encuentra los controladores").contains("InicioController");
		assertThat(violaciones).isEmpty();
	}

	private String detalleEnBase() {
		return jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE id = ?", String.class, idEvento);
	}
}
