# US-08 - Buscar tutores o materias

## Objetivo

Implementar la funcionalidad de búsqueda utilizada por un usuario autenticado con rol **STUDENT**.

Antes de generar código:

1. Analiza la historia de usuario.
2. Identifica las entidades existentes que pueden reutilizarse.
3. No crear entidades nuevas si el modelo actual ya las contempla.
4. Explica brevemente cualquier decisión de diseño antes de implementar.

---

## Historia de Usuario

Como alumno, quiero buscar ingresando el nombre de una materia o de un tutor en un único campo, para encontrar rápidamente lo que necesito sin tener que saber de antemano si busco por uno u otro criterio.

---

## Criterios de aceptación

* El buscador posee un único campo de texto.
* El usuario puede escribir el nombre de una materia o un tutor.
* La búsqueda devuelve resultados mixtos organizados en:

  * Materias
  * Tutores
* Al seleccionar una materia deben mostrarse todos los tutores que la dictan.
* Al seleccionar un tutor debe visualizarse su perfil completo.
* Las sugerencias deben actualizarse dinámicamente mientras el usuario escribe.
* Si no existen resultados debe responder:
  "No se encontraron resultados para tu búsqueda."
* Sólo pueden utilizar el buscador usuarios cuyo rol activo sea STUDENT.
* Usuarios con rol activo TUTOR no pueden acceder al endpoint.

---

## Modelo de dominio existente

Reutilizar únicamente las entidades existentes:

* Usuario
* PerfilAlumno
* PerfilTutor
* Materia
* MateriaTutor

No crear entidades nuevas.

---

## Endpoint

Diseñar el endpoint REST más apropiado.

Se recomienda:

GET /api/v1/search

Ejemplo:

GET /api/v1/search?q=mate

---

## Respuesta esperada

La respuesta debe separar claramente ambas secciones.

Ejemplo:

{
"materias":[
{
"id":1,
"nombre":"Matemática"
}
],
"tutores":[
{
"id":12,
"nombre":"Juan Pérez",
"fotoPerfil":"...",
"calificacionPromedio":4.8,
"verificado":true
}
]
}

Si ambas listas están vacías responder:

{
"materias":[],
"tutores":[],
"mensaje":"No se encontraron resultados para tu búsqueda."
}

---

## Repositories

Reutilizar los existentes.

Agregar únicamente los métodos necesarios.

Ejemplos:

UsuarioRepository

* buscar tutores por nombre

MateriaRepository

* buscar materias por nombre

---

## Service

Generar SearchService.

Responsabilidades:

* validar rol STUDENT
* buscar materias
* buscar tutores
* excluir usuarios que únicamente poseen perfil STUDENT
* devolver respuesta agrupada
* manejar búsqueda sin resultados

Toda la lógica de negocio debe implementarse en Service.

---

## Controller

Generar SearchController.

Debe:

* utilizar constructor injection
* documentar con OpenAPI
* utilizar ResponseEntity
* obtener el usuario autenticado desde Spring Security

---

## DTOs

Generar únicamente:

SearchResponse

SubjectSearchResponse

TutorSearchResponse

No exponer entidades JPA.

---

## Seguridad

Registrar el endpoint en SecurityConfig.

Acceso permitido únicamente para:

ROLE_STUDENT

Si el usuario posee rol activo TUTOR devolver:

403 Forbidden

---

## Swagger

Documentar:

* @Operation
* @ApiResponses
* @ApiResponse

---

## Tests

Generar:

SearchServiceTest

Casos:

* búsqueda por materia
* búsqueda por tutor
* búsqueda mixta
* sin resultados
* usuario con rol incorrecto
* usuario no autenticado

SearchControllerTest

Casos:

* 200 OK
* 403 Forbidden
* 401 Unauthorized

---

## Restricciones

* No crear lógica de negocio en Controller.
* No acceder a Repository desde Controller.
* No utilizar Optional.get().
* No utilizar System.out.println().
* Utilizar Lombok.
* Utilizar constructor injection.
* Respetar AGENTS.md y docs/base-standards.md.

Antes de generar el código, explica el diseño propuesto y verifica que reutiliza el modelo de dominio existente.

Antes de implementar esta historia, toma como referencia la implementación de la US-07 "Ver perfil de un tutor como alumno".

Reutiliza el endpoint, DTOs y lógica del perfil de tutor cuando corresponda. No dupliques funcionalidad existente. Si la búsqueda devuelve un tutor, el resultado debe permitir acceder al endpoint del perfil implementado en la US-07.

