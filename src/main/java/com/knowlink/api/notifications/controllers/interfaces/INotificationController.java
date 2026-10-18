package com.knowlink.api.notifications.controllers.interfaces;

import com.knowlink.api.notifications.controllers.responses.NotificationResponse;
import com.knowlink.api.security.models.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/notifications")
@Tag(name = "Notificaciones", description = "Centro de notificaciones del usuario autenticado")
public interface INotificationController {

    @GetMapping
    @Operation(summary = "Listar las notificaciones del usuario")
    @ApiResponse(responseCode = "200", description = "Notificaciones recuperadas")
    @ResponseStatus(OK)
    List<NotificationResponse> list(@AuthenticationPrincipal UserPrincipal principal);

    @GetMapping("/unread-count")
    @Operation(summary = "Consultar la cantidad de notificaciones no leídas")
    @ApiResponse(responseCode = "200", description = "Cantidad de novedades no leídas")
    @ResponseStatus(OK)
    Map<String, Long> unreadCount(@AuthenticationPrincipal UserPrincipal principal);

    @GetMapping("/support/unread-count")
    @Operation(summary = "Consultar la cantidad de novedades no leídas de soporte")
    @ApiResponse(responseCode = "200", description = "Cantidad de reclamos y denuncias de soporte con novedades")
    @ResponseStatus(OK)
    Map<String, Long> supportUnreadCount(@AuthenticationPrincipal UserPrincipal principal);

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Marcar una notificación como leída")
    @ApiResponse(responseCode = "200", description = "Notificación marcada como leída")
    @ApiResponse(responseCode = "404", description = "La notificación no existe")
    @ResponseStatus(OK)
    void markAsRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID notificationId);

    @PatchMapping("/read-all")
    @Operation(summary = "Marcar todas las notificaciones como leídas")
    @ApiResponse(responseCode = "200", description = "Notificaciones marcadas como leídas")
    @ResponseStatus(OK)
    void markAllAsRead(@AuthenticationPrincipal UserPrincipal principal);
}
