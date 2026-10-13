package pe.edu.virgenmaria.cuentasclaras.conectores.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.CifradoAesGcm.SecretoIlegibleException;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 8, tanda 2 (sección 3.6, decisión 118): en prod y en el piloto, al arrancar, comprueba que la clave maestra de
 * los conectores descifra TODOS los secretos guardados (de todos los colegios). Si falta la clave, se desplegó otra o un
 * secreto quedó en una versión que esta instalación no tiene (rotación a medias), no arranca: nadie emitiría boletas ni
 * cobraría en línea con un conector ilegible, y el responsable técnico lo ve en el despliegue, no un colegio en caja.
 * Nunca registra un secreto: solo conteos y versiones.
 */
@Component
@Profile({ "prod", "piloto" })
public class VerificadorClaveConectores implements InitializingBean {

	private static final Logger LOG = LoggerFactory.getLogger(VerificadorClaveConectores.class);

	private final ConectorColegioRepository conectores;

	private final SecretosConectores secretos;

	private final RecorridoColegios colegios;

	public VerificadorClaveConectores(ConectorColegioRepository conectores, SecretosConectores secretos,
			RecorridoColegios colegios) {
		this.conectores = conectores;
		this.secretos = secretos;
		this.colegios = colegios;
	}

	@Override
	public void afterPropertiesSet() {
		verificar();
	}

	public void verificar() {
		int total = 0;
		List<String> ilegibles = new ArrayList<>();
		for (Long colegio : colegios.activos()) {
			List<ConectorColegio> conSecreto = ContextoColegio.en(colegio,
					() -> conectores.findBySecretoPistaIsNotNullOrClaveVersionIsNotNull());
			for (ConectorColegio conector : conSecreto) {
				if (!conector.conSecreto()) {
					continue;
				}
				total++;
				try {
					if (!secretos.descifraVersion(conector.getClaveVersion())) {
						throw new SecretoIlegibleException("versión " + conector.getClaveVersion());
					}
					secretos.descifrar(conector);
				}
				catch (SecretoIlegibleException e) {
					ilegibles.add("conector " + conector.getId() + " del colegio " + colegio + " (versión "
							+ conector.getClaveVersion() + ")");
				}
			}
		}
		if (!ilegibles.isEmpty()) {
			throw new IllegalStateException("La clave de los conectores (CC_CLAVE_CONECTORES, versión " + secretos.version()
					+ ") no descifra " + ilegibles.size() + " secreto(s): " + String.join(", ", ilegibles) + ". Despliega la "
					+ "clave correcta o completa la rotación (CC_CLAVE_CONECTORES_ANTERIOR y recifrar-conectores).");
		}
		LOG.info("Secretos de los conectores verificados: {} con la clave versión {}.", total, secretos.version());
	}
}
