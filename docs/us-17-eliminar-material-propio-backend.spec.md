# US-17 — Eliminar material propio (Backend)

> Spec generado a partir de la exploración real de `knowlink-backend`, rama `feature/delete-subject` (commit `17b829c`). Los nombres de paquetes, clases y rutas de este documento son los que existen hoy en el código; no los cambies por los del spec viejo de US-16.

## Metadata

| Campo | Valor |
|---|---|
| ID Historia | US-17 |
| Story Points | 1 |
| Prioridad | Baja |
| Módulo | `com.knowlink.api.materials` |
| Repo / rama | `knowlink-backend`, rama `feature/delete-subject` (base `dev`) |
| Relacionadas | US-16 (carga/listado), US-26 (acceso del alumno), US-27 (denuncia), US-23 (baja por admin), US-46 (panel del tutor) |
| Spec hermano | `us-17-eliminar-material-propio-frontend.spec.md` (repo `knowlink-frontend`) |

## Antes de empezar (obligatorio)

1. Leer `AGENTS.md` y `docs/base-standards.md`. Este spec los complementa; ante conflicto manda `base-standards.md`.
2. Confirmar la rama de trabajo: `git checkout dev && git pull && git checkout -b feat/delete-own-material` (o usar la rama de la historia si ya existe).
3. Correr la línea base antes de tocar nada: `./mvnw test -q`. Si ya hay tests rojos, anotarlo y no arreglarlos dentro de esta historia.
4. Implementar en este orden: **repository → service → storage → controller → tests**.
5. No implementar código de frontend ni cambiar contratos existentes más allá de lo que dice este documento.

## Historia

Como tutor, quiero poder eliminar materiales que yo mismo subí, para mantener mi repositorio actualizado y retirar contenido desactualizado o incorrecto.

## Alcance y decisiones de diseño

### Qué hace el backend

- Expone `DELETE /api/v1/materials/{id}` para el rol `TUTOR`.
- Solo el tutor dueño del material puede borrarlo. Un tutor no puede borrar material ajeno; un alumno o un admin tampoco.
- La eliminación es **física**: se borra la fila de `academic_material` y el objeto de Supabase Storage. No es un soft delete.
- La confirmación (`¿Estás seguro de que querés eliminar permanentemente [nombre]?`) y la cancelación son **responsabilidad exclusiva del frontend**. El backend no guarda estado de confirmación pendiente: si recibe el `DELETE`, ejecuta.
- Un material con denuncias asociadas **no se puede eliminar** por el tutor: responde `409 MATERIAL_HAS_REPORTS`. Se explica abajo.
- Un segundo `DELETE` sobre el mismo ID devuelve `404`, porque la fila ya no existe.

### Por qué no se usa `active` ni soft delete

El enunciado pide eliminar “permanentemente” y no conservar copia. Además, `active` ya tiene otro significado en el sistema: US-23 lo usa para retirar material del catálogo cuando el admin resuelve una denuncia, y US-27 lo consulta para decidir si un material sigue existiendo. Reutilizar `active = false` para esta historia dejaría el archivo en Storage y una URL firmada seguiría funcionando, incumpliendo AC2.

**No tocar `active` ni `available` en esta historia.** El borrado es `materialRepository.delete(...)`.

### Por qué un material denunciado no se elimina

A partir de US-27/US-23, `academic_material_report` y `material_report_case` tienen FK a `academic_material`; `academic_material_defense` también. Un hard delete de un material con denuncias:

1. Fallaría con `DataIntegrityViolationException` (500/409 genérico) por las FK.
2. Si se resolviera con borrado en cascada, destruiría evidencia de moderación y distorsionaría las métricas del panel admin (US-33).

Por eso, si existe **al menos una denuncia** (abierta o resuelta) para el material, el tutor recibe `409 MATERIAL_HAS_REPORTS` y el material queda intacto. El admin sigue siendo el único que puede retirarlo del catálogo con el flujo de US-23 (soft delete). Si producto quiere permitir al tutor borrar materiales ya denunciados, hace falta una historia aparte que defina qué pasa con las denuncias, los descargos y las métricas; **no resolverlo acá con cascadas**.

## Criterios de aceptación (Gherkin)

```gherkin
Feature: Eliminar material propio

  Background:
    Given un tutor autenticado con rol TUTOR

  Scenario: El tutor elimina un material propio sin denuncias
    Given el tutor subió el material "M1" y no tiene denuncias
    When envía DELETE /api/v1/materials/{M1.id}
    Then responde 204 No Content sin cuerpo
    And la fila de "M1" ya no existe en academic_material
    And el objeto de "M1" ya no existe en el bucket de Supabase Storage

  Scenario: El tutor elimina un material propio que estaba fuera de catálogo
    Given el material "M1" pertenece al tutor y no tiene denuncias
    And "M1" tiene active=false o available=false por una operación administrativa
    When el tutor envía DELETE /api/v1/materials/{M1.id}
    Then responde 204
    And la fila y el archivo se eliminan igual

  Scenario: Un tutor intenta eliminar material de otro tutor
    Given un tutor autenticado "A"
    And un material "M2" subido por el tutor "B"
    When "A" envía DELETE /api/v1/materials/{M2.id}
    Then responde 403 con detail "ACCESS_DENIED"
    And el mensaje es "No tenés permiso para eliminar este material"
    And "M2" y su archivo siguen intactos

  Scenario: Un alumno intenta eliminar un material
    Given un usuario con rol STUDENT
    When envía DELETE /api/v1/materials/{id}
    Then responde 403
    And no se modifica nada

  Scenario: Material inexistente
    When un tutor envía DELETE /api/v1/materials/{id-que-no-existe}
    Then responde 404 con detail "MATERIAL_NOT_FOUND"

  Scenario: El material tiene denuncias
    Given el material "M1" pertenece al tutor
    And "M1" tiene al menos una denuncia registrada
    When el tutor envía DELETE /api/v1/materials/{M1.id}
    Then responde 409 con detail "MATERIAL_HAS_REPORTS"
    And la fila de "M1" sigue existiendo
    And el archivo de "M1" sigue existiendo en Supabase Storage
    And las denuncias y el caso administrativo no se modifican

  Scenario: El objeto de Storage ya no existe
    Given el material "M1" pertenece al tutor y no tiene denuncias
    And el objeto de "M1" fue borrado manualmente de Supabase Storage
    When el tutor envía DELETE /api/v1/materials/{M1.id}
    Then Supabase responde 404 (o equivalente "not found")
    And el backend trata la respuesta como borrado idempotente
    And responde 204
    And la fila de "M1" se elimina

  Scenario: Falla Supabase Storage al eliminar
    Given el material "M1" pertenece al tutor y no tiene denuncias
    And Supabase Storage responde 500 o hay un timeout
    When el tutor envía DELETE /api/v1/materials/{M1.id}
    Then responde 502 con detail "STORAGE_ERROR"
    And el mensaje es un texto seguro en español
    And la transacción hace rollback: la fila de "M1" sigue existiendo
    And el tutor puede reintentar más tarde

  Scenario: Segundo DELETE sobre el mismo material
    Given el tutor eliminó "M1" correctamente
    When vuelve a enviar DELETE /api/v1/materials/{M1.id}
    Then responde 404 con detail "MATERIAL_NOT_FOUND"

  Scenario: El material eliminado ya no se puede descargar ni listar
    Given un alumno con acceso habilitado al material "M1" (US-26)
    And el tutor elimina "M1"
    When el alumno envía GET /api/v1/materials/{M1.id}/download
    Then responde 404 con detail "MATERIAL_NOT_FOUND"
    And GET /api/v1/materials?subjectId={id} ya no incluye "M1"
    And el listado de materiales accesibles del tutor ya no incluye "M1"
    And el perfil público del tutor ya no lista "M1"

  Scenario: Una URL firmada emitida antes de la eliminación deja de funcionar
    Given un alumno obtuvo una URL firmada de "M1" (vigencia 1 h)
    And el tutor elimina "M1"
    When el alumno abre esa URL firmada
    Then Supabase responde error porque el objeto ya no existe
```

## Hechos verificados en el código (no re-investigar)

| Elemento | Estado real en `main` / `feature/delete-subject` |
|---|---|
| Paquete del módulo | `com.knowlink.api.materials` (no `com.knowlink.api.resources`) |
| `IMaterialController` | `com.knowlink.api.materials.controller.interfaces.IMaterialController`, `@RequestMapping("/api/v1/materials")`. Ya tiene `upload`, `listBySubject`, `download`, `report`, `checkAccess`, `listAccessibleMaterials`. **No tiene `delete`.** |
| `MaterialControllerImpl` | Implementa el controller. `upload` ya muestra el patrón correcto: `@PreAuthorize("hasRole('TUTOR')")`, `UserPrincipal principal = (UserPrincipal) userDetails;`, `principal.getUser().getUserId()`. **No tiene `delete`.** |
| `IMaterialService` | `upload`, `listBySubject`, `getDownloadUrl`. **No tiene `delete`.** |
| `MaterialServiceImpl` | `@Service`, `@RequiredArgsConstructor`, `@Slf4j`. Constructor con **7 dependencias**: `materialRepository`, `reportRepository`, `subjectRepository`, `tutorProfileRepository`, `tutorSubjectRepository`, `supabaseStorageService`, `materialAccessService`. **No agregar dependencias**: los tests existentes lo instancian con 7 argumentos. |
| `IAcademicMaterialRepository` | Extiende `JpaRepository<AcademicMaterial, UUID>`. Ya tiene `findByIdForUpdate(UUID)` con `@Lock(PESSIMISTIC_WRITE)`, `findByAcademicMaterialIdAndActiveTrue`, `removeFromCatalog` (soft delete de admin) y `delete`/`flush` heredados. **No hace falta agregar `delete`.** |
| `AcademicMaterial` | `@Table(name = "academic_material")`, PK `UUID academicMaterialId`, dueño = `tutorSubject.tutorProfile.user.userId`, campos `name`, `originalFileName`, `storagePath`, `available`, `active`, `reportsCount`, `materialType`. No tiene relaciones hijas propias. |
| `TutorSubject.academicMaterials` | `@OneToMany(mappedBy = "tutorSubject", cascade = CascadeType.ALL, orphanRemoval = true)`, `List<AcademicMaterial>`. Es LAZY y **no debe inicializarse** en el flujo de borrado. |
| `ISupabaseStorageService` | Ya expone `void delete(String path)` y `void delete(String bucket, String path)`. `delete(path)` delega en el bucket configurado. |
| `SupabaseStorageServiceImpl` | Ya implementa el `DELETE` contra `/storage/v1/object/{bucket}/{path}`. Hoy **cualquier** `RestClientResponseException`, incluido 404, se convierte en `StorageServiceException` (502). Hay que hacerlo idempotente para 404 / “not found”. El bucket sale de `supabase.storage.materials-bucket` (default `materiales`); **no hardcodear**. |
| `StorageServiceException` | Ya existe en `com.knowlink.api.materials.exception`, extiende `RuntimeException` y provoca rollback. |
| `GlobalExceptionHandler` | Ya tiene `@ExceptionHandler(StorageServiceException.class)` → `502`. Hoy arma `ApiError(502, "Storage service error", ex.getMessage())`; el formato no coincide con el resto (`message` debería ser el texto de usuario y `detail` el código). Se ajusta en esta historia. |
| Denuncias | `AcademicMaterialReport` (`academic_material_report`) tiene FK `academic_material_id`; `MaterialReportCase` (`material_report_case`) también; `AcademicMaterialDefense` (`academic_material_defense`) también. Por eso el bloqueo del punto anterior. |
| `IAcademicMaterialReportRepository` | Ya tiene `existsByMaterialAcademicMaterialIdAndReporterUserId(...)` (lo usa US-27). Se agrega `existsByMaterialAcademicMaterialId(UUID)` para el chequeo de borrado. |
| `MaterialReportServiceImpl` | Al registrar la primera denuncia hace `incrementReportsCount` y `markUnavailable`; no borra ni oculta la fila. El material sigue existiendo para el panel admin. |
| `SecurityConfig` | `anyRequest().authenticated()` ya cubre `/api/v1/materials/**`. La autorización por rol se hace con `@PreAuthorize`. **No tocar `SecurityConfig`.** |
| Tests existentes | `src/test/java/com/knowlink/api/materials/services/MaterialServiceImplTest.java` y `.../service/implementations/MaterialServiceImplTest.java` (ambos con 7 mocks) y `.../controllers/MaterialControllerImplTest.java` (`@SpringBootTest` + `MockMvc` + perfil `test`/H2 + `@MockitoBean ISupabaseStorageService`). |

## Diseño del endpoint

| Método | Path | Rol | Éxito | Errores |
|---|---|---|---|---|
| `DELETE` | `/api/v1/materials/{id}` | `TUTOR` dueño | `204 No Content` sin cuerpo | `401` sin JWT, `403` rol distinto o material ajeno, `404` inexistente, `409` con denuncias, `502` fallo de Storage |

- Path variable: `UUID id` (PK `academicMaterialId`).
- Sin body de request ni de response.
- Formato de error existente: `{ "status": 403, "message": "...", "detail": "..." }` (`ApiError`).
- El `404` usa el errorCode existente `MATERIAL_NOT_FOUND`, el mismo que ya usa `getDownloadUrl`.
- El `409` usa `BusinessConflictException("MATERIAL_HAS_REPORTS", "No podés eliminar un material con denuncias asociadas.", "...")`, ya mapeada a 409 por `GlobalExceptionHandler`.
- El `403` de material ajeno usa `ResourceAccessDeniedException("No tenés permiso para eliminar este material")`, ya mapeada a 403 con `detail = "ACCESS_DENIED"`.
- El `502` usa `StorageServiceException` y el handler ajustado.
- El `403` por rol (`STUDENT`/`ADMIN`) lo produce Spring Security antes de entrar al método; no hace falta lógica adicional.

### Ejemplos de respuesta de error

```json
{ "status": 404, "message": "El material no existe.", "detail": "MATERIAL_NOT_FOUND" }
```

```json
{ "status": 403, "message": "No tenés permiso para eliminar este material", "detail": "ACCESS_DENIED" }
```

```json
{ "status": 409, "message": "No podés eliminar un material con denuncias asociadas.", "detail": "MATERIAL_HAS_REPORTS" }
```

```json
{ "status": 502, "message": "No se pudo comunicar con el almacenamiento de archivos. Intentá nuevamente más tarde.", "detail": "STORAGE_ERROR" }
```

## Archivos a crear / modificar

Todos los paths de producción son relativos a `src/main/java/com/knowlink/api/materials/` salvo los indicados.

| # | Acción | Archivo |
|---|---|---|
| 1 | MODIFICAR | `controller/interfaces/IMaterialController.java` |
| 2 | MODIFICAR | `controller/implementations/MaterialControllerImpl.java` |
| 3 | MODIFICAR | `service/interfaces/IMaterialService.java` |
| 4 | MODIFICAR | `service/implementations/MaterialServiceImpl.java` |
| 5 | MODIFICAR | `repositories/IAcademicMaterialReportRepository.java` |
| 6 | MODIFICAR | `service/implementations/SupabaseStorageServiceImpl.java` |
| 7 | MODIFICAR | `src/main/java/com/knowlink/api/exceptions/GlobalExceptionHandler.java` |
| 8 | MODIFICAR | `src/test/java/com/knowlink/api/materials/service/implementations/MaterialServiceImplTest.java` |
| 9 | MODIFICAR | `src/test/java/com/knowlink/api/materials/controllers/MaterialControllerImplTest.java` |
| 10 | OPCIONAL | `src/test/java/com/knowlink/api/materials/service/implementations/SupabaseStorageServiceImplTest.java` |

## Implementación

### 1. `IMaterialController` — agregar el contrato

Agregar el método con Swagger en español (el módulo ya mezcla inglés y español; para endpoints nuevos usar español, como en `report`):

```java
@Operation(summary = "Eliminar material propio",
           description = "Elimina de forma permanente un material subido por el tutor autenticado")
@ApiResponse(responseCode = "204", description = "Material eliminado")
@ApiResponse(responseCode = "403", description = "El material pertenece a otro tutor o el rol no es TUTOR")
@ApiResponse(responseCode = "404", description = "Material no encontrado")
@ApiResponse(responseCode = "409", description = "El material tiene denuncias asociadas")
@ApiResponse(responseCode = "502", description = "No se pudo eliminar el archivo del almacenamiento")
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
ResponseEntity<Void> delete(
        @Parameter(description = "ID del material") @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails);
```

`@DeleteMapping` ya está cubierto por el import wildcard `org.springframework.web.bind.annotation.*`. `HttpStatus` ya está importado en la interfaz.

### 2. `MaterialControllerImpl` — implementar sin lógica de negocio

```java
@Override
@PreAuthorize("hasRole('TUTOR')")
public ResponseEntity<Void> delete(
        UUID id,
        UserDetails userDetails) {

    UserPrincipal principal = (UserPrincipal) userDetails;
    materialService.delete(id, principal.getUser().getUserId());
    return ResponseEntity.noContent().build();
}
```

Mismo patrón que `upload`. No acceder al repositorio desde el controller.

### 3. `IMaterialService` — agregar el método

```java
void delete(UUID materialId, UUID tutorUserId);
```

### 4. `MaterialServiceImpl.delete` — lógica y orden obligatorio

Agregar el import de `BusinessConflictException`:

```java
import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
```

Implementación:

```java
@Override
@Transactional
public void delete(UUID materialId, UUID tutorUserId) {
    AcademicMaterial material = materialRepository.findByIdForUpdate(materialId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "MATERIAL_NOT_FOUND",
                    "El material no existe.",
                    "Material not found with id: " + materialId));

    UUID ownerId = material.getTutorSubject()
            .getTutorProfile()
            .getUser()
            .getUserId();

    if (!ownerId.equals(tutorUserId)) {
        log.warn("Material delete denied: material {} belongs to tutor {}, requested by {}",
                materialId, ownerId, tutorUserId);
        throw new ResourceAccessDeniedException("No tenés permiso para eliminar este material");
    }

    if (reportRepository.existsByMaterialAcademicMaterialId(materialId)) {
        log.warn("Material delete blocked: material {} has reports", materialId);
        throw new BusinessConflictException(
                "MATERIAL_HAS_REPORTS",
                "No podés eliminar un material con denuncias asociadas.",
                "Material " + materialId + " has associated reports");
    }

    String storagePath = material.getStoragePath();

    materialRepository.delete(material);
    materialRepository.flush();

    supabaseStorageService.delete(storagePath);

    log.info("Material deleted: {} by tutor: {}", materialId, tutorUserId);
}
```

Puntos no negociables:

1. **`findByIdForUpdate`**, no `findById` ni `findByAcademicMaterialIdAndActiveTrue`. El lock pesimista serializa el borrado con el ingreso de denuncias (`MaterialReportIntakeServiceImpl` usa el mismo método) y evita que dos `DELETE` concurrentes terminen en un 500. Además permite borrar material con `active = false` si no tiene denuncias.
2. **La verificación de propiedad va antes de tocar base o Storage.** Nunca borrar primero y preguntar después.
3. **El chequeo de denuncias va antes de borrar.** Si hay una denuncia, no se toca ni la fila ni el archivo.
4. **Se guarda `storagePath` antes del `delete`**, porque después de `delete` la entidad queda removida.
5. **Primero la fila, después Storage**, dentro de la misma transacción. Si Storage falla, la excepción runtime hace rollback y la fila vuelve a existir; el archivo puede o no haberse borrado (ver “Consistencia”).
6. **No inicializar `TutorSubject.academicMaterials`.** No llamar `getAcademicMaterials()` en este flujo. La colección es LAZY y no hace falta para borrar el hijo; el `delete` del repositorio es suficiente. Si en el test de integración aparece un error de Hibernate por `cascade`/`orphanRemoval`, la alternativa es `material.getTutorSubject().getAcademicMaterials().remove(material);` antes de `delete`, pero **no debería ser necesario** y no debe agregarse “por las dudas”.
7. **No llamar `removeFromCatalog`**: ese método es el soft delete de US-23 y no borra el archivo.

### 5. `IAcademicMaterialReportRepository` — nuevo exists

```java
boolean existsByMaterialAcademicMaterialId(UUID materialId);
```

El nombre sigue el patrón del método ya existente `existsByMaterialAcademicMaterialIdAndReporterUserId(...)`, así que Spring Data lo resuelve sin `@Query`.

### 6. `SupabaseStorageServiceImpl.delete` — hacerlo idempotente

Mantener la firma actual. Centralizar el comportamiento en el método de dos argumentos, porque `delete(path)` ya delega ahí:

```java
@Override
public void delete(String bucket, String path) {
    String deleteUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;

    try {
        restClient.delete()
                .uri(deleteUrl)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .retrieve()
                .toBodilessEntity();
    } catch (RestClientResponseException e) {
        if (isObjectNotFound(e)) {
            log.warn("Supabase delete skipped, object already absent - bucket: {}, path: {}",
                    bucket, path);
            return;
        }
        log.error("Supabase delete failed - bucket: {}, path: {}, status: {}, body: {}",
                bucket, path, e.getStatusCode(), e.getResponseBodyAsString(), e);
        throw new StorageServiceException(STORAGE_ERROR_MESSAGE, e);
    } catch (ResourceAccessException e) {
        log.error("Supabase delete unreachable - bucket: {}, path: {}", bucket, path, e);
        throw new StorageServiceException(STORAGE_ERROR_MESSAGE, e);
    }

    log.info("File deleted from Supabase Storage: {}/{}", bucket, path);
}

private boolean isObjectNotFound(RestClientResponseException exception) {
    if (exception.getStatusCode().value() == 404) {
        return true;
    }
    if (exception.getStatusCode().value() != 400) {
        return false;
    }
    String body = exception.getResponseBodyAsString();
    if (body == null) {
        return false;
    }
    String normalized = body.toLowerCase(Locale.ROOT);
    return normalized.contains("not found") || normalized.contains("not_found");
}
```

Notas:

- **No** tragarse 401, 403, 500, timeouts ni errores de red: esos siguen siendo `StorageServiceException` (502 + rollback).
- El caso 400 con cuerpo “Object not found”/`not_found` está contemplado porque algunas versiones de Supabase Storage no devuelven 404 al borrar un objeto inexistente. **Validar contra el Supabase real**; si el entorno devuelve otro formato, ajustar solo `isObjectNotFound`.
- No tocar `upload` ni `generateSignedUrl`.
- No cambiar el bucket: sale de `supabase.storage.materials-bucket`.

### 7. `GlobalExceptionHandler` — formato de error 502

Hoy el handler existe pero arma el `ApiError` al revés:

```java
ApiError error = new ApiError(HttpStatus.BAD_GATEWAY.value(), "Storage service error", ex.getMessage());
```

Cambiarlo a:

```java
@ExceptionHandler(StorageServiceException.class)
public ResponseEntity<ApiError> handleStorageService(StorageServiceException ex) {
    logger.error("Storage service error: {}", ex.getMessage(), ex);
    ApiError error = new ApiError(
            HttpStatus.BAD_GATEWAY.value(),
            ex.getMessage(),
            "STORAGE_ERROR");
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
}
```

Así `message` es el texto en español que ya construye `SupabaseStorageServiceImpl` (`STORAGE_ERROR_MESSAGE`) y `detail` es el código estable que consume el frontend. No devolver el stacktrace ni el body crudo de Supabase.

### 8. Nada más que tocar

- Entidad `AcademicMaterial`: sin cambios.
- `SecurityConfig`: sin cambios.
- `IAcademicMaterialRepository`: sin cambios (ya tiene `findByIdForUpdate`, `delete`, `flush`).
- `MaterialReportServiceImpl`, `MaterialReportIntakeServiceImpl`, `AdminMaterialReportServiceImpl`: sin cambios.
- `TutorProfileResponse` / listados públicos: sin cambios; al desaparecer la fila, dejan de listar el material automáticamente.
- No migraciones ni columnas nuevas.
- No notificaciones: US-17 no pide avisar a nadie.

## Consistencia transaccional

El orden es deliberado:

1. Se valida y se bloquea la fila.
2. Se borra la fila y se hace `flush`.
3. Se borra el objeto de Storage.
4. Si el paso 3 falla, la transacción hace rollback y la fila vuelve a existir. El tutor puede reintentar.
5. Si el paso 3 tiene éxito pero el commit falla, queda una fila apuntando a un archivo inexistente. Es el único hueco residual y se acepta para esta historia; **no** montar jobs de limpieza ni outbox.

No invertir el orden (Storage primero): si se borra el archivo y falla el borrado de la fila, queda un material descargable con URL rota, peor estado que el hueco residual.

## Testing

Respetar el estilo existente (JUnit 5, Mockito, AssertJ, `@DisplayName`). No cambiar el constructor de `MaterialServiceImpl`.

### Unit — `MaterialServiceImplTest`

Agregar a `src/test/java/com/knowlink/api/materials/service/implementations/MaterialServiceImplTest.java`:

- **Tutor dueño elimina**: `findByIdForUpdate` devuelve el material; `existsByMaterialAcademicMaterialId` devuelve `false`. Verificar `materialRepository.delete(material)`, `materialRepository.flush()` y `supabaseStorageService.delete(storagePath)`. Usar `InOrder` para exigir `delete → flush → storage.delete`.
- **Tutor no dueño**: `ResourceAccessDeniedException` con mensaje exacto; `verify(materialRepository, never()).delete(any())`, `verify(materialRepository, never()).flush()` y `verify(supabaseStorageService, never()).delete(any())`.
- **Material inexistente**: `ResourceNotFoundException` con `getErrorCode() == "MATERIAL_NOT_FOUND"`; nunca tocar Storage.
- **Material con denuncias**: `exists...` devuelve `true`; `BusinessConflictException` con errorCode `MATERIAL_HAS_REPORTS`; nunca borrar ni tocar Storage.
- **Material con `active = false` / `available = false` y sin denuncias**: se elimina igual (el mock de `findByIdForUpdate` devuelve esa entidad).
- **Storage lanza `StorageServiceException`**: la excepción se propaga. El rollback real se prueba en integración.
- **Concurrencia (opcional)**: no hace falta test unitario; el lock pesimista se cubre por diseño.

No hace falta duplicar todos los tests en `src/test/java/com/knowlink/api/materials/services/MaterialServiceImplTest.java`; con que ese archivo siga compilando y verde alcanza.

### Integración — `MaterialControllerImplTest`

Agregar al archivo existente (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Transactional`, H2, `@MockitoBean ISupabaseStorageService`):

- **DELETE como tutor dueño** → `204`; `materialRepository.findById(id)` vacío; `verify(supabaseStorageService).delete(existingMaterial.getStoragePath())`.
- **DELETE como otro tutor** → `403`; la fila sigue existiendo; `verify(supabaseStorageService, never()).delete(any())`.
- **DELETE como STUDENT** → `403`.
- **DELETE de UUID inexistente** → `404` y `jsonPath("$.detail").value("MATERIAL_NOT_FOUND")`.
- **DELETE con denuncia asociada** → `409` y `jsonPath("$.detail").value("MATERIAL_HAS_REPORTS")`; la fila sigue existiendo y `verify(supabaseStorageService, never()).delete(any())`. Crear la denuncia con `IAcademicMaterialReportRepository.save(AcademicMaterialReport.builder().material(existingMaterial).reporter(studentUser).reason(MaterialReportReason.PLAGIARISM).build())`.
- **DELETE exitoso + regresión AC2**: después del `204`, `GET /api/v1/materials/{id}/download` → `404`; `GET /api/v1/materials?subjectId=...` como TUTOR no incluye el material; `GET /api/v1/materials/tutores/{tutorId}/materiales` como alumno con acceso mockeado no incluye el material (o al menos el listado de materia).
- **Sin autenticación** → `401`.
- **Storage ya no tiene el objeto** → mockear `doNothing().when(supabaseStorageService).delete(path)` y esperar `204`; es el caso normal ya cubierto por el mock, pero el test de integración deja explícito que el servicio no falla si el mock no lanza.

Para el **502 con rollback**:

- La clase está anotada `@Transactional` a nivel de test; si el servicio lanza una excepción, la transacción de test queda marcada como rollback-only y consultar después puede fallar o dar resultados engañosos.
- Opción recomendada: crear un test class aparte `MaterialDeleteRollbackIntegrationTest` **sin** `@Transactional`, con su propio `@SpringBootTest`/`MockMvc`/H2, y en el test:
  1. Crear el fixture (usuarios, subject, tutorSubject, material).
  2. Configurar `doThrow(new StorageServiceException("...", new RuntimeException())).when(supabaseStorageService).delete(path)`.
  3. Ejecutar `DELETE`.
  4. Esperar `502`, `$.detail = "STORAGE_ERROR"`.
  5. Verificar con `materialRepository.findById(id)` que la fila **sigue existiendo** (rollback real, porque no hay transacción de test que la sostenga).
- Alternativa válida: anotar solo ese método con `@Transactional(propagation = Propagation.NOT_SUPPORTED)` en la clase existente y limpiar el fixture al final. Si se elige esta opción, no dejar el test dependiendo del orden de ejecución.

### Opcional — unit de Storage

`SupabaseStorageServiceImplTest` con `MockRestServiceServer` (el servicio construye su `RestClient` desde `RestClient.Builder`):

- `DELETE` 200 → no lanza.
- `DELETE` 404 → no lanza (idempotente).
- `DELETE` 400 con body `{"error":"Object not found"}` → no lanza.
- `DELETE` 500 → `StorageServiceException`.
- Timeout/red → `StorageServiceException`.
- Verificar que la URL contiene el bucket configurado y el path exacto; no hardcodear `materiales` en el test (usar la propiedad).

### Comandos

```bash
./mvnw test -q
./mvnw verify -B
```

No bajar la cobertura actual del módulo `materials`.

## Impacto en frontend

El frontend de US-17 ya espera `204`, `403`, `404` y `502`. Esta spec agrega un caso nuevo:

- `409 MATERIAL_HAS_REPORTS`: el interceptor de `httpClient` mostrará el mensaje del backend; el diálogo debe cerrarse o permanecer abierto sin bloquearse y el material debe seguir en el listado. **Actualizar el spec de frontend** para incluir `409` en la lista de errores esperados (no es bloqueante para el backend).

## No hacer

- ❌ No implementar soft delete ni usar `active`/`available` para esta historia.
- ❌ No crear tabla/historial de “materiales eliminados” ni conservar copias del archivo.
- ❌ No borrar denuncias, casos administrativos, descargos ni adjuntos de descargo para “poder borrar” el material.
- ❌ No implementar eliminación por admin (US-23/24) ni denuncias (US-27); esos flujos ya existen y no se tocan.
- ❌ No permitir borrar material ajeno bajo ningún rol.
- ❌ No agregar dependencias al constructor de `MaterialServiceImpl`.
- ❌ No cambiar `SecurityConfig` ni la entidad `AcademicMaterial`.
- ❌ No devolver `ex.getMessage()` técnico de Hibernate/Supabase al cliente; solo textos seguros en español y códigos estables.
- ❌ No hardcodear URLs, bucket ni keys de Supabase.
- ❌ No usar `removeFromCatalog` para este flujo.
- ❌ No inicializar `TutorSubject.academicMaterials` ni agregar cascadas nuevas.
- ❌ Comentarios, nombres y logs en inglés; solo los mensajes de cara al usuario en español.

## Definition of Done

- [ ] `DELETE /api/v1/materials/{id}` implementado en `IMaterialController` / `MaterialControllerImpl` con `@PreAuthorize("hasRole('TUTOR')")`, `@Operation` y `@ApiResponse`
- [ ] `IMaterialService.delete(UUID, UUID)` + `MaterialServiceImpl.delete` con `findByIdForUpdate`, verificación de propiedad y chequeo de denuncias antes de cualquier efecto
- [ ] Borrado de fila + `flush` + borrado de Storage dentro de una transacción; fallo de Storage → rollback
- [ ] `IAcademicMaterialReportRepository.existsByMaterialAcademicMaterialId(UUID)` agregado
- [ ] `SupabaseStorageServiceImpl.delete` idempotente para 404 / “not found”; el resto de errores sigue siendo `StorageServiceException`
- [ ] `GlobalExceptionHandler` devuelve `{status:502, message:<texto español>, detail:"STORAGE_ERROR"}`
- [ ] Tests unitarios y de integración de arriba en verde (`./mvnw verify -B`)
- [ ] Regresión AC2 verificada: descarga `404`, ausencia en listados de materia, tutor y perfil público
- [ ] `409 MATERIAL_HAS_REPORTS` verificado con una denuncia real en H2 y sin tocar el archivo
- [ ] Sin cambios en `SecurityConfig`, entidad, migraciones ni repositorio de materiales
- [ ] Commit(s) con prefijo `feat:` / `test:` en español, PR referenciando US-17
