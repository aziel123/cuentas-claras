# Acta del paso 0: Cloud SQL para MySQL 8.4 (región Santiago)

> Plantilla (sprint 8, tanda 3; sección 6.6 del diseño y `docs/operacion/despliegue-gcp.md`, sección 4). Se llena con los
> registros que deja `sh deploy/gcp/paso-0.sh probar` en `~/cc-paso0` (fuera del repositorio). **Si una fila dice FALLA,
> no se despliega en Google Cloud**: primero se ajusta lo que el servicio permite o se prueba AWS RDS en `sa-east-1`.

| Dato | Valor |
|---|---|
| Fecha y hora (Lima) | |
| Responsable técnico | |
| Proyecto de Google Cloud | |
| Instancia de prueba | `cc-paso0` · MySQL 8.4 · `db-g1-small` · `southamerica-west1` |
| Commit del repositorio | |
| Versión de MySQL (`SELECT VERSION()`) | |

## 1. Job `mysql` del CI completo (`job-mysql.log`)
Copia aquí el «Resumen del job mysql» (cada paso con OK o FALLÓ).

| Paso | Resultado |
|---|---|
| Usuarios `cc_migrador`, `cc_app`, `cc_sistema`, `cc_respaldo` y el rol `cc_negocio` con `01`, como root | |
| `migrar` con `cc_migrador` (V1 a V30) | |
| Fase 1: Hibernate valida el esquema | |
| `02-permisos-tablas.sql` como root (REVOKE ALL, roles, GRANT por columna, funciones `DEFINER`) | |
| `03-triggers.sql` como `cc_migrador` (98 triggers y 10 funciones) **sin** `SET PERSIST` (la marca es de la instancia) | |
| Fase 2 con los permisos mínimos y las pruebas de los sprints 6, 7 y 8 | |
| `comprobar` (1142, 1143 y 1644) | |
| Arranque de prod con su verificador (huellas, privilegios, TLS, plataforma) | |
| M2, M3, M4 y la rotación de la clave de los conectores | |

## 2. Comprobaciones a mano (`comprobaciones.log`)
| Comprobación | Esperado | Resultado |
|---|---|---|
| `SHOW GRANTS` del administrador del servicio | `ROLE_ADMIN`, `CREATE ROLE`, `DROP ROLE`, `CREATE USER`; sin `SUPER` | |
| `JSON_LENGTH(huellas_objetos())` | 91 | |
| `permisos_objetos()` | Sin error (lee `mysql.role_edges`, `mysql.user`, `mysql.global_grants`, `mysql.db`, `mysql.tables_priv`, `mysql.columns_priv` y `mysql.procs_priv`) | |
| Usuarios `cc_*` con un rol distinto de `cc_negocio` | 0 | |
| `@@GLOBAL.log_bin_trust_function_creators` | 1 | |
| `@@GLOBAL.general_log` | 0 | |
| `Ssl_cipher` con `--ssl-mode=VERIFY_CA` y la autoridad de la instancia | No vacío | |

## 3. Respaldo y restauración (`respaldo.log`, `restauracion/`)
| Paso | Resultado |
|---|---|
| `respaldar.sh` como `cc_respaldo` (cifrado con 2 claves age, manifiesto con conteos por colegio) | |
| `restaurar-y-verificar.sh` en un MySQL vacío (cadena de la bitácora, anclas, conteos y libros) | |

## 4. TLS obligatorio (`tls.log`, `prod-tls.log`)
| Paso | Resultado |
|---|---|
| `gcloud sql instances patch --ssl-mode ENCRYPTED_ONLY` | |
| Sin TLS no conecta | |
| Prod arranca con `sslMode=VERIFY_CA`: «Huellas de los 98 triggers y las 13 funciones verificadas» | |
| «Conexiones a MySQL cifradas (TLS) verificadas» y «Privilegios verificados» | |

## 5. Lo que este paso no prueba
- La conexión por **IP privada** desde la VM (la de producción): la comprueba el primer `desplegar.sh`, que lee las mismas
  líneas del verificador y detiene la aplicación si falta alguna.
- Cloud SQL Auth Proxy: no se usa (H14).

## 6. Decisión
- [ ] Todo en OK: se despliega en Google Cloud (decisión 110).
- [ ] Algo falló: qué y qué se ajusta, o se cambia de proveedor:

Instancia borrada (`paso-0.sh borrar`) el: ______  Firma del responsable técnico: ______
