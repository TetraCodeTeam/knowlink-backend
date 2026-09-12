# Spec Backend — Servicio de Envío de Mails (Gmail API)

## Metadata
- **Proyecto**: KnowLink (Proyecto Final — TetraCodeTeam)
- **Módulo**: Infraestructura transversal (no es una US numerada del backlog; es un servicio de soporte usado por otras historias que requieran notificar por mail)
- **Repo**: backend Spring Boot
- **Sprint**: 4
- **Autor del spec**: Julián Salvucci
- **Consumidores previstos**: cualquier US que dispare notificaciones (ej. confirmación de reserva, bienvenida de registro, aviso de material nuevo)

## Contexto y decisión
El equipo evaluó Resend como proveedor de envío de mails, pero su modo gratuito sin dominio verificado (`onboarding@resend.dev`) **solo permite enviar a la casilla del dueño de la cuenta** — cualquier otro destinatario devuelve 403. Verificar un dominio propio en Resend requiere comprarlo, y el equipo no cuenta con presupuesto.

Decisión: implementar el envío de mails contra la **Gmail API** (REST, OAuth2), usando una cuenta de Gmail de un integrante del equipo como remitente. Esto permite:
- Enviar a **cualquier destinatario real** sin restricciones de modo test.
- Operar sin costo y sin dominio propio.
- Comunicarse por HTTPS (no SMTP), evitando el bloqueo de puertos salientes 25/465/587 que aplica Render en su free tier.

## Acceptance Criteria (Gherkin)

```gherkin
Feature: Envío de notificaciones por mail desde el backend

  Scenario: Envío exitoso de un mail a un usuario real
    Given el backend tiene configuradas las credenciales OAuth2 de Gmail (client id, client secret, refresh token) como variables de entorno
    When se invoca EmailService.enviarCorreo(destinatario, asunto, cuerpoHtml)
    Then el servicio obtiene un access token vigente desde el endpoint de OAuth2 de Google
    And arma el mensaje en formato RFC 2822 codificado en base64url
    And realiza un POST a la Gmail API (users/me/messages/send)
    And el destinatario recibe el mail sin restricción de "modo test"

  Scenario: Renovación automática del access token
    Given el access token cacheado está vencido o no existe
    When se solicita un nuevo envío
    Then el servicio pide un access token nuevo usando el refresh token
    And no requiere intervención humana ni un nuevo flujo de login

  Scenario: Falla en el envío no debe romper el flujo principal
    Given un llamado a un endpoint de negocio (ej. registro de usuario) dispara un mail de bienvenida
    When el envío de mail falla (timeout, error de Gmail API, credenciales inválidas)
    Then la operación de negocio principal se completa igual
    And el error de envío queda registrado en el log
    And no se propaga una excepción no controlada al cliente

  Scenario: Configuración faltante en un ambiente
    Given falta alguna variable de entorno requerida (GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET, GMAIL_REFRESH_TOKEN, GMAIL_SENDER_ADDRESS)
    When la aplicación intenta levantar el EmailService
    Then se loguea un error claro indicando qué variable falta
    And la aplicación no falla en el arranque (el envío de mail es una funcionalidad best-effort, no bloqueante)
```

## Stack confirmado
- Java 21 + Spring Boot 3.5
- `spring-boot-starter-webflux` para `WebClient` (llamadas HTTP a Google OAuth2 y Gmail API)
- Sin dependencia de `spring-boot-starter-mail` / `JavaMailSender` — no se usa SMTP
- Variables de entorno (Render → Environment): `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GMAIL_REFRESH_TOKEN`, `GMAIL_SENDER_ADDRESS`

## Advertencias de dependencias
- Si `spring-boot-starter-mail` ya está agregado en el `pom.xml` por un intento anterior con Resend/SMTP, **no removerlo automáticamente** sin confirmar — puede haber otro código dependiendo de `JavaMailSender`. Si no se usa en ningún otro lado, marcarlo para remoción en un commit separado.
- Verificar que `spring-boot-starter-webflux` no entre en conflicto con un posible `spring-boot-starter-web` ya presente (ambos pueden convivir; `WebClient` no requiere levantar un server reactivo).

## Ubicación de clases / paquete
- `co.knowlink.backend.email.EmailService` — interfaz o clase de servicio con el método público `enviarCorreo(String destinatario, String asunto, String cuerpoHtml)`
- `co.knowlink.backend.email.GmailApiClient` — clase interna encargada de: (a) obtener/cachear el access token vía refresh token, (b) construir el mensaje RFC 2822 en base64url, (c) hacer el POST a la Gmail API
- `co.knowlink.backend.email.EmailProperties` — `@ConfigurationProperties` para mapear las variables de entorno (`gmail.client-id`, `gmail.client-secret`, `gmail.refresh-token`, `gmail.sender-address`)
- El método `enviarCorreo` debe marcarse `@Async` (requiere `@EnableAsync` en la configuración) para no bloquear el hilo del request mientras se resuelve el envío

## No hacer
- No usar `JavaMailSender`/SMTP para este flujo — quedó descartado por el bloqueo de puertos en Render free tier.
- No hardcodear `client_secret` ni `refresh_token` en código ni en `application.properties` versionado — solo variables de entorno.
- No hacer que una falla de envío de mail tire una excepción que rompa el endpoint de negocio que lo dispara (ver escenario 3).
- No implementar un flujo de login interactivo en producción — el refresh token se genera una única vez, manualmente, fuera del ciclo de deploy.
- No asumir que hay un límite ilimitado de envíos: la cuenta de Gmail usada como remitente tiene un tope diario (uso personal, no es un servicio transaccional dedicado); no diseñar para volumen alto sin revisar esto primero.

## Definition of Done
- [ ] `EmailService.enviarCorreo(...)` implementado y probado enviando a un destinatario real (no la cuenta remitente)
- [ ] Manejo de expiración/renovación del access token sin intervención manual
- [ ] Fallos de envío logueados y no propagados como excepción no controlada
- [ ] Variables de entorno documentadas (README o `.env.example`) y cargadas en Render
- [ ] Método de envío marcado `@Async`, sin bloquear el hilo del request original
- [ ] Al menos un test de integración (o manual documentado) contra un destinatario real distinto de la cuenta remitente
