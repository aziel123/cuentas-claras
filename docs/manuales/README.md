# Manuales de una página por rol

Sprint 7 (entrega). Cada manual cabe en una hoja A4 y sigue la misma estructura (sección 15.1 de `docs/arquitectura/sprint-7-endurecimiento.md`):
1. **Tu día con Cuentas Claras:** de 3 a 6 pasos.
2. **Lo que el sistema no te deja hacer, y por qué:** una línea por regla, sin culpar a nadie.
3. **Si algo no cuadra:** a quién avisar y qué no tocar.
4. **Dónde ver el video:** el código QR del video del rol ([guion](../entrega/guion-videos.md)).

| Manual | Para | Videos |
|---|---|---|
| [promotoria.md](promotoria.md) | Promotora o dueña. Reemplaza la guía `docs/operacion/guia-promotora.md` | 4, 5 y 6 |
| [direccion.md](direccion.md) | Dirección | 4 |
| [administracion.md](administracion.md) | Administración y contador | 3 |
| [caja.md](caja.md) | Cajera o secretaria | 1 y 2 |
| [docente.md](docente.md) | Docentes (se completa en marzo con la fase académica) | 9 |
| [familias.md](familias.md) | Padres, madres y apoderados; también como volante | 7 y 8 |
| [operador.md](operador.md) | Responsable técnico (usa términos técnicos y puede pasar de una página) | 9 |

## Antes de imprimir
- **Códigos QR:** cuando un video esté publicado (sin listar, decisión 106), reemplaza el texto «Código QR del video N: se agrega cuando el video esté publicado» por la imagen del código QR, de unos 3 cm de lado, con el título del video debajo.
- **Plazos de la Ley 29733** (20 y 10 días hábiles, 48 horas y conservación): siguen marcados «a confirmar por el asesor legal». Cuando el asesor los confirme, quita la marca y anota la fecha de la versión.
- **Versión:** cada manual dice «Versión 1, octubre de 2026» en su encabezado. Si cambia una pantalla o una regla, sube la versión y vuelve a imprimir solo ese manual.

## Cómo imprimirlos en A4
**Opción sencilla (sin instalar nada):** abre el archivo en GitHub o en Visual Studio Code con la vista previa de Markdown, usa *Imprimir* del navegador, elige **A4**, márgenes **estrechos** (unos 15 mm) y escala **100 %**, sin encabezados ni pies de página del navegador. Si se pasa a una segunda hoja, baja la escala a 90 %.

**Opción con Pandoc** (para imprimir todos iguales):
```bash
pandoc caja.md -o caja.pdf -V geometry:a4paper,margin=15mm -V fontsize=10pt -V mainfont="Inter"
```
(Necesita Pandoc y un motor de PDF; si no tienes la fuente Inter, quita `-V mainfont=...`.)

**Volante para las familias:** imprime `familias.md` en A4 a una cara: una copia por familia, más algunas de reserva para la mesa de ayuda de la matrícula (sesión S6 de la [capacitación](../entrega/capacitacion.md)).

## Revisión del lenguaje
Español claro del Perú, en segunda persona («tu caja», «tus boletas»), sin siglas técnicas salvo en `operador.md`, con los botones y títulos tal como aparecen en pantalla. Revisado con el microcopy de la skill `evaluacion-ux`. Si una persona no entiende una línea en la capacitación, se reescribe esa línea: el manual se adapta a quien lo usa, no al revés.
