# US-07 - Ver perfil de tutor como alumno

## Contexto

Implementar la visualización pública del perfil de un tutor para usuarios autenticados con rol ALUMNO.

## Modelo de dominio existente

Utilizar exclusivamente las siguientes entidades ya definidas:

### Usuario

* id
* nombreCompleto
* email
* numeroCelular
* rolActivo

### PerfilTutor

* biografia
* calificacionPromedio
* carrera
* fotoPerfil
* verificado
* tiempoMinAntelacionHoras

### MateriaTutor

* tipoCompensacion
* precio
* descripcion
* modalidad
* estado

### Materia

* nombre

### MaterialAcademico

* nombre
* urlArchivo
* fechaSubida
* disponible
* tipoMaterial

### Calificacion

* puntuacion
* comentario
* fechaCalificacion
* visible

### BloqueDisponibilidad

* dia
* horaInicio
* horaFin
* disponible

### SlotHorario

* fecha
* horaInicio
* horaFin
* estadoSlot

### Reserva

* estadoReserva
* fechaSesion

## Endpoint requerido

GET /api/v1/tutors/{tutorId}/profile

## Autorización

* Sólo usuarios autenticados.
* Sólo rol ALUMNO.
* Utilizar Spring Security.
* Implementar @PreAuthorize.

## Información visible

El endpoint debe devolver:

### Datos del tutor

* id
* nombreCompleto
* biografia
* carrera
* fotoPerfil
* verificado
* calificacionPromedio

### Materias

Por cada MateriaTutor:

* materia
* descripcion
* modalidad
* tipoCompensacion
* precio

### Calificaciones

Sólo calificaciones visibles.

Por cada una:

* puntuacion
* comentario
* fechaCalificacion

### Disponibilidad

Mostrar:

* dia
* horaInicio
* horaFin

## Restricciones

NO exponer:

* email
* numeroCelular
* DNI
* contraseña
* token de autenticación
* datos de MercadoPago

## Regla de materiales

Verificar si existe al menos una Reserva entre:

* alumno autenticado
* tutor solicitado

con estado:

* RESERVADA
* EN_CURSO
* REALIZADA

Si existe:

retornar materiales disponibles.

Si no existe:

retornar lista vacía.

## DTOs requeridos

Generar:

* TutorProfileResponse
* TutorSubjectResponse
* TutorReviewResponse
* TutorAvailabilityResponse
* TutorMaterialResponse

No exponer entidades JPA.

## Repositories esperados

* PerfilTutorRepository
* ReservaRepository
* CalificacionRepository (si aplica)

Agregar únicamente métodos necesarios.

## Service requerido

TutorProfileService

Método principal:

getTutorProfile(Long tutorId, Long alumnoId)

Responsabilidades:

* obtener tutor
* validar existencia
* obtener materias
* obtener disponibilidad
* obtener calificaciones visibles
* verificar acceso a materiales
* construir response

## Controller requerido

TutorController

GET /api/v1/tutors/{tutorId}/profile

Debe obtener el usuario autenticado desde Spring Security.

## Swagger

Agregar:

* @Operation
* @ApiResponses
* @ApiResponse

## Tests

Generar:

### TutorProfileServiceTest

Casos:

* tutor existente
* tutor inexistente
* alumno con reserva
* alumno sin reserva
* materiales visibles
* materiales ocultos

### TutorControllerTest

Casos:

* acceso autorizado
* acceso sin autenticación
* acceso con rol incorrecto

## SecurityConfig

Registrar:

GET /api/v1/tutors/*/profile

Acceso:

ROLE_ALUMNO

## Importante

No crear nuevas entidades salvo que sean estrictamente necesarias.

Reutilizar exclusivamente las entidades existentes del modelo de dominio.
