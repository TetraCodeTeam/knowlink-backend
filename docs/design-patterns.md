# KnowLink Backend — Patrones de diseño aplicados

## Contexto

Este documento registra los patrones de diseño (GoF) que se aplicaron deliberadamente
en el backend de KnowLink, junto con el razonamiento que llevó a esa decisión. También
registra patrones que se **evaluaron y se descartaron conscientemente**, porque el
código se parecía superficialmente al patrón pero no cumplía su estructura real.

El objetivo es doble: que quede un criterio consistente para futuras decisiones de
arquitectura, y que sirva como respaldo documentado para la defensa de proyecto final — mostrar
no solo qué patrones se usaron, sino por qué otros nombres de patrones que hubiera sido
tentador aplicar (Mediator, Builder) no correspondían.

Complementa a `base-standards.md`, que cubre convenciones de código; este documento cubre
decisiones de diseño puntuales, no convenciones generales.

---

## Criterio para documentar algo acá

Un patrón se documenta en este archivo únicamente si se cumplen las dos condiciones:

1. **Coincide con la estructura real del patrón**, no solo con su intención general.
   Agrupar un par de llamadas a repositorios en un `@Service` no es, por sí solo,
   ningún patrón GoF — es arquitectura en capas estándar.
2. **Hubo una decisión de diseño con alternativas reales evaluadas**, no una elección
   obvia. Si el patrón era la única forma razonable de resolver el problema, no aporta
   documentar la decisión.

---

## Patrones aplicados

### Facade — envío de notificaciones por email

| | |
|---|---|
| **Patrón** | Facade |
| **Estado** | Aplicado y confirmado |
| **Clase fachada** | `EmailServiceImpl` (`com.knowlink.api.users.services.implementations`), implementa `IEmailService` |
| **Subsistema que oculta** | `JavaMailSender`, `MimeMessageHelper`, `MimeMessage` (Jakarta Mail, vía `spring-boot-starter-mail`) |
| **Cliente** | `UserNotificationListener` (`com.knowlink.api.users.listeners`) |

**Por qué corresponde:**

- El subsistema (API de Jakarta Mail) requiere inicializar varios objetos en orden
  (`createMimeMessage()` → `MimeMessageHelper` → `setFrom/setTo/setSubject/setText` →
  `send()`) — exactamente el escenario que el patrón está pensado para resolver.
- `IEmailService` expone solo tres métodos de negocio (`sendConfirmAccountEmail`,
  `sendResendConfirmAccountEmail`, `sendResetPasswordEmail`) — el cliente nunca importa
  `jakarta.mail`.
- El subsistema no conoce a la fachada: `JavaMailSender` no sabe que `EmailServiceImpl`
  existe, es una dependencia inyectada más.
- Si se reemplazara `JavaMailSender` por otro proveedor (ej. una API HTTP de terceros),
  el único archivo que cambiaría es `EmailServiceImpl` — ni `IEmailService` ni
  `UserNotificationListener` se enterarían.

**Relación con Observer/Eventos:**

Este Facade convive con `ApplicationEventPublisher` + `@TransactionalEventListener`
(`UserRegisteredEvent`, `ResendConfirmationEvent`, `PasswordResetRequestedEvent`). Son
patrones complementarios, no alternativos: los eventos resuelven **cuándo** notificar
(desacoplado de la transacción de negocio, disparado recién en `AFTER_COMMIT`), y el
Facade resuelve **cómo** se arma y envía cada email (oculto detrás de una interfaz
simple). Ninguno reemplaza al otro.

**Nota sobre `EmailBuilder`:** la clase que arma el HTML de cada email
(`com.knowlink.api.users.builder.EmailBuilder`) **no es** el patrón Builder de GoF, pese
al nombre. El Builder real construye un objeto complejo paso a paso con métodos
encadenables y un `.build()` final; acá cada método arma y devuelve un `String`
completo en una sola llamada. Es un *formatter* de contenido, no un Builder. El nombre
de la clase se mantiene por legibilidad, pero no debe citarse como ejemplo del patrón
Builder en documentación académica.

---

## Patrones evaluados y descartados

### Mediator — comunicación entre `tutors` y `students`

**Contexto del problema:** `TutorProfileServiceImpl` y `StudentProfileServiceImpl`
necesitan conocerse mutuamente (activación cruzada de roles, chequeo de existencia de
perfil del otro tipo). Inyectar la interfaz de servicio completa de un lado en el otro
genera dependencia circular de beans con inyección por constructor
(`BeanCurrentlyInCreationException` en Spring).

**Por qué se consideró Mediator:** un mediador centraliza la comunicación entre
componentes que de otro modo se acoplarían directamente entre sí — a primera vista
parecía el problema exacto que describe el patrón.

**Por qué se descartó:**

- El patrón exige que los componentes **dejen de conocerse entre sí por completo** y
  solo conozcan al mediador. Acá `TutorProfileServiceImpl` y `StudentProfileServiceImpl`
  siguen comunicándose de forma directa para las operaciones de escritura
  (`createProfileFromTutorData`, persistencia cruzada de perfiles) — un mediador
  "parcial" que solo cubre el chequeo de existencia no es el patrón, es una
  optimización puntual.
- El patrón centraliza además la **decisión de comportamiento** (qué hacer en
  respuesta a una notificación), no solo el dato. Lo que se necesitaba acá era una
  consulta de solo lectura (`hasTutorProfile` / `hasStudentProfile`), no una
  orquestación de eventos.
- Envolver los dos servicios completos en un mediador no elimina el ciclo, lo mueve un
  salto más allá (`Mediador → ITutorProfileService → IStudentProfileService → …`).

**Solución aplicada en su lugar** *(sujeta a confirmar si ya está implementada en el
repo — surgió como recomendación en la sesión de diseño)*:

- **Extract Class** para la regla de negocio compartida de armado de materias
  (`ITutorSubjectAssemblyService` / `TutorSubjectAssemblyServiceImpl`, en
  `com.knowlink.api.tutors.services`), consumida tanto por
  `TutorProfileServiceImpl.createProfile` como por
  `StudentProfileServiceImpl.activateTutorRole`.
- **Colaborador compartido de bajo nivel**, sin dependencia hacia ninguno de los dos
  servicios de dominio (`IUserProfileLookupService` /
  `UserProfileLookupServiceImpl`, en `com.knowlink.api.users.services`), que envuelve
  directamente `ITutorProfileRepository` + `IStudentProfileRepository` para resolver
  `hasTutorProfile(userId)` / `hasStudentProfile(userId)` sin ciclo posible.

Ninguna de las dos es Mediator — son técnicas de refactorización estándar (Extract
Class) aplicadas para resolver un problema real de acoplamiento circular, sin necesidad
de forzarle el nombre de un patrón de comportamiento de GoF.

---

## Checklist antes de documentar algo como Facade

- [ ] ¿El subsistema tiene múltiples clases (propias o de terceros) que hay que
      inicializar/coordinar en un orden específico?
- [ ] ¿La clase envolvente **redirige** llamadas en vez de agregar lógica de negocio
      nueva?
- [ ] ¿El subsistema desconoce por completo la existencia de la fachada?
- [ ] ¿Reemplazar la implementación del subsistema afectaría solo a la fachada, y a
      ningún cliente?

Si alguna respuesta es "no", probablemente sea otra cosa — arquitectura en capas
estándar, Extract Class, un servicio de validación — y no corresponde documentarlo acá
como Facade.

---

## Historial de decisiones

| Fecha | Patrón evaluado | Decisión | Archivos involucrados |
|---|---|---|---|
| 2026-08-21 | Facade | Confirmado — envío de emails | `EmailServiceImpl`, `IEmailService`, `EmailBuilder`, `UserNotificationListener` |
| 2026-08-21 | Facade | Candidato sin confirmar | `JwtService` |
| 2026-08-21 | Mediator | Descartado — resuelto con Extract Class | `ITutorSubjectAssemblyService`, `IUserProfileLookupService` |
| 2026-08-21 | Builder | Descartado — `EmailBuilder` es un formatter, no un Builder GoF | `EmailBuilder` |