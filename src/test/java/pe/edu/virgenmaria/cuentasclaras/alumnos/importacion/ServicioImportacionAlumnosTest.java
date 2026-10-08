package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearAnioEscolarRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.archivo;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.con;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.mateo;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.sebastian;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.valeria;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/** Importación sobre la base real: vista previa sin efectos, confirmación todo o nada, idempotencia y aislamiento. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@ExtendWith(OutputCaptureExtension.class)
class ServicioImportacionAlumnosTest {

	@Autowired
	private ServicioImportacionAlumnos servicio;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AnioEscolarRepository anios;

	@Autowired
	private PlatformTransactionManager transacciones;

	private Estructura escuela;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		escuela = EscenarioEscolar.crearEstructura(estructura);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void vistaPreviaNoGuardaNadaNiAudita() {
		long eventos = contar(jdbc, "evento_auditoria");

		VistaPreviaImportacion previa = previa(archivo(mateo(), valeria(), sebastian()));

		assertThat(previa.resumen()).extracting(ResumenImportacion::alumnosNuevos, ResumenImportacion::familiasNuevas,
				ResumenImportacion::apoderadosNuevos, ResumenImportacion::matriculasNuevas, ResumenImportacion::filasConErrores)
				.containsExactly(3, 2, 2, 3, 0);
		assertThat(previa.importadoAntesEn()).isNull();
		for (String tabla : new String[] { "alumno", "apoderado", "familia", "matricula", "importacion_alumnos" }) {
			assertThat(contar(jdbc, tabla)).as(tabla).isZero();
		}
		assertThat(contar(jdbc, "evento_auditoria")).isEqualTo(eventos);
	}

	@Test
	void confirmarConErroresEsRechazado() {
		VistaPreviaImportacion previa = previa(archivo(mateo(), con(valeria(), 1, "123")));
		assertThat(previa.resumen().filasConErrores()).isEqualTo(1);

		assertThatThrownBy(() -> servicio.confirmar(previa, previa.token()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Corrige los errores");
		assertThat(contar(jdbc, "alumno")).isZero();
	}

	@Test
	void reimportarElMismoArchivoNoDuplicaNada() {
		byte[] libro = archivo(mateo(), valeria(), sebastian());
		confirmar(previa(libro));
		long eventos = contar(jdbc, "evento_auditoria");

		VistaPreviaImportacion otraVez = previa(libro);

		assertThat(otraVez.resumen().sinCambios()).isTrue();
		assertThat(otraVez.resumen().alumnosSinCambios()).isEqualTo(3);
		assertThat(otraVez.importadoAntesEn()).isNotNull();
		assertThatThrownBy(() -> servicio.confirmar(otraVez, otraVez.token()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("No hay nada que importar");
		assertThat(contar(jdbc, "alumno")).isEqualTo(3);
		assertThat(contar(jdbc, "matricula")).isEqualTo(3);
		assertThat(contar(jdbc, "familia")).isEqualTo(2);
		assertThat(contar(jdbc, "evento_auditoria")).isEqualTo(eventos);
	}

	/**
	 * Auditoría A4: antes, reimportar el Excel con otro celular cambiaba el contacto de un apoderado ya registrado sin
	 * que nadie más lo aprobara (y desviaba los avisos de pago). Ahora se muestra como «requiere solicitud» y no se
	 * aplica.
	 */
	@Test
	void reimportarConUnCelularCambiadoNoCambiaElContactoYPideSolicitud() {
		confirmar(previa(archivo(mateo(), valeria(), sebastian())));
		long eventos = contar(jdbc, "evento_auditoria");

		VistaPreviaImportacion previa = previa(archivo(con(mateo(), 15, "999888777"), con(valeria(), 15, "999888777"),
				sebastian()));

		assertThat(previa.resumen()).extracting(ResumenImportacion::alumnosActualizados,
				ResumenImportacion::alumnosSinCambios, ResumenImportacion::apoderadosActualizados,
				ResumenImportacion::alumnosNuevos).containsExactly(0, 3, 0, 0);
		assertThat(previa.conCambiosPorSolicitar()).isTrue();
		assertThat(previa.conCambios()).singleElement().satisfies(p -> assertThat(p.cambios()).containsExactly(
				new CambioFila("Celular de Rosa Huamán Ccori", "+51 *** *** 321", "+51 *** *** 777", true)));
		assertThatThrownBy(() -> servicio.confirmar(previa, previa.token())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("No hay nada que importar");
		assertThat(jdbc.queryForObject("SELECT telefono_whatsapp FROM apoderado WHERE numero_documento = '45678912'",
				String.class)).isEqualTo("+51987654321");
		assertThat(contar(jdbc, "evento_auditoria")).isEqualTo(eventos);
	}

	/** Un nombre corregido sí se aplica; el celular que viene en la misma fila, no. */
	@Test
	void reimportarAplicaElNombreDelApoderadoPeroNoSuContacto() {
		confirmar(previa(archivo(mateo(), valeria(), sebastian())));

		VistaPreviaImportacion previa = previa(archivo(con(con(mateo(), 15, "999888777"), 13, "Rosa María"),
				con(con(valeria(), 15, "999888777"), 13, "Rosa María"), sebastian()));

		assertThat(previa.errores()).isEmpty();
		assertThat(previa.resumen().apoderadosActualizados()).isEqualTo(1);
		assertThat(previa.conCambios()).singleElement().satisfies(p -> assertThat(p.cambios())
				.extracting(CambioFila::requiereSolicitud).containsExactlyInAnyOrder(false, true));
		confirmar(previa);
		assertThat(jdbc.queryForMap("SELECT nombres, telefono_whatsapp FROM apoderado WHERE numero_documento = "
				+ "'45678912'")).containsEntry("nombres", "Rosa María").containsEntry("telefono_whatsapp", "+51987654321");
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'APODERADO_CONTACTO_CAMBIADO'")).isZero();
		assertThat(contar(jdbc, "solicitud_cambio")).isZero();
	}

	/** Doble clic en «Confirmar e importar»: la segunda vez ve los datos cambiados y no duplica nada. */
	@Test
	void confirmarLaMismaRevisionDosVecesNoDuplica() {
		VistaPreviaImportacion previa = previa(archivo(mateo(), valeria(), sebastian()));
		confirmar(previa);

		assertThatThrownBy(() -> confirmar(previa)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Los datos cambiaron desde la revisión");
		assertThat(contar(jdbc, "alumno")).isEqualTo(3);
		assertThat(contar(jdbc, "matricula")).isEqualTo(3);
		assertThat(contar(jdbc, "importacion_alumnos")).isEqualTo(1);
	}

	@Test
	void confirmarLaMismaRevisionDosVecesEnParaleloNoDuplica() throws Exception {
		VistaPreviaImportacion previa = previa(archivo(mateo(), valeria(), sebastian()));
		// Dos usuarios distintos (el límite de una importación por usuario no interviene): el año bloqueado decide.
		var primero = UsuariosDePrueba.autenticado(1L, 1L, "usuario.prueba", "Uno", false,
				java.util.EnumSet.of(Rol.ADMINISTRACION));
		java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(2);
		java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
		try {
			java.util.List<java.util.concurrent.Future<String>> resultados = new java.util.ArrayList<>();
			for (int i = 0; i < 2; i++) {
				resultados.add(hilos.submit(() -> {
					UsuariosDePrueba.iniciarSesion(primero);
					salida.await();
					try {
						servicio.confirmar(previa, previa.token());
						return "ok";
					}
					catch (ReglaNegocioException e) {
						return e.getMessage();
					}
					finally {
						SecurityContextHolder.clearContext();
					}
				}));
			}
			salida.countDown();
			java.util.List<String> mensajes = new java.util.ArrayList<>();
			for (var r : resultados) {
				mensajes.add(r.get(30, java.util.concurrent.TimeUnit.SECONDS));
			}
			assertThat(mensajes).containsOnlyOnce("ok");
			assertThat(mensajes).filteredOn(m -> !m.equals("ok")).singleElement().asString()
					.containsAnyOf("Los datos cambiaron", "importación en proceso");
		}
		finally {
			hilos.shutdownNow();
		}
		assertThat(contar(jdbc, "alumno")).isEqualTo(3);
		assertThat(contar(jdbc, "importacion_alumnos")).isEqualTo(1);
	}

	/**
	 * Auditoría B3: un usuario no satura el servidor con varias importaciones a la vez. Determinista: otra transacción
	 * tiene el año bloqueado, así la confirmación queda «en proceso» esperando el bloqueo.
	 */
	@Test
	void unaSolaImportacionEnProcesoPorUsuario() throws Exception {
		byte[] libro = archivo(mateo(), valeria(), sebastian());
		VistaPreviaImportacion previa = previa(libro);
		var usuario = UsuariosDePrueba.autenticado(Rol.ADMINISTRACION);
		java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(1);
		java.util.concurrent.CountDownLatch bloqueado = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch soltar = new java.util.concurrent.CountDownLatch(1);
		try {
			var candado = hilos.submit(() -> {
				// Otra persona del mismo colegio (el colegio se fija al abrir la transacción).
				UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(1L, 2L, "otra.persona", "Otra", false,
						java.util.EnumSet.of(Rol.ADMINISTRACION)));
				new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
					assertThat(anios.bloquear(escuela.anio2026())).isPresent();
					bloqueado.countDown();
					try {
						soltar.await(20, java.util.concurrent.TimeUnit.SECONDS);
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				});
				SecurityContextHolder.clearContext();
			});
			bloqueado.await(10, java.util.concurrent.TimeUnit.SECONDS);
			java.util.concurrent.atomic.AtomicReference<Object> resultado = new java.util.concurrent.atomic.AtomicReference<>();
			Thread confirmacion = new Thread(() -> {
				UsuariosDePrueba.iniciarSesion(usuario);
				try {
					servicio.confirmar(previa, previa.token());
					resultado.set("ok");
				}
				catch (RuntimeException e) {
					resultado.set(e);
				}
				finally {
					SecurityContextHolder.clearContext();
				}
			});
			confirmacion.start();
			// Espera a que la confirmación esté detenida en el bloqueo del año (ya «en proceso»).
			for (int intento = 0; intento < 200 && confirmacion.getState() != Thread.State.TIMED_WAITING
					&& confirmacion.getState() != Thread.State.WAITING; intento++) {
				Thread.sleep(25);
			}
			assertThat(resultado.get()).as("la confirmación no debe haber terminado").isNull();

			assertThatThrownBy(() -> previa(libro)).isInstanceOf(ReglaNegocioException.class)
					.hasMessage("Ya tienes una importación en proceso. Espera a que termine y vuelve a intentarlo.");
			soltar.countDown();
			confirmacion.join(20_000);
			assertThat(resultado.get()).isEqualTo("ok");
			candado.get(20, java.util.concurrent.TimeUnit.SECONDS);
		}
		finally {
			soltar.countDown();
			hilos.shutdownNow();
		}
		// Terminada, se puede volver a revisar otro archivo.
		assertThat(previa(libro).resumen().alumnosSinCambios()).isEqualTo(3);
	}

	@Test
	void hermanosQuedanEnLaMismaFamilia() {
		confirmar(previa(archivo(mateo(), valeria(), sebastian())));

		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT familia_id) FROM alumno WHERE apellido_paterno = 'Quispe'",
				Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT responsable_pago_id) FROM alumno "
				+ "WHERE apellido_paterno = 'Quispe'", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT nombre FROM familia f JOIN alumno a ON a.familia_id = f.id "
				+ "WHERE a.numero_documento = '78451236'", String.class)).isEqualTo("Familia Quispe Huamán");
		assertThat(jdbc.queryForList("SELECT fecha_matricula FROM matricula", java.sql.Date.class))
				.containsOnly(java.sql.Date.valueOf("2026-03-02"));
	}

	@Test
	void alumnoDeOtraFamiliaEsErrorYNoSeMueve() {
		confirmar(previa(archivo(mateo(), sebastian())));
		Long familiaMateo = jdbc.queryForObject("SELECT familia_id FROM alumno WHERE numero_documento = '78451236'",
				Long.class);

		// Mateo con Pedro (de la familia Flores) como apoderado.
		VistaPreviaImportacion previa = previa(archivo(con(con(con(con(con(con(mateo(), 10, "41235678"), 11, "Flores"),
				12, "Díaz"), 13, "Pedro"), 14, "Padre"), 15, "912345678")));

		assertThat(previa.errores()).singleElement().satisfies(e -> assertThat(e.mensaje())
				.contains("Mateo Quispe Huamán es de otra familia (Familia Quispe Huamán)", "desde su ficha"));
		assertThat(jdbc.queryForObject("SELECT familia_id FROM alumno WHERE numero_documento = '78451236'", Long.class))
				.isEqualTo(familiaMateo);
		// Y con un apoderado que no está registrado, tampoco: se agrega desde la familia.
		VistaPreviaImportacion conApoderadoNuevo = previa(archivo(con(mateo(), 10, "40000099")));
		assertThat(conApoderadoNuevo.errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("ya está registrado y este apoderado no"));
	}

	@Test
	void seccionInexistenteYOtraSeccionDelAlumnoSonErrores() {
		confirmar(previa(archivo(mateo())));
		VistaPreviaImportacion previa = previa(archivo(con(mateo(), 8, "B"), con(valeria(), 8, "Z")));

		assertThat(previa.errores()).extracting(ErrorFila::mensaje).containsExactly(
				"Mateo Quispe Huamán ya está matriculado en 5.° Primaria A (2026). Para moverlo usa «Cambiar sección» en "
						+ "su ficha.",
				"La sección 2.° Primaria Z no existe en 2026. Créala primero en Colegio.");
	}

	@Test
	void avisaSiElMismoArchivoYaSeImporto() {
		byte[] libro = archivo(mateo());
		confirmar(previa(libro));

		assertThat(previa(libro).importadoAntesEn()).isNotNull().isEqualTo(
				jdbc.queryForObject("SELECT creado_en FROM importacion_alumnos", java.time.LocalDateTime.class));
		assertThat(previa(archivo(valeria())).importadoAntesEn()).isNull();
	}

	@Test
	void confirmarQuedaAuditadoConConteos() {
		byte[] libro = archivo(mateo(), valeria(), sebastian());
		VistaPreviaImportacion previa = previa(libro);
		ResultadoImportacion resultado = servicio.confirmar(previa, previa.token());

		Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM importacion_alumnos WHERE id = ?",
				resultado.importacionId());
		assertThat(registro).containsEntry("archivo_nombre", "alumnos.xlsx").containsEntry("archivo_sha256", previa.sha256())
				.containsEntry("archivo_bytes", libro.length).containsEntry("filas", 3).containsEntry("alumnos_nuevos", 3)
				.containsEntry("familias_nuevas", 2).containsEntry("matriculas_nuevas", 3)
				.containsEntry("creado_por", "usuario.prueba").containsEntry("colegio_id", 1L);
		Map<String, Object> evento = ultimoEvento(jdbc, "IMPORTACION_CONFIRMADA");
		assertThat(evento).containsEntry("entidad_id", resultado.importacionId().toString())
				.containsEntry("nombre_usuario", "usuario.prueba");
		assertThat((String) evento.get("valor_nuevo")).contains("alumnos nuevos=3", "familias nuevas=2",
				"matrículas nuevas=3");
		assertThat((String) evento.get("detalle")).contains(previa.sha256(), "alumnos.xlsx", "año 2026");
		// Cada alta, con su propio evento y datos enmascarados.
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'ALUMNO_REGISTRADO'")).isEqualTo(3);
		assertThat(EscenarioEscolar.todaLaBitacora(jdbc)).doesNotContain("78451236", "987654321", "rosa.huaman@");
		assertThat(servicio.historial()).singleElement().satisfies(h -> {
			assertThat(h.anio()).isEqualTo(2026);
			assertThat(h.conteos().alumnosNuevos()).isEqualTo(3);
		});
	}

	@Test
	void siLaBaseCambioDesdeLaVistaPreviaPideRevisarDeNuevo() {
		VistaPreviaImportacion previa = previa(archivo(mateo(), sebastian()));
		// Mientras se revisaba, alguien registró a Sebastián a mano.
		alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("75330981", "Flores", "Rojas", "Sebastián",
				LocalDate.of(2013, 8, 21), "41235678", "Flores", "Díaz", "Pedro", "912345678", null, null));
		long alumnosAntes = contar(jdbc, "alumno");
		long eventosAntes = contar(jdbc, "evento_auditoria");

		assertThatThrownBy(() -> servicio.confirmar(previa, previa.token()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Los datos cambiaron desde la revisión");
		assertThat(contar(jdbc, "alumno")).isEqualTo(alumnosAntes);
		assertThat(contar(jdbc, "evento_auditoria")).isEqualTo(eventosAntes);
		assertThat(contar(jdbc, "importacion_alumnos")).isZero();
	}

	@Test
	void tokenDeOtroUsuarioOColegioEsRechazado() {
		VistaPreviaImportacion previa = previa(archivo(mateo()));

		assertThatThrownBy(() -> servicio.confirmar(previa, UUID.randomUUID()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es válida");
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(1L, 2L, "otra.persona", "Otra Persona", false,
				EnumSet.of(Rol.ADMINISTRACION)));
		assertThatThrownBy(() -> servicio.confirmar(previa, previa.token()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es válida");
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(colegioB, 1L, "usuario.prueba", "Usuario", false,
				EnumSet.of(Rol.DIRECTOR)));
		assertThatThrownBy(() -> servicio.confirmar(previa, previa.token()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es válida");
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.PROMOTOR));
		assertThatThrownBy(() -> servicio.confirmar(previa, previa.token())).isInstanceOf(AccessDeniedException.class);
		assertThat(contar(jdbc, "alumno")).isZero();
	}

	@Test
	void losLogsNoContienenDniNiNombres(CapturedOutput salida) {
		VistaPreviaImportacion previa = previa(archivo(mateo(), con(valeria(), 1, "8012774"), sebastian()));
		VistaPreviaImportacion buena = previa(archivo(mateo(), valeria(), sebastian()));
		servicio.confirmar(buena, buena.token());

		assertThat(salida.getAll()).contains(previa.sha256(), buena.sha256())
				.doesNotContain("78451236", "8012774", "45678912", "987654321", "Quispe", "Huamán", "Mateo", "Rosa",
						"rosa.huaman", "Sebastián");
	}

	@Test
	void importarEnColegioBNoTocaAlColegioAConElMismoDni() {
		confirmar(previa(archivo(mateo())));
		Long mateoA = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_documento = '78451236'", Long.class);
		Map<String, Object> antes = jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", mateoA);

		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(colegioB, 7L, "director.b", "Directora B", false,
				EnumSet.of(Rol.DIRECTOR)));
		Long anioB = estructura.crearAnio(new CrearAnioEscolarRequest(2026, LocalDate.of(2026, 3, 2),
				LocalDate.of(2026, 12, 18), true));
		estructura.crearSeccion(anioB, new CrearSeccionRequest(Grado.PRIMARIA_5, "A"));
		assertThat(servicio.historial()).isEmpty();
		VistaPreviaImportacion previa = servicio.previsualizar(anioB, "alumnos.xlsx", archivo(con(mateo(), 4, "Andrés")));

		// En B es un alumno nuevo (el del A no existe para B) y el archivo nunca se importó en B.
		assertThat(previa.resumen().alumnosNuevos()).isEqualTo(1);
		assertThat(previa.importadoAntesEn()).isNull();
		servicio.confirmar(previa, previa.token());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE numero_documento = '78451236'", Long.class))
				.isEqualTo(2);
		assertThat(jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", mateoA)).isEqualTo(antes);
		assertThat(servicio.historial()).singleElement().satisfies(h -> assertThat(h.creadoPor()).isEqualTo("director.b"));
	}

	private VistaPreviaImportacion previa(byte[] libro) {
		return servicio.previsualizar(escuela.anio2026(), "alumnos.xlsx", libro);
	}

	private void confirmar(VistaPreviaImportacion previa) {
		servicio.confirmar(previa, previa.token());
	}
}
