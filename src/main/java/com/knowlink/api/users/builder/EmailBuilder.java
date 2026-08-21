package com.knowlink.api.users.builder;

import com.knowlink.api.users.data.models.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.UUID;

@Component
public class EmailBuilder {

    @Value("${frontend.url}")
    private String frontendUrl;

    public String buildConfirmAccount(User user, UUID confirmationToken) {
        String confirmationLink = frontendUrl + "/auth/activate?userId=" + user.getUserId()
                + "&token=" + confirmationToken;

        return wrapTemplate(
                "¡Bienvenido a Knowlink!",
                user.getFullName(),
                "Gracias por registrarte. Para completar tu registro, confirmá tu dirección de correo electrónico haciendo clic en el botón de abajo:",
                confirmationLink,
                "Confirmar cuenta",
                "Si no creaste esta cuenta, podés ignorar este correo sin ningún problema.");
    }

    public String buildResendConfirmAccount(User user, UUID confirmationToken) {
        String confirmationLink = frontendUrl + "/auth/activate?userId=" + user.getUserId()
                + "&token=" + confirmationToken;

        return wrapTemplate(
                "Nuevo enlace de confirmación",
                user.getFullName(),
                "Solicitaste un nuevo enlace de confirmación para tu cuenta. Confirmá tu dirección de correo electrónico haciendo clic en el botón de abajo:",
                confirmationLink,
                "Confirmar cuenta",
                "Si no creaste esta cuenta, podés ignorar este correo sin ningún problema.");
    }

    public String buildResetPassword(User user, UUID token) {
        String resetLink = frontendUrl + "/auth/reset-password?token=" + token;

        return wrapTemplate(
                "Restablecé tu contraseña",
                user.getFullName(),
                "Recibimos una solicitud para restablecer la contraseña de tu cuenta de Knowlink. Hacé clic en el botón de abajo para continuar:",
                resetLink,
                "Restablecer contraseña",
                "Si no solicitaste esto, podés ignorar este correo sin ningún problema.");
    }

    private String wrapTemplate(String heading, String recipientName, String bodyText,
                                 String actionUrl, String actionLabel, String footerText) {
        String safeRecipientName = HtmlUtils.htmlEscape(recipientName);

        return """
                    <html>
                        <head>
                            <style>
                                a.action-button {
                                    display: inline-block;
                                    margin-top: 25px;
                                    padding: 14px 28px;
                                    background-color: #4361ee;
                                    color: white !important;
                                    text-decoration: none;
                                    border-radius: 6px;
                                    font-weight: bold;
                                }
                            </style>
                        </head>
                        <body style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #0f0e17; padding: 40px; text-align: center;">
                            <div style="max-width: 600px; margin: auto; background: #1a1a2e; padding: 30px; border-radius: 10px; box-shadow: 0 4px 15px rgba(0,0,0,0.3);">
                                <h2 style="color: #4361ee; margin-bottom: 20px;">%s</h2>
                                <p style="font-size: 16px; color: #ffffff;"><strong>¡Hola %s!</strong></p>
                                <p style="font-size: 16px; color: #cccccc;">%s</p>
                                <a href="%s" class="action-button">%s</a>
                                <p style="margin-top: 25px; font-size: 14px; color: #888888;">%s</p>
                            </div>
                        </body>
                    </html>
                """
                .formatted(heading, safeRecipientName, bodyText, actionUrl, actionLabel, footerText);
    }
}