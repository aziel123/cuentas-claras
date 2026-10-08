package pe.edu.virgenmaria.cuentasclaras.comun.multicolegio;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Indica a Hibernate el colegio de la sesión que se abre. Se registra por nombre de clase en
 * {@code spring.jpa.properties.hibernate.tenant_identifier_resolver} (no como bean), por eso
 * necesita un constructor público sin argumentos.
 * <p>
 * Nunca devuelve {@code null}: sin colegio devuelve {@link ContextoColegio#NINGUNO} y no se
 * ve ni se inserta nada.
 */
public class ResolutorColegioActual implements CurrentTenantIdentifierResolver<Long> {

	@Override
	public Long resolveCurrentTenantIdentifier() {
		return ContextoColegio.actual();
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return false;
	}

	@Override
	public boolean isRoot(Long colegioId) {
		return ContextoColegio.SISTEMA.equals(colegioId);
	}
}
