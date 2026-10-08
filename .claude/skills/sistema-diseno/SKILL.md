---
name: sistema-diseno
description: Sistema de diseño UI del proyecto (tokens de color, tipografía, espaciado, componentes, patrones de pantalla y reglas de accesibilidad) para el panel administrativo y el portal de padres. Úsala al crear o revisar cualquier pantalla, plantilla Thymeleaf o CSS.
---

# Sistema de diseño

## Personalidad
**Confiable, claro y cercano.** Es un sistema que maneja el dinero de las familias y la información de sus hijos, así que debe transmitir orden y transparencia. Nada recargado.

## Marca: Colegio Virgen María
Paleta institucional **blanco, azul y celeste, con el blanco predominante** (pedido del colegio). Fondo y tarjetas son blancos; el azul y el celeste solo marcan acciones y acentos. No hay modo oscuro: la plataforma se ve igual aunque el equipo esté en modo oscuro. Contraste validado con WCAG 2.1 AA.

| Rol | Color | Uso | Contraste |
|---|---|---|---|
| Blanco (predominante) | `#ffffff` | Fondo de la página, tarjetas, barra superior | — |
| Azul (primario) | `#1b4f9c` | Botón principal, enlaces, pestaña activa, barras de gráficos | Blanco sobre azul: 7.9:1 |
| Azul hover | `#153f7e` | Estado hover del primario | 10.3:1 |
| Azul profundo | `#0f2c55` | Zonas de marca puntuales | Blanco: más de 12:1 |
| Celeste (acento) | `#38aee6` | **Solo decorativo**: franjas, bordes superiores, monograma, ilustraciones | 2.5:1 sobre blanco, **nunca para texto** |
| Celeste texto | `#0a6ea8` | Texto o íconos en celeste | 5.5:1 sobre blanco |
| Celeste suave | `#eaf6fd` | Fondos de selección y filas activas | Texto celeste encima: más de 4.5:1 |

Reglas de marca:
- El **azul** lleva la acción. El **celeste** acompaña y nunca compite con el botón principal.
- Sobre fondo celeste vivo, el texto va en azul profundo (5.5:1). Nunca blanco sobre celeste.
- El monograma **VM** en blanco sobre azul, con franja celeste inferior, reemplaza al logo hasta tener el oficial del colegio.

## Tokens (CSS custom properties)
Defínelos una sola vez en `static/css/tokens.css` y úsalos siempre.
```css
:root {
  /* Marca Colegio Virgen María */
  --color-primario: #1b4f9c;
  --color-primario-hover: #153f7e;
  --color-primario-texto: #1b4f9c;
  --color-azul-profundo: #0f2c55;
  --color-celeste: #38aee6;        /* solo decorativo */
  --color-celeste-texto: #0a6ea8;
  --color-celeste-suave: #eaf6fd;

  /* Blanco predominante (pedido del colegio): azul y celeste solo como acentos */
  --color-fondo: #ffffff;
  --color-superficie: #ffffff;
  --color-superficie-2: #f3f8fd;
  --color-borde: #dce7f2;
  --color-texto: #0f172a;
  --color-texto-secundario: #475569;

  /* Estados (siempre con texto o ícono) */
  --color-exito: #15803d;   --color-exito-suave: #dcfce7;    /* Pagado */
  --color-alerta: #a14a06;  --color-alerta-suave: #fef3c7;   /* Por vencer */
  --color-peligro: #b91c1c; --color-peligro-suave: #fee2e2;  /* Vencido o anulado */
  --color-info: #0369a1;    --color-info-suave: #e0f2fe;

  /* Tipografía */
  --fuente: "Inter", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  --texto-xs: 0.75rem; --texto-sm: 0.875rem; --texto-base: 1rem;
  --texto-lg: 1.125rem; --texto-xl: 1.5rem; --texto-2xl: 2rem;

  /* Espaciado (escala de 4 px) */
  --esp-1: 4px; --esp-2: 8px; --esp-3: 12px; --esp-4: 16px;
  --esp-6: 24px; --esp-8: 32px; --esp-12: 48px;

  --radio: 8px;
  --sombra: 0 1px 3px rgb(27 79 156 / 0.08);
  color-scheme: light; /* sin modo oscuro: la plataforma siempre se ve blanca */
}

```
Los textos de estado sobre fondos suaves cumplen el contraste AA. Valida cualquier color nuevo con una herramienta de contraste.

Referencia visual navegable: `docs/prototipo/cuentas-claras.html`.

## Componentes base (fragmentos Thymeleaf en `templates/fragments/`)
| Componente | Uso | Reglas |
|---|---|---|
| `layout` | Estructura de página | Menú lateral en escritorio, barra inferior en celular. Muestra colegio, usuario y rol. |
| `boton` | Acciones | Variantes: primario (1 por pantalla), secundario, peligro y texto. Altura mínima de 44 px. |
| `campo` | Inputs | `<label>` siempre visible (el placeholder no la reemplaza). Ayuda y error debajo del campo. |
| `tabla` | Listados | Encabezado fijo, montos alineados a la derecha, en celular se convierte en tarjetas. |
| `badge-estado` | Estado de cuota o pago | Color más texto: **Pagado**, **Pendiente**, **Vencido**, **Anulado**. |
| `monto` | Mostrar dinero | `S/ 1,250.00`, números tabulares (`font-variant-numeric: tabular-nums`). |
| `alerta` | Mensajes | Éxito, info, advertencia y error, con ícono y texto. `role="alert"` para los errores. |
| `modal-confirmacion` | Acciones sensibles | Título claro, consecuencia explicada, **motivo obligatorio** para anular o descontar y botón de peligro. |
| `tarjeta-kpi` | Panel del dueño | Cifra grande, etiqueta y comparación ("vs. ayer"). |
| `estado-vacio` | Listas vacías | Explicación y acción siguiente. |

## Patrones de pantalla clave
- **Caja (cajero)**: buscar alumno (por DNI, nombre o código) → ver cuotas pendientes → seleccionar → elegir medio de pago → confirmar → comprobante emitido y padre notificado (mostrar ambos como confirmación).
- **Estado de cuenta (padre, celular)**: total pendiente arriba y destacado, botón "Pagar ahora", lista de cuotas con badge y historial de comprobantes descargables.
- **Panel del dueño (celular)**: recaudado hoy, del mes, morosidad en % y en S/, alertas de caja con diferencia y pendientes de aprobación.
- **Aprobaciones (director)**: bandeja con quién solicita, qué, monto y motivo, y los botones Aprobar o Rechazar con comentario.

## Accesibilidad (WCAG 2.1 AA, obligatorio)
- Contraste de 4.5:1 en texto normal y 3:1 en texto grande y elementos de interfaz.
- Foco visible en todos los elementos interactivos (`:focus-visible`).
- Navegable por teclado y en orden lógico.
- Nunca transmitir información solo con color.
- `lang="es"` en el `<html>` e imágenes con `alt`.
- Objetivos táctiles de al menos 44×44 px.

## Lenguaje
Español claro, en segunda persona ("tu pago", "tus cuotas"). Sin jerga técnica. Botones con verbo de acción: "Registrar pago", no "Aceptar".
