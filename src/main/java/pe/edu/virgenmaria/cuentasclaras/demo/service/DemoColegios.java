package pe.edu.virgenmaria.cuentasclaras.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.inicial.AccesoApoderadoDemo;
import pe.edu.virgenmaria.cuentasclaras.alumnos.inicial.FamiliasDemo;
import pe.edu.virgenmaria.cuentasclaras.alumnos.inicial.FamiliasDemo.FamiliaDemo;
import pe.edu.virgenmaria.cuentasclaras.alumnos.inicial.FamiliasDemo.FamiliasCreadas;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.inicial.CajaDemo;
import pe.edu.virgenmaria.cuentasclaras.caja.inicial.CajaDemo.AlumnoCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.inicial.PensionesDemo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.inicial.PensionesDemo.Deudor;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.LogosColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.demo.dto.DemoCreada;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.CuentasDemo;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Sprint 8, tanda 3 (sección 8.2, decisiones 126 y 127): «Crear mi demo». Cada visitante recibe SU colegio ficticio,
 * ya en producción, con datos de ejemplo y una cuenta por rol (prefijo y clave aleatorios; la clave la ve una sola vez).
 * El aislamiento entre colegios del sprint 1 separa a los visitantes y la sesión única no los choca: las cuentas son
 * distintas.
 * <p>
 * Lo crea el actor {@code sistema.demo}, con los mismos servicios y reglas del colegio: las cuentas por la ruta de
 * identidad, las familias con el registro de alumnos, los planes los propone Administración y los aprueba Dirección
 * (con su firma), y los cobros los hace la cajera (Lucía Ramos) con su sesión. Solo existe en el perfil {@code demo}
 * (H2 en memoria, sin salida a internet, se borra cada noche).
 */
@Service
@Profile("demo")
public class DemoColegios {

	private static final Logger LOG = LoggerFactory.getLogger(DemoColegios.class);

	/** Letras del prefijo y de la clave sin las que se confunden (0, o, 1, l, i). */
	static final String ALFABETO = "abcdefghjkmnpqrstuvwxyz23456789";

	private final ColegioRepository colegios;

	private final LogosColegio logos;

	private final AuditoriaService auditoria;

	private final CuentasDemo cuentas;

	private final FamiliasDemo familias;

	private final AccesoApoderadoDemo apoderado;

	private final PensionesDemo pensiones;

	private final CajaDemo caja;

	private final LimiteDemos limite;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	private final SecureRandom azar = new SecureRandom();

	public DemoColegios(ColegioRepository colegios, LogosColegio logos, AuditoriaService auditoria, CuentasDemo cuentas,
			FamiliasDemo familias, AccesoApoderadoDemo apoderado, PensionesDemo pensiones, CajaDemo caja, LimiteDemos limite,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.colegios = colegios;
		this.logos = logos;
		this.auditoria = auditoria;
		this.cuentas = cuentas;
		this.familias = familias;
		this.apoderado = apoderado;
		this.pensiones = pensiones;
		this.caja = caja;
		this.limite = limite;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	/** Crea el colegio ficticio de un visitante, con sus cuentas y sus datos. */
	public DemoCreada crear(String ip) {
		limite.reservar(ip);
		try {
			return crearColegio();
		}
		catch (RuntimeException e) {
			limite.devolver(ip);
			LOG.warn("No se pudo crear una demo: {}", e.getClass().getSimpleName(), e);
			if (e instanceof ReglaNegocioException) {
				throw e;
			}
			throw new ReglaNegocioException("No se pudo preparar tu demo. Inténtalo de nuevo en un momento.");
		}
	}

	private DemoCreada crearColegio() {
		String prefijo = aleatorio(1, "abcdefghjkmnpqrstuvwxyz") + aleatorio(3, ALFABETO);
		String clave = aleatorio(4, ALFABETO) + "-" + aleatorio(4, ALFABETO) + "-" + aleatorio(4, ALFABETO);
		LocalDate hoy = LocalDate.now(reloj);
		LocalDateTime ahora = LocalDateTime.now(reloj);

		// 1. El colegio (ficticio), en producción, con su logo genérico.
		Colegio colegio = transaccion.execute(t -> {
			int numero = colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO).size() + 1;
			Colegio nuevo = Colegio.enPreparacion("Colegio Demostración N.° " + numero,
					"Colegio Demostración N.° " + numero + " S.A.C. (ficticio)", rucFicticio(numero),
					"Av. Ejemplo 123, Lima (dirección ficticia)", "contacto@colegio-demo.example", EnumSet.allOf(Nivel.class),
					"Titular de la demostración", "00000000", "DEMO-" + numero);
			nuevo.pasarAProduccion(ActorSistema.DEMO.usuario(), ahora);
			return colegios.saveAndFlush(nuevo);
		});
		long colegioId = colegio.getId();
		ContextoColegio.en(colegioId, () -> transaccion.executeWithoutResult(t -> {
			logos.guardar(logoGenerico(), "logo-demo.png");
			auditoria.registrar(Actor.sistema(colegioId), AccionAuditoria.COLEGIO_CREADO, "colegio",
					Long.toString(colegioId), null, colegio.getNombre(), "Colegio de la demo creado por un visitante "
							+ "(sistema.demo). Datos ficticios: se borra esta noche.");
		}));

		// 2. Las cuentas del personal, las familias, la cuenta del apoderado, las pensiones y la caja de hoy.
		List<String> personal = cuentas.crear(colegioId, prefijo, clave);
		FamiliasCreadas creadas = familias.cargar(colegioId, hoy);
		List<FamiliaDemo> lista = creadas.familias();
		String delPortal = apoderado.crear(colegioId, prefijo + ".apoderado", clave, lista.getFirst().dniApoderado());
		List<Deudor> deudores = new ArrayList<>();
		for (FamiliaDemo f : lista.subList(6, 18)) {
			deudores.add(new Deudor(f.alumnos().getFirst().dni(), f.alumnos().getFirst().grado().nivel()));
		}
		pensiones.cargar(colegioId, prefijo + ".administracion", prefijo + ".direccion", creadas.anio(), hoy, deudores);
		caja.cargar(colegioId, prefijo, List.of(cobro(lista.get(1)), cobro(lista.get(2)), cobro(lista.get(3))),
				cobro(lista.get(4)), new AlumnoCobro(lista.getFirst().alumnos().get(1).dni(),
						lista.getFirst().alumnos().get(1).nombres()));

		List<DemoCreada.Cuenta> vistas = new ArrayList<>();
		for (int i = 0; i < CuentasDemo.PERSONAL.size(); i++) {
			CuentasDemo.CuentaDemo c = CuentasDemo.PERSONAL.get(i);
			vistas.add(new DemoCreada.Cuenta(c.rol().etiqueta(), personal.get(i), c.nombreCompleto()));
		}
		vistas.add(new DemoCreada.Cuenta("Apoderado (portal de las familias)", delPortal, lista.getFirst().nombreApoderado()));
		LOG.info("Demo creada: colegio {} con {} familias ficticias (prefijo {}).", colegioId, lista.size(), prefijo);
		return new DemoCreada(colegio.getNombre(), clave, List.copyOf(vistas));
	}

	private static AlumnoCobro cobro(FamiliaDemo familia) {
		return new AlumnoCobro(familia.alumnos().getFirst().dni(), familia.alumnos().getFirst().nombres());
	}

	private String aleatorio(int largo, String alfabeto) {
		StringBuilder texto = new StringBuilder(largo);
		for (int i = 0; i < largo; i++) {
			texto.append(alfabeto.charAt(azar.nextInt(alfabeto.length())));
		}
		return texto.toString();
	}

	/** Un RUC ficticio de 11 dígitos (20000000 + número, con su dígito verificador módulo 11). */
	static String rucFicticio(int numero) {
		String base = "2000000" + String.format("%03d", Math.floorMod(numero, 1000));
		int[] pesos = { 5, 4, 3, 2, 7, 6, 5, 4, 3, 2 };
		int suma = 0;
		for (int i = 0; i < 10; i++) {
			suma += (base.charAt(i) - '0') * pesos[i];
		}
		int digito = 11 - suma % 11;
		return base + (digito == 10 ? 0 : digito == 11 ? 1 : digito);
	}

	/** El logo genérico de la demo: un círculo azul de la marca con un libro abierto blanco (sin texto ni fuentes). */
	static byte[] logoGenerico() {
		int lado = 256;
		BufferedImage imagen = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = imagen.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new Color(0x1b, 0x4f, 0x9c));
			g.fill(new Ellipse2D.Double(8, 8, lado - 16, lado - 16));
			g.setColor(new Color(0x38, 0xae, 0xe6));
			g.setStroke(new BasicStroke(10f));
			g.draw(new Ellipse2D.Double(13, 13, lado - 26, lado - 26));
			g.setColor(Color.WHITE);
			Path2D izquierda = new Path2D.Double();
			izquierda.moveTo(64, 92);
			izquierda.lineTo(124, 104);
			izquierda.lineTo(124, 180);
			izquierda.lineTo(64, 168);
			izquierda.closePath();
			Path2D derecha = new Path2D.Double();
			derecha.moveTo(192, 92);
			derecha.lineTo(132, 104);
			derecha.lineTo(132, 180);
			derecha.lineTo(192, 168);
			derecha.closePath();
			g.fill(izquierda);
			g.fill(derecha);
		}
		finally {
			g.dispose();
		}
		try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			ImageIO.write(imagen, "png", salida);
			return salida.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
