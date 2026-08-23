# US-16 — Cargar material académico (Backend)

**Repo:** knowlink-backend
**Sprint:** 3
**Story Points:** 8
**Prioridad:** MEDIA
**Épica:** —
**Relacionadas:** US-43 (catálogo de materias), US-26 (acceso alumnos), US-27 (denuncia de material), US-17 (eliminar material)
**Tags:** #RN-21 #RN-22
**Módulo:** recursos

---

## Historia

Como tutor, quiero subir archivos de estudio vinculados a una materia específica, para que otros alumnos puedan beneficiarse de mi material.

---

## Criterios de aceptación (Gherkin)

### AC1 — Carga con nombre y materia asociada
```gherkin
Feature: Carga de material académico

  Scenario: Tutor carga un material indicando nombre y materia
    Given el tutor está autenticado
    And existe una materia "Análisis Matemático II" en el catálogo
    When el tutor envía un archivo válido con nombre "Resumen Unidad 3 - Derivadas" y materiaId asociado
    Then el sistema almacena el archivo en Supabase Storage
    And persiste el registro en material_academico con el nombre indicado por el tutor y el materiaId correspondiente
    And responde 201 con el DTO del material creado

  Scenario: Tutor intenta guardar sin indicar nombre
    Given el tutor está autenticado
    When el tutor envía un archivo válido con materiaId pero sin nombre
    Then el sistema responde 400
    And el mensaje de error es "Debés indicar un nombre para el material"
    And no se sube ningún archivo a Supabase Storage
```

> **Nota:** el "nombre" del AC1 es un campo de texto que el tutor completa explícitamente (ej. "Resumen Unidad 3 - Derivadas"), **distinto** del nombre físico del archivo que sube (ej. `apunte_v2_final_copia.pdf`). No confundir ni derivar uno del otro automáticamente. El mensaje de error de nombre vacío no está en el enunciado original de US-16, pero se agrega por consistencia con AC3/AC4 (mismo patrón de validación server-side) — confirmar redacción exacta con el equipo antes de mergear si se quiere un texto distinto.

### AC2 — Visibilidad para alumnos con reserva (por tutor + materia)
```gherkin
  Scenario: Alumno con reserva activa con ese tutor en esa materia visualiza el material
    Given un alumno tiene una reserva de tutoría en la materia "Análisis Matemático II" con el Tutor A
    And el Tutor A cargó un material para esa materia
    When el alumno solicita el listado de materiales de esa materia
    Then el sistema devuelve el material del Tutor A con su URL de descarga

  Scenario: Alumno sin reserva intenta acceder
    Given un alumno NO tiene reserva activa en la materia del material
    When el alumno solicita descargar el material
    Then el sistema responde 403 Forbidden

  Scenario: Alumno NO accede a material de un tutor distinto al que le dictó la tutoría
    Given un alumno tiene una reserva de tutoría en "Análisis Matemático II" con el Tutor A
    And el Tutor B (sin relación de reserva con ese alumno) cargó un material para "Análisis Matemático II"
    When el alumno solicita el listado de materiales de esa materia
    Then el sistema NO devuelve el material cargado por el Tutor B
    And solo devuelve materiales de tutores con los que el alumno tiene reserva en esa materia

  Scenario: Alumno sin reserva con ningún tutor de la materia solicita el listado
    Given un alumno NO tiene reserva activa con ningún tutor de la materia "Análisis Matemático II"
    When el alumno solicita el listado de materiales de esa materia
    Then el sistema responde 403 Forbidden
    And el mensaje de error es "El alumno no tiene reserva activa con este tutor"
```

> **Decisión de diseño confirmada**: la visibilidad es **por tutor + materia**, no solo por materia. Un alumno solo ve material de los tutores con los que efectivamente tiene (o tuvo) una reserva de tutoría en esa materia puntual — no ve material de otros tutores que dicten la misma materia sin relación de reserva con ese alumno. Esto reemplaza la interpretación literal más amplia del AC original ("visible para cualquier usuario que haya reservado tutorías de esa materia"), acotándola explícitamente al tutor con el que reservó.
>
> **Caso límite confirmado**: si un alumno consulta `GET /api/materiales?materiaId={id}` y no tiene reserva activa con **ningún** tutor de esa materia, el sistema responde `403 Forbidden` con el mensaje `"El alumno no tiene reserva activa con este tutor"`. Mismo comportamiento aplica al endpoint de descarga puntual cuando el alumno no tiene reserva con el tutor específico dueño del material solicitado.

### AC3 — Materia obligatoria (RN-21)
```gherkin
  Scenario: Tutor intenta subir sin seleccionar materia
    Given el tutor está autenticado
    When el tutor envía un archivo sin materiaId
    Then el sistema responde 400
    And el mensaje de error es "Debés asociar el material a una materia"
    And no se sube ningún archivo a Supabase Storage
```

### AC4 — Formatos permitidos (RN-22)
```gherkin
  Scenario Outline: Formato de archivo no permitido
    Given el tutor está autenticado
    When el tutor envía un archivo de extensión "<extension>"
    Then el sistema responde 400
    And el mensaje de error es "El formato del archivo no está permitido"
    And no se sube ningún archivo a Supabase Storage

    Examples:
      | extension |
      | docx      |
      | jpg       |
      | zip       |
      | mp4       |

  Scenario Outline: Formato de archivo permitido
    Given el tutor está autenticado
    When el tutor envía un archivo de extensión "<extension>"
    Then el sistema sube el archivo correctamente

    Examples:
      | extension |
      | png       |
      | pdf       |
      | xlsx      |
```

---

## Stack confirmado

- **Java 21**, Spring Boot
- **MySQL** (metadata: tabla `material_academico`)
- **Supabase Storage** (contenido binario de los archivos) vía API REST — bucket `materiales`
- Cliente HTTP: `RestClient` (Spring 6+) o `WebClient` — **no** usar SDKs de terceros no auditados; consumir la REST API de Supabase Storage directamente con `SUPABASE_SERVICE_ROLE_KEY` desde variables de entorno.

### ⚠️ Advertencia de dependencia
Este endpoint depende de que **US-43 (catálogo de materias)** ya exponga `MateriaRepository`/`materia.id` como entidad persistida y consultable. No mockear la materia — si US-43 no está mergeada en la rama de integración, este spec queda bloqueado y debe reportarse como impedimento, no resolverse con datos hardcodeados.

También depende de **US-26 (acceso alumnos)** para el chequeo de reserva activa **por tutor + materia** en los endpoints de listado y descarga (AC2). Se necesitan dos métodos en `ReservaService`:
- `tieneAlgunaReservaEnMateria(usuarioId, materiaId)` → boolean, usado en el gate inicial del listado
- `tieneReservaConTutorEnMateria(usuarioId, tutorId, materiaId)` → boolean, usado para filtrar cada material y para el endpoint de descarga

Si ninguno de los dos existe aún, implementarlos como stubs que retornan `true` únicamente en perfil de test, dejando un `// TODO(US-26)` explícito — nunca deshabilitar el chequeo en producción.

---

## Alcance funcional

### Ubicación exacta de paquetes

```
src/main/java/com/knowlink/
├── recursos/
│   ├── controller/
│   │   └── MaterialAcademicoController.java
│   ├── service/
│   │   ├── MaterialAcademicoService.java
│   │   └── SupabaseStorageService.java
│   ├── repository/
│   │   └── MaterialAcademicoRepository.java
│   ├── model/
│   │   └── MaterialAcademico.java
│   ├── dto/
│   │   ├── MaterialAcademicoRequestDTO.java
│   │   ├── MaterialAcademicoResponseDTO.java
│   │   └── ErrorResponseDTO.java
│   └── exception/
│       ├── MateriaNoAsociadaException.java
│       └── FormatoNoPermitidoException.java
```

### Entidad `MaterialAcademico`

| Campo | Tipo | Notas |
|---|---|---|
| id | Long | PK autoincremental |
| nombre | String | NOT NULL — nombre indicado por el tutor (AC1), es el que se muestra en la UI |
| nombreArchivoOriginal | String | nombre físico del archivo tal como lo subió el tutor (para trazabilidad interna, no se muestra como principal) |
| materiaId | Long | FK a `materia`, NOT NULL |
| tutorId | Long | FK a `usuario`, NOT NULL |
| formato | Enum(PNG, PDF, XLSX) | NOT NULL |
| rutaStorage | String | path dentro del bucket de Supabase, ej. `{materiaId}/{uuid}-{nombre}` |
| fechaCarga | LocalDateTime | default now |
| tamanioBytes | Long | opcional, para validación de tamaño |
| activo | boolean | soft delete, default true (relación con US-17) |

### Endpoints

**`POST /api/materiales`** (multipart/form-data)
- Params: `archivo` (MultipartFile), `nombre` (String, requerido), `materiaId` (Long)
- Auth: rol TUTOR requerido
- Validaciones en orden: (1) materiaId no nulo → AC3, (2) nombre no vacío/blank → error "Debés indicar un nombre para el material", (3) extensión permitida → AC4, (4) materia existe en catálogo (404 si no)
- Sube el binario a Supabase Storage bucket `materiales`, path `{materiaId}/{uuid}-{nombreOriginal}`
- Persiste metadata en `material_academico`
- Response: 201 + `MaterialAcademicoResponseDTO`

**`GET /api/materiales?materiaId={id}`**
- Auth: rol ALUMNO o TUTOR
- Si ALUMNO: primero verifica si tiene reserva activa con **al menos un** tutor de esa materia (`ReservaService.tieneAlgunaReservaEnMateria(usuarioId, materiaId)`). Si no tiene ninguna, responde `403` con mensaje `"El alumno no tiene reserva activa con este tutor"`. Si tiene al menos una, continúa y filtra el listado material por material usando `tieneReservaConTutorEnMateria(usuarioId, tutorId, materiaId)`, dejando solo los materiales de tutores con los que sí tiene reserva
- Devuelve lista de materiales activos (`activo = true`) de la materia, **filtrados por la relación tutor-alumno-materia**, cada uno con URL firmada de Supabase (expiración sugerida: 1 hora)

**`GET /api/materiales/{id}/descargar`**
- Auth: valida `ReservaService.tieneReservaConTutorEnMateria(usuarioId, material.getTutorId(), material.getMateriaId())` — si es `false`, responde `403` con mensaje `"El alumno no tiene reserva activa con este tutor"`
- Devuelve 302 redirect a la URL firmada de Supabase, o el DTO con la URL si el frontend maneja la descarga

### Integración Supabase Storage — detalle técnico

```java
@Service
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    private static final String BUCKET = "materiales";

    public String subir(MultipartFile archivo, Long materiaId) {
        String path = materiaId + "/" + UUID.randomUUID() + "-" + archivo.getOriginalFilename();
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + BUCKET + "/" + path;

        restClient.post()
            .uri(uploadUrl)
            .header("Authorization", "Bearer " + serviceRoleKey)
            .header("Content-Type", archivo.getContentType())
            .body(archivo.getBytes())
            .retrieve()
            .toBodilessEntity();

        return path;
    }

    public String generarUrlFirmada(String path, int expiracionSegundos) {
        String signUrl = supabaseUrl + "/storage/v1/object/sign/" + BUCKET + "/" + path;
        // POST con { "expiresIn": expiracionSegundos } → devuelve signedURL
        SignedUrlResponse response = restClient.post()
            .uri(signUrl)
            .header("Authorization", "Bearer " + serviceRoleKey)
            .body(Map.of("expiresIn", expiracionSegundos))
            .retrieve()
            .body(SignedUrlResponse.class);
        return supabaseUrl + response.signedURL();
    }

    public void eliminar(String path) {
        // DELETE al mismo endpoint, para US-17
    }
}
```

**Variables de entorno requeridas** (agregar a `application.yml` / secrets del entorno, nunca hardcodear):
```yaml
supabase:
  url: ${SUPABASE_URL}
  service-role-key: ${SUPABASE_SERVICE_ROLE_KEY}
```

### Validación de formato (RN-22)

Validar **tanto extensión como content-type real** del multipart, no confiar solo en el nombre de archivo:

```java
private static final Map<String, Set<String>> FORMATOS_PERMITIDOS = Map.of(
    "PNG", Set.of("image/png"),
    "PDF", Set.of("application/pdf"),
    "XLSX", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
);
```

Si `content-type` no coincide con la extensión declarada, tratar como formato no permitido (mismo mensaje de error).

### Límite de tamaño

Configurar en `application.yml`:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB
```
No está en los criterios de aceptación explícitos, pero es obligatorio dado el límite de 1GB del free tier de Supabase — sin este control, un solo archivo grande puede agotar la cuota.

---

## Expectativas de testing por capa

| Capa | Framework | Qué cubrir |
|---|---|---|
| Unit — Service | JUnit 5 + Mockito | AC3 (materiaId null → excepción con mensaje exacto), AC4 (extensión/content-type inválido → excepción), mapeo correcto de DTO ↔ entidad |
| Unit — SupabaseStorageService | JUnit 5 + Mockito (mock del RestClient) | Construcción correcta del path, manejo de error si Supabase responde 4xx/5xx (no debe persistir metadata si falla la subida) |
| Integration — Controller | `@SpringBootTest` + `MockMvc` + Testcontainers (MySQL) | Los 4 AC completos end-to-end contra MySQL real, con Supabase mockeado vía WireMock |
| Integration — Seguridad | `@SpringBootTest` + `MockMvc` | AC2: alumno sin reserva con ningún tutor de la materia recibe `403` con mensaje exacto `"El alumno no tiene reserva activa con este tutor"`; tutor sin rol adecuado recibe 403 en el POST; **alumno con reserva con Tutor A NO recibe/no puede descargar materiales cargados por Tutor B de la misma materia** — confirma el filtro por tutor+materia; **alumno con reserva con ambos Tutor A y Tutor B en la misma materia recibe materiales de ambos** |

**Cobertura mínima esperada:** 80% en `service/` y `controller/` del módulo `recursos`.

---

## No hacer

- ❌ No subir el archivo a Supabase antes de validar materiaId y formato — las validaciones van primero, siempre.
- ❌ No usar SDK oficial de Supabase para Java si no está evaluado por el equipo — usar REST API directa según este spec.
- ❌ No hardcodear `SUPABASE_SERVICE_ROLE_KEY` en código ni en `application.yml` versionado — solo en variables de entorno/secrets.
- ❌ No hacer hard delete de `material_academico` en este spec (eso es US-17) — este spec solo crea/lista/descarga.
- ❌ No omitir el chequeo de content-type real confiando solo en la extensión del nombre de archivo.
- ❌ No generar URLs públicas permanentes del bucket — siempre URLs firmadas con expiración.

---

## Definition of Done

- [ ] Migración SQL de `material_academico` aplicada (con campo `nombre` NOT NULL, distinto de `nombreArchivoOriginal`)
- [ ] `POST /api/materiales` implementado con las 4 validaciones en orden correcto (materia, nombre, formato, existencia de materia)
- [ ] `GET /api/materiales?materiaId=` implementado con chequeo de reserva (AC2)
- [ ] `GET /api/materiales/{id}/descargar` implementado con URL firmada
- [ ] `SupabaseStorageService` implementado y configurado con variables de entorno
- [ ] Bucket `materiales` creado en el proyecto Supabase con políticas RLS documentadas (ver spec frontend / infra)
- [ ] Tests unitarios de service (AC3, AC4) en verde
- [ ] Tests de integración de controller (AC1–AC4) en verde con Testcontainers
- [ ] Límite de tamaño de archivo configurado (20MB)
- [ ] Sin credenciales hardcodeadas — verificado en PR review
- [ ] PR con referencia a US-16 y a las dependencias US-43/US-26
