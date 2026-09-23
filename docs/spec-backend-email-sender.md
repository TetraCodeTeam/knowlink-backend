# Spec Backend — Servicio de Envío de Mails (Brevo API)

## Metadata
- **Proyecto**: KnowLink (Proyecto Final — TetraCodeTeam)
- **Módulo**: Infraestructura transversal (no es una US numerada del backlog; es un servicio de soporte usado por otras historias que requieran notificar por mail)
- **Repo**: backend Spring Boot
- **Sprint**: 4
- **Autor del spec**: Julián Salvucci
- **Consumidores previstos**: cualquier US que dispare notificaciones (ej. confirmación de reserva, bienvenida de registro, aviso de material nuevo)

## Contexto y decisión
El equipo probó, en orden, tres proveedores:

1. **Resend** — descartado: en modo sin dominio verificado (`onboarding@resend.dev`) solo permite enviar a la casilla del dueño de la cuenta. Verificar dominio propio requiere comprarlo, y el equipo no tiene presupuesto.
2. **Gmail API (OAuth2)** — descartado: Google Cloud exige asociar una cuenta de facturación (tarjeta) al proyecto para habilitar la API, aunque el uso sea gratuito.
3. **SMTP2GO** — descartado: el registro de cuenta rechaza mails de webmail gratuito ("Error code 6 - Please use an email at your own domain to sign up"), y ningún integrante tiene mail de dominio propio.

Decisión final: implementar el envío de mails contra la **API REST de Brevo** (ex Sendinblue). Es el único de los tres que:
- Permite registrarse con cualquier mail, incluido Gmail.
- Verifica el remitente por link/código enviado a esa misma casilla ("Single Sender Verification"), **sin necesitar dominio propio**.
- Permite enviar a **cualquier destinatario real** una vez verificado el remitente.
- Comunica por HTTPS, evitando el bloqueo de puertos SMTP salientes (25/465/587) del free tier de Render.
- Plan free: 300 emails/día, para siempre, sin tarjeta.

**Riesgo a validar temprano**: desde 2024, Gmail y Yahoo exigen autenticación de dominio (SPF/DKIM vía dominio propio) para remitentes de **alto volumen**. Para el volumen de un proyecto de facultad esto no debería bloquear la entrega, pero conviene probar el envío contra una casilla de Gmail real cuanto antes en el desarrollo, no recién en la demo final, para detectar a tiempo si algún mail cae en spam.

## Acceptance Criteria (Gherkin)

```gherkin
Feature: Envío de notificaciones por mail desde el backend

  Scenario: Envío exitoso de un mail a un usuario real
    Given el backend tiene configuradas BREVO_API_KEY y BREVO_SENDER_ADDRESS como variables de entorno
    And BREVO_SENDER_ADDRESS corresponde a un remitente verificado en Brevo ("Single Sender Verification")
    When se invoca EmailService.enviarCorreo(destinatario, asunto, cuerpoHtml)
    Then el servicio arma el payload JSON con sender {name, email}, to (array de {email, name}), subject y htmlContent
    And realiza un POST a https://api.brevo.com/v3/smtp/email con el header api-key
    And el destinatario recibe el mail sin restricción de "modo test"

  Scenario: Falla en el envío no debe romper el flujo principal
    Given un llamado a un endpoint de negocio (ej. registro de usuario) dispara un mail de bienvenida
    When el envío de mail falla (timeout, error de Brevo, remitente no verificado, límite diario excedido)
    Then la operación de negocio principal se completa igual
    And el error de envío queda registrado en el log
    And no se propaga una excepción no controlada al cliente

  Scenario: Configuración faltante en un ambiente
    Given falta alguna variable de entorno requerida (BREVO_API_KEY, BREVO_SENDER_ADDRESS)
    When la aplicación intenta levantar el EmailService
    Then se loguea un error claro indicando qué variable falta
    And la aplicación no falla en el arranque (el envío de mail es una funcionalidad best-effort, no bloqueante)

  Scenario: Validación temprana de entrega a Gmail/Yahoo
    Given el remitente está verificado pero no autenticado por dominio propio (sin SPF/DKIM de dominio)
    When se manda un mail de prueba a una casilla de Gmail real durante el desarrollo
    Then el equipo confirma que el mail llega a la bandeja de entrada (no a spam)
    And si cae en spam, se evalúa antes de la entrega final si hace falta un dominio autenticado
```

## Stack confirmado
- Java 21 + Spring Boot 3.5
- `spring-boot-starter-webflux` para `WebClient` (llamadas HTTP a la API de Brevo)
- Sin dependencia de `spring-boot-starter-mail` / `JavaMailSender` — no se usa SMTP
- Variables de entorno (Render → Environment): `BREVO_API_KEY`, `BREVO_SENDER_ADDRESS`, `BREVO_SENDER_NAME`
- Endpoint: `POST https://api.brevo.com/v3/smtp/email`, header `api-key`, body JSON:
  ```json
  {
    "sender": { "name": "KnowLink", "email": "remitente@verificado.com" },
    "to": [ { "email": "usuario@real.com", "name": "Nombre Usuario" } ],
    "subject": "Asunto",
    "htmlContent": "<p>...</p>"
  }
  ```

## Advertencias de dependencias
- Si `spring-boot-starter-mail` ya está agregado en el `pom.xml` por un intento anterior con Resend/SMTP, **no removerlo automáticamente** sin confirmar — puede haber otro código dependiendo de `JavaMailSender`. Si no se usa en ningún otro lado, marcarlo para remoción en un commit separado.
- Verificar que `spring-boot-starter-webflux` no entre en conflicto con un posible `spring-boot-starter-web` ya presente (ambos pueden convivir; `WebClient` no requiere levantar un server reactivo).
- El remitente configurado en `BREVO_SENDER_ADDRESS` debe estar previamente verificado en el panel de Brevo (Senders, Domains & Dedicated IPs → Senders). Un envío desde un remitente no verificado es rechazado por la API.

## Ubicación de clases / paquete
- `co.knowlink.backend.email.EmailService` — interfaz o clase de servicio con el método público `enviarCorreo(String destinatario, String asunto, String cuerpoHtml)`
- `co.knowlink.backend.email.BrevoClient` — clase interna encargada de construir el payload JSON y hacer el POST a `v3/smtp/email`
- `co.knowlink.backend.email.EmailProperties` — `@ConfigurationProperties` para mapear las variables de entorno (`brevo.api-key`, `brevo.sender-address`, `brevo.sender-name`)
- El método `enviarCorreo` debe marcarse `@Async` (requiere `@EnableAsync` en la configuración) para no bloquear el hilo del request mientras se resuelve el envío

## No hacer
- No usar `JavaMailSender`/SMTP para este flujo — quedó descartado por el bloqueo de puertos en Render free tier.
- No usar la Gmail API — quedó descartada porque Google Cloud exige cuenta de facturación (tarjeta) para habilitar la API, aunque el uso sea gratuito.
- No usar SMTP2GO — quedó descartado porque el registro de cuenta rechaza mails de webmail gratuito.
- No hardcodear el `api-key` de Brevo en código ni en `application.properties` versionado — solo variables de entorno.
- No hacer que una falla de envío de mail tire una excepción que rompa el endpoint de negocio que lo dispara (ver escenario de falla).
- No intentar mandar mails desde un remitente sin verificar en Brevo — la API va a rechazar ese envío.
- No dar por sentado que la entrega a Gmail/Yahoo funciona sin probarla — validar temprano (ver escenario de validación).

## Definition of Done
- [ ] Remitente verificado en el panel de Brevo (Single Sender Verification) antes de dar por cerrada la historia
- [ ] `EmailService.enviarCorreo(...)` implementado y probado enviando a un destinatario real (no la cuenta del remitente)
- [ ] Prueba de entrega contra al menos una casilla de Gmail real, confirmando que no cae en spam
- [ ] Fallos de envío logueados y no propagados como excepción no controlada
- [ ] Variables de entorno documentadas (README o `.env.example`) y cargadas en Render
- [ ] Método de envío marcado `@Async`, sin bloquear el hilo del request original
