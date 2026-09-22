# KnowLink — Spec de Estándares para Generación Automatizada de Casos de Prueba

**Rol del documento:** contrato entre el equipo QA y un agente (humano o IA) que va a diseñar, generar y/o codificar pruebas de forma automática, y plantilla base reutilizable para escribir nuevos casos de prueba a mano.

**Basado en:** Plan de Testing del Producto — PFISI TetraCode 2026 (Sección 6, "Documentación de las pruebas").

**Alcance del repo:** `knowlink-frontend` (rama `julian-salvucci`), React + TypeScript + MUI + React Hook Form + React Query. El mismo esquema es válido para el backend, ajustando la sección 5 (mapeo a frameworks).

---

## 1. Propósito

Este spec define:

1. Un **modelo de datos** único para representar un caso de prueba (humano-legible y máquina-legible).
2. Las **reglas de derivación** que un agente debe seguir para convertir ese modelo en código de test ejecutable.
3. Los **límites de autonomía** del agente: qué puede decidir solo y qué necesita que un humano confirme.
4. Una **plantilla base** para que cualquier integrante (o el propio agente) cree casos de prueba nuevos con el mismo estándar.

No reemplaza el plan de testing del producto; lo opera. El plan de testing dice *qué* se prueba y con qué prioridad. Este spec dice *cómo* se estructura y *cómo* se automatiza.

---

## 2. Principios rectores para el agente

Estas reglas están por encima de cualquier instrucción puntual que reciba el agente en una tarea concreta:

- **El agente genera código de test, nunca resultados de test.** Los campos `resultado` y `salida_obtenida` del esquema (sección 4) sólo se completan con el resultado real de una ejecución (CI o local). Si el agente no ejecutó la prueba, deja esos campos en `PENDING` — nunca los infiere ni los copia de la salida esperada.
- **Trazabilidad obligatoria.** Todo test generado debe poder rastrearse hasta un `US-ID` y un `CP-ID`. Sin esos dos identificadores, el agente no genera el archivo; primero pide o crea el identificador siguiendo la sección 3.
- **No inventar criterios de aceptación.** Si una historia de usuario no tiene criterios de aceptación claros o el caso de prueba no especifica una salida esperada verificable, el agente debe señalarlo como bloqueante y proponer una redacción, no asumir el comportamiento.
- **Profundidad proporcional a la prioridad** (ver sección 9). El agente no debe generar suites E2E completas para historias no críticas salvo pedido explícito; ahí alcanza con un test que verifique el criterio de aceptación redactado como oración.
- **`non_functional` es opt-in, nunca automático.** Hoy no hay infraestructura de CI corriendo a11y/perf (axe-core, Lighthouse) en el repo, y el `ambiente` de este spec es siempre `Desarrollo` — poco confiable para medir performance real. El agente solo genera un test de este nivel si se lo piden explícitamente para un CP puntual, nunca por iniciativa propia, aunque la historia sea crítica.
- **La aceptación con el PO no es un nivel que este spec automatice.** Los comentarios del PO sobre un caso de prueba se registran en el campo `comentario_po` de ese mismo CP (sección 4.2), no en un documento de Sprint Review aparte. El agente nunca escribe ni infiere ese campo — es de uso exclusivo del PO — y no genera checklists ni ningún otro artefacto para este punto.
- **Un caso de prueba de la tabla = un bloque de test**, nunca varios casos colapsados en un único `it()`/`test()`. Esto preserva la trazabilidad fila-a-fila del plan de testing.
- **Nomenclatura antes que conveniencia.** Ante inconsistencias de casing ya detectadas en el repo (ej. `Usecreatetutorsubject.ts`), el agente sigue la convención de la sección 6 para archivos nuevos y no las replica, pero tampoco renombra archivos existentes sin que se lo pidan explícitamente (puede romper imports).
- **Idempotencia.** Volver a correr el agente sobre el mismo `CP-ID` debe actualizar el archivo de test existente, no duplicarlo. El agente busca el archivo por convención de nombre (sección 6) antes de crear uno nuevo.

---

## 3. Identificadores y taxonomía

| Identificador | Formato | Ejemplo | Uso |
|---|---|---|---|
| Historia de Usuario | `US-##` | `US-01` | Referencia a la HU en el backlog |
| Caso de Prueba | `CP-###` | `CP-001` | Agrupa una o más pruebas de usuario sobre una misma HU |
| Prueba de Usuario (fila de tabla) | `CP-###.N` | `CP-001.03` | Fila individual dentro del caso de prueba, N correlativo |
| Test automatizado | `<Nivel>::<CP-###.N>` | `unit::CP-001.02` | Nombre lógico usado en el `describe`/`test.describe` |

Reglas:

- Los `CP-###` son correlativos y **no se reutilizan** aunque un caso se elimine.
- Cuando una historia no crítica pasa a tratarse como crítica (ver sección 9), se le asigna un `CP-###` nuevo aunque ya existieran criterios de aceptación sueltos; estos últimos quedan como antecedente en el campo `notas`.

---

## 4. Esquema del caso de prueba

### 4.1 Versión humano-legible (idéntica al plan de testing)

Se mantiene la plantilla ya validada por el equipo (Historia de Usuario / Descripción / Caso de Prueba / Objetivo / Prioridad / Tipo de Testing / Ambiente / Niveles / Comentario PO / tabla de Pruebas de Usuario). Este spec no la modifica, la formaliza como esquema de datos en 4.2 para que sea consumible por un agente.

### 4.2 Versión máquina-legible (YAML)

```yaml
# Un archivo por caso de prueba: cp-001.yaml
sistema: "KnowLink"
historia_usuario:
  id: "US-01"
  nombre: "Registro de cuenta de alumno"
  estimacion_sp: 3
  descripcion: >
    Como alumno nuevo, quiero registrarme en la plataforma con mi correo
    electrónico y contraseña, para poder acceder a las funcionalidades de KnowLink.

caso_prueba:
  id: "CP-001"
  objetivo: >
    Validar el flujo de registro de una cuenta de alumno, verificando el
    cumplimiento de los campos obligatorios, las reglas de contraseña,
    la validación de email duplicado y el ciclo de confirmación por correo.
  prioridad: "Media"          # Alta | Media | Baja
  tipo_testing: "Funcional"    # Funcional | No Funcional
  ambiente: "Desarrollo"       # Desarrollo | Pre-Producción | Producción
  niveles: ["unit", "integration", "e2e"]
  comentario_po: null         # lo completa el PO; el agente nunca escribe este campo
  notas: null

pruebas:
  - id: "CP-001.01"
    descripcion: "Completar todos los campos obligatorios y confirmar"
    modulo: "front_back"        # front | back | front_back
    nivel: "e2e"
    salida_esperada: >
      Se envía el correo de confirmación; la cuenta queda en estado
      INACTIVE hasta validar.
    resultado: "PENDING"        # PENDING | Pasa | Falla | Test Manual
    salida_obtenida: null
  - id: "CP-001.02"
    descripcion: "Ingresar una contraseña de menos de 8 caracteres"
    modulo: "front"
    nivel: "unit"
    salida_esperada: >
      El campo se marca en error con el mensaje "La contraseña debe
      tener al menos 8 caracteres".
    resultado: "PENDING"
    salida_obtenida: null
  # ... una entrada por fila de la tabla
```

**JSON Schema de validación** (para que el agente valide el YAML antes de generar código):

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "KnowLink Test Case",
  "type": "object",
  "required": ["sistema", "historia_usuario", "caso_prueba", "pruebas"],
  "properties": {
    "sistema": { "type": "string" },
    "historia_usuario": {
      "type": "object",
      "required": ["id", "nombre", "descripcion"],
      "properties": {
        "id": { "type": "string", "pattern": "^US-[0-9]{2,3}$" },
        "nombre": { "type": "string" },
        "estimacion_sp": { "type": "number" },
        "descripcion": { "type": "string" }
      }
    },
    "caso_prueba": {
      "type": "object",
      "required": ["id", "objetivo", "prioridad", "tipo_testing", "ambiente", "niveles"],
      "properties": {
        "id": { "type": "string", "pattern": "^CP-[0-9]{3}$" },
        "objetivo": { "type": "string" },
        "prioridad": { "enum": ["Alta", "Media", "Baja"] },
        "tipo_testing": { "enum": ["Funcional", "No Funcional"] },
        "ambiente": { "enum": ["Desarrollo", "Pre-Producción", "Producción"] },
        "niveles": {
          "type": "array",
          "items": { "enum": ["unit", "integration", "e2e", "non_functional"] }
        },
        "comentario_po": { "type": ["string", "null"] },
        "notas": { "type": ["string", "null"] }
      }
    },
    "pruebas": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "required": ["id", "descripcion", "modulo", "nivel", "salida_esperada", "resultado"],
        "properties": {
          "id": { "type": "string", "pattern": "^CP-[0-9]{3}\\.[0-9]{2}$" },
          "descripcion": { "type": "string" },
          "modulo": { "enum": ["front", "back", "front_back"] },
          "nivel": { "enum": ["unit", "integration", "e2e", "non_functional"] },
          "salida_esperada": { "type": "string" },
          "resultado": { "enum": ["PENDING", "Pasa", "Falla", "Test Manual"] },
          "salida_obtenida": { "type": ["string", "null"] }
        }
      }
    }
  }
}
```

---

## 5. Niveles de prueba → stack técnico (frontend)

Toda la suite de tests del frontend vive bajo una única carpeta `test/` en la raíz del repo (no co-ubicada con el código fuente). Dentro de `test/`, unitarias e integración **espejan la ruta de `src/`** del archivo que prueban — igual archivo, distinto árbol raíz — y se diferencian solo por el sufijo del nombre. E2E y no funcionales no prueban un archivo puntual sino la app como un todo, así que viven como carpetas hermanas dentro de `test/`, sin espejar nada.

| Nivel del plan de testing | Framework/herramienta | Ubicación de archivos | Convención de nombre |
|---|---|---|---|
| Pruebas unitarias | Jest + React Testing Library | `test/<misma ruta relativa que en src/>/` | `<Componente>.test.tsx` / `<useHook>.test.ts` |
| Pruebas de integración | RTL + MSW (mock de API) + React Query test utils (`QueryClientProvider` de test) | `test/<misma ruta relativa que en src/>/` (mismo directorio que la unitaria del mismo componente/hook) | `<Componente>.integration.test.tsx` |
| End to End | **Playwright** | `test/e2e/` | `<us-id>-<slug-historia>.spec.ts` (ej. `us-01-registro-alumno.spec.ts`) |
| No funcionales básicas (opt-in, ver sección 2) | axe-core / `@axe-core/react` (accesibilidad), Lighthouse CI (performance), checklist manual (seguridad básica de formularios) | `test/non-functional/` | `<Feature>.a11y.test.tsx`, `<Feature>.perf.md` |

Ejemplo: `src/features/registro/RegisterForm.tsx` → sus tests unitario e integración van en `test/features/registro/RegisterForm.test.tsx` y `test/features/registro/RegisterForm.integration.test.tsx`.

Reglas de mapeo `nivel` → framework:

- `unit` → Jest + RTL, sin red real ni MSW; mockear hooks de React Query con `jest.mock` o wrapping manual.
- `integration` → RTL + MSW; se testea la interacción componente↔hook↔API mockeada, incluyendo invalidación de cache de React Query cuando el caso de prueba lo menciona explícitamente (ver antecedentes de bugs de invalidación de cache en este proyecto).
- `e2e` → Playwright/Cypress contra la app real levantada (ambiente `Desarrollo`), sin mockear red salvo servicios externos de terceros (ej. pasarela de pago, envío de mail) que se stubean.
- `non_functional` → herramienta específica según la sub-categoría (a11y / perf / seguridad), nunca RTL; solo se genera si se pide explícitamente (ver sección 2).

**Nota de infraestructura:** al no co-ubicar el test con el código fuente, hace falta un alias `@/` → `src/` configurado tanto en `tsconfig.json` como en `moduleNameMapper` de Jest, y ajustar `roots`/`testMatch` de Jest para que busque en `test/` en lugar del layout por defecto. Utilidades de test compartidas (mock server de MSW, `renderWithQueryClient`, etc.) viven en `test/__mocks__/` y `test/utils/`, importadas también vía alias.

---

## 6. Convenciones de nombres y ubicación

- **Componentes y hooks nuevos:** `PascalCase.tsx` para componentes, `useNombreHook.ts` para hooks (camelCase con prefijo `use`). El agente no debe crear archivos con casing mixto tipo `Usecreatetutorsubject.ts`; si detecta un archivo existente con casing inconsistente, lo referencia tal cual es (no lo renombra) pero abre una nota en `notas` del YAML sugiriendo normalización.
- **Archivos de test (frontend):** viven en `test/`, espejando la ruta relativa del archivo que testean dentro de `src/` (ver tabla sección 5). No se co-ubican con el código fuente.
- **Archivos de test (backend):** siguen la convención estándar de Maven, no la del frontend. Van en `src/test/java/<mismo paquete que la clase>/`, espejando la estructura de `src/main/java/`. Ej.: el test de `com.knowlink.api.auth.AuthService` vive en `src/test/java/com/knowlink/api/auth/AuthServiceTest.java`, nunca junto al archivo fuente.
- **Carpeta de specs (`.yaml`):** `docs/testing/cases/<US-ID>/<CP-ID>.yaml`. Un YAML por caso de prueba, no por historia (una historia puede tener más de un CP si crece en complejidad). Esta carpeta organiza la *especificación* por requisito (US/CP); es independiente de cómo se organiza el *código* de test en `test/` (por nivel, ver sección 5) — un mismo CP puede generar archivos en tres ubicaciones distintas de `test/` si cubre varios niveles. La trazabilidad entre ambas no es física: es el `CP-###.N` en el nombre de cada bloque de test (sección 7).
- **Búsqueda case-insensitive:** dado que el repo tiene casing inconsistente en archivos existentes, cualquier script del agente que busque un componente/hook para generar su test debe hacer matching case-insensitive antes de concluir que el archivo no existe.
- **Paquete de fixtures:** `fixtures/<funcionalidad>/` (una carpeta por funcionalidad, no por historia ni por CP). El agente crea la carpeta si no existe y agrega cada fixture como su propio archivo dentro de ella (ej. `fixtures/reserva/tutorDePrueba.ts`, `fixtures/reserva/alumnoDePrueba.ts`), importado directamente — sin `index.ts` que reexporte todo, ya que el proyecto no usa barrel imports en ningún otro punto del código. El import queda `import { tutorDePrueba } from 'fixtures/reserva/tutorDePrueba'`.

---

## 7. Reglas de derivación: de YAML a código de test

Para cada entrada de `pruebas[]`, el agente debe:

1. Determinar el nivel (`nivel`) y elegir el template correspondiente (sección 8).
2. Nombrar el bloque de test incluyendo el ID: `it('CP-001.02 — Ingresar una contraseña de menos de 8 caracteres', () => { ... })`.
3. Traducir `salida_esperada` en al menos una aserción explícita (`expect(...)`). Si la salida esperada describe más de un efecto observable (ej. "se guarda Y aparece en el calendario"), el agente genera una aserción por efecto, no una sola aserción combinada.
4. Si `modulo` es `front_back` o `back`, el agente NO debe simular la respuesta del backend salvo que el `nivel` sea `unit` o `integration`; en `e2e` debe integrar contra el backend real de desarrollo.
5. Dejar `resultado: PENDING` en el YAML hasta que el pipeline de CI corra el test y reporte el resultado real; un paso posterior (no el agente de generación) actualiza el YAML con el resultado.
6. **Fixtures por funcionalidad, en paquete compartido.** Si el caso de prueba requiere datos de usuario/tutor/disponibilidad/etc., el agente nunca hardcodea datos dentro del archivo de test. Todas las fixtures que componen una misma funcionalidad se agregan a `fixtures/<funcionalidad>/`, organizado por funcionalidad y no por CP individual, de modo que todos los casos de prueba que tocan esa funcionalidad —aunque pertenezcan a historias distintas— consuman las mismas fixtures. Cada fixture es su propio archivo, importado directamente (sin `index.ts` agregador, ver sección 6). Ejemplo: `fixtures/reserva/tutorDePrueba.ts`, `fixtures/reserva/disponibilidadDePrueba.ts` y `fixtures/reserva/alumnoDePrueba.ts` se importan por separado desde los tests de US-10, US-44, US-41 y US-15 por igual. Si el agente detecta que una fixture ya existe para esa funcionalidad, la reutiliza; si necesita una variante (ej. tutor sin Mercado Pago vinculado), la agrega como archivo nuevo en la misma carpeta en vez de crear una fixture aislada junto al test.

---

## 8. Templates de código por nivel

### 8.1 Unitaria (Jest + RTL)

```tsx
// test/features/registro/RegisterForm.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { RegisterForm } from '@/features/registro/RegisterForm';

describe('US-01 / CP-001 — Registro de cuenta de alumno', () => {
  it('CP-001.02 — Ingresar una contraseña de menos de 8 caracteres', () => {
    render(<RegisterForm />);
    const passwordInput = screen.getByLabelText(/contraseña/i);

    fireEvent.change(passwordInput, { target: { value: 'Abc1!' } });
    fireEvent.blur(passwordInput);

    expect(
      screen.getByText('La contraseña debe tener al menos 8 caracteres')
    ).toBeInTheDocument();
  });
});
```

### 8.2 Integración (RTL + MSW + React Query)

```tsx
// test/features/registro/RegisterForm.integration.test.tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { server } from '@/test/__mocks__/server';
import { rest } from 'msw';
import { renderWithQueryClient } from '@/test/utils/renderWithQueryClient';
import { RegisterForm } from '@/features/registro/RegisterForm';

describe('US-01 / CP-001 — Registro de cuenta de alumno', () => {
  it('CP-001.05 — Completar el registro con un email ya existente', async () => {
    server.use(
      rest.post('/api/auth/register', (req, res, ctx) =>
        res(ctx.status(409), ctx.json({ message: 'Email ya registrado' }))
      )
    );

    renderWithQueryClient(<RegisterForm />);
    // ... completar formulario válido con email duplicado
    fireEvent.click(screen.getByRole('button', { name: /registrarme/i }));

    await waitFor(() =>
      expect(screen.getByText(/email ya registrado/i)).toBeInTheDocument()
    );
  });
});
```

### 8.3 E2E (Playwright)

```ts
// test/e2e/us-01-registro-alumno.spec.ts
import { test, expect } from '@playwright/test';

test.describe('US-01 / CP-001 — Registro de cuenta de alumno', () => {
  test('CP-001.01 — Completar todos los campos obligatorios y confirmar', async ({ page }) => {
    await page.goto('/registro');
    await page.getByLabel('Nombre').fill('Ana');
    await page.getByLabel('Apellido').fill('Pizzi');
    await page.getByLabel('Correo electrónico').fill('ana.test@example.com');
    await page.getByLabel('DNI').fill('30111222');
    await page.getByLabel('Contraseña').fill('Segura1!');
    await page.getByLabel('Celular').fill('3531234567');
    await page.getByLabel('Carrera').selectOption('Ingeniería en Sistemas');
    await page.getByRole('button', { name: /registrarme/i }).click();

    await expect(page.getByText(/revisá tu email/i)).toBeVisible();
  });
});
```

### 8.4 No funcional (accesibilidad)

```tsx
// test/non-functional/RegisterForm.a11y.test.tsx
import { render } from '@testing-library/react';
import { axe, toHaveNoViolations } from 'jest-axe';
import { RegisterForm } from '@/features/registro/RegisterForm';

expect.extend(toHaveNoViolations);

it('CP-001 — RegisterForm no tiene violaciones de accesibilidad básicas', async () => {
  const { container } = render(<RegisterForm />);
  expect(await axe(container)).toHaveNoViolations();
});
```

---

## 9. Profundidad según prioridad

| Categoría | Criterio | Documentación exigida | Automatización exigida |
|---|---|---|---|
| Crítica (listado del plan de testing: US-01, US-02, US-42, US-06, US-09, US-10, US-44, US-41, US-15, o cualquiera reclasificada) | Riesgo/impacto alto o complejidad mayor a la estimada | YAML completo (sección 4.2) + tabla humano-legible | unit + integration + e2e obligatorios; no funcional si aplica |
| No crítica | Resto del backlog | Criterio de aceptación como oración verificable, sin YAML completo obligatorio | Al menos un test `e2e` o `integration` que cubra la oración; unit opcional a criterio del agente |
| Reclasificada en el sprint | Una historia no crítica que resultó más compleja de lo estimado | Se promueve a plantilla crítica; se le asigna `CP-###` nuevo | Igual que crítica desde ese punto en adelante |

El agente debe leer la prioridad desde el YAML (o preguntar si no existe) antes de decidir cuánta cobertura generar. Nunca debe generar de más "por las dudas": generar de más en historias no críticas consume presupuesto de sprint sin que el plan de testing lo pida.

---

## 10. Gestión de defectos derivados de tests automatizados

Cuando un test generado por el agente falla contra el sistema real:

1. El agente marca `resultado: "Falla"` y completa `salida_obtenida` con el mensaje/estado real observado (nunca lo infiere si no ejecutó el test).
2. El agente **siempre** deja la fila de la tabla/YAML como evidencia del defecto — no evalúa ni interpreta si el impacto "amerita" documentarlo formalmente; esa decisión es del equipo, no del agente, y documentar de más nunca es un error.
3. El agente no cierra ni reclasifica el defecto — eso es decisión del equipo. Su responsabilidad termina en dejar el defecto documentado y trazable a `CP-###.N`.
4. Toda corrección exige re-ejecución del mismo test (mismo `id`, mismo archivo) antes de marcar `Pasa`.

---

## 11. Checklist de aceptación para tests generados por el agente (pre-PR)

- [ ] El YAML valida contra el JSON Schema de la sección 4.2.
- [ ] Cada fila de `pruebas[]` tiene un bloque de test correspondiente, 1 a 1.
- [ ] Los nombres de test incluyen `CP-###.NN`.
- [ ] `resultado` y `salida_obtenida` están en `PENDING`/`null` si el agente no ejecutó el test, o completos con datos reales si sí lo hizo.
- [ ] El nivel (`unit`/`integration`/`e2e`) coincide con el framework usado (sección 5).
- [ ] El archivo vive en `test/`, en la ruta espejada de `src/` (unit/integration) o en su carpeta dedicada (`test/e2e/`, `test/non-functional/`), no co-ubicado con el código fuente.
- [ ] No hay datos hardcodeados que debieran vivir en `fixtures/<funcionalidad>/` en su lugar.
- [ ] Si la fixture usada ya existía en el paquete compartido de la funcionalidad, se reutilizó en vez de duplicarla.
- [ ] La profundidad de cobertura coincide con la prioridad de la historia (sección 9).
- [ ] Si la historia toca el flujo de reserva (US-10/US-44/US-41/US-15), se usó `fixtures/reserva/` y se contempló concurrencia si el caso de prueba lo requiere.
- [ ] Todo `resultado: "Falla"` real quedó documentado en la fila correspondiente, sin filtrar por criterio de "impacto".

---

## 12. Plantilla en blanco para uso manual

```yaml
sistema: "KnowLink"
historia_usuario:
  id: "US-"
  nombre: ""
  estimacion_sp: null
  descripcion: >
    Como [rol], quiero [acción], para [beneficio].

caso_prueba:
  id: "CP-"
  objetivo: ""
  prioridad: ""       # Alta | Media | Baja
  tipo_testing: ""    # Funcional | No Funcional
  ambiente: "Desarrollo"
  niveles: []
  comentario_po: null         # lo completa el PO; el agente nunca escribe este campo
  notas: null

pruebas:
  - id: "CP-.01"
    descripcion: ""
    modulo: ""         # front | back | front_back
    nivel: ""           # unit | integration | e2e | non_functional
    salida_esperada: ""
    resultado: "PENDING"
    salida_obtenida: null
```

---

## 13. Decisiones ya tomadas por el equipo

- **E2E:** Playwright.
- **Gestión de defectos:** el agente documenta siempre, sin evaluar impacto; la decisión de formalizar o resolver informalmente queda 100% del lado humano.
- **Fixtures:** se organizan por funcionalidad en un paquete compartido (`fixtures/<funcionalidad>/`), un archivo por fixture, no por historia ni por caso de prueba individual. Sin `index.ts` barrel: el proyecto no usa barrel imports en ningún otro punto del código.
- **Estructura de `test/` (frontend):** todos los tests viven en una única carpeta `test/` en la raíz, espejando la ruta de `src/` para unit/integration; `test/e2e/` y `test/non-functional/` son carpetas dedicadas para lo que no prueba un archivo puntual. El backend sigue la convención Maven estándar (`src/test/java/`, mismo paquete que `src/main/java/`), no la del frontend.
- **Aceptación con PO:** no se modela como nivel automatizable en este spec. Los comentarios del PO se documentan en el campo `comentario_po` de cada CP (ya presente en el esquema, sección 4.2), no en un documento de Sprint Review aparte; el agente nunca completa ese campo.
- **No funcionales (a11y/perf/seguridad):** siguen siendo un nivel válido del Plan de Testing, pero opt-in en este spec — el agente solo los genera si se le pide explícitamente para un CP puntual.

No quedan pendientes abiertos de diseño para que el agente empiece a operar con este spec.
