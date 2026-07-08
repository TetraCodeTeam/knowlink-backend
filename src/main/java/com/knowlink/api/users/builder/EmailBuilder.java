package com.knowlink.api.users.builder;

import com.knowlink.api.users.data.models.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EmailBuilder {

    @Value("${frontend.url}")
    private String frontendUrl;

    public String buildConfirmAccount(User user, UUID confirmationToken) {
        String confirmationLink = frontendUrl + "/auth/activate?userId=" + user.getUserId()
                + "&token=" + confirmationToken;

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
                                <h2 style="color: #4361ee; margin-bottom: 20px;">¡Bienvenido a Knowlink!</h2>
                                <p style="font-size: 16px; color: #ffffff;"><strong>¡Hola %s!</strong></p>
                                <p style="font-size: 16px; color: #cccccc;">
                                    Gracias por registrarte. Para completar tu registro, confirmá tu dirección de correo electrónico haciendo clic en el botón de abajo:
                                </p>
                                <a href="%s" class="action-button">Confirmar cuenta</a>
                                <p style="margin-top: 25px; font-size: 14px; color: #888888;">
                                    Si no creaste esta cuenta, podés ignorar este correo sin ningún problema.
                                </p>
                            </div>
                        </body>
                    </html>
                """
                .formatted(user.getEmail(), confirmationLink);
    }

    public String buildResendConfirmAccount(User user, UUID confirmationToken) {
        String confirmationLink = frontendUrl + "/auth/activate?userId=" + user.getUserId()
                + "&token=" + confirmationToken;

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
                                <h2 style="color: #4361ee; margin-bottom: 20px;">Nuevo enlace de confirmación</h2>
                                <p style="font-size: 16px; color: #ffffff;"><strong>¡Hola %s!</strong></p>
                                <p style="font-size: 16px; color: #cccccc;">
                                    Solicitaste un nuevo enlace de confirmación para tu cuenta.
                                    Confirmá tu dirección de correo electrónico haciendo clic en el botón de abajo:
                                </p>
                                <a href="%s" class="action-button">Confirmar cuenta</a>
                                <p style="margin-top: 25px; font-size: 14px; color: #888888;">
                                    Si no creaste esta cuenta, podés ignorar este correo sin ningún problema.
                                </p>
                            </div>
                        </body>
                    </html>
                """
                .formatted(user.getEmail(), confirmationLink);
    }

    public String buildResetPassword(String userEmail, UUID token) {
        String resetLink = frontendUrl + "/auth/reset-password?token=" + token;

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
                                <h2 style="color: #4361ee; margin-bottom: 20px;">Restablecé tu contraseña</h2>
                                <p style="font-size: 16px; color: #ffffff;"><strong>¡Hola %s!</strong></p>
                                <p style="font-size: 16px; color: #cccccc;">
                                    Recibimos una solicitud para restablecer la contraseña de tu cuenta de Knowlink.
                                    Hacé clic en el botón de abajo para continuar:
                                </p>
                                <a href="%s" class="action-button">Restablecer contraseña</a>
                                <p style="margin-top: 25px; font-size: 14px; color: #888888;">
                                    Si no solicitaste esto, podés ignorar este correo sin ningún problema.
                                </p>
                            </div>
                        </body>
                    </html>
                """
                .formatted(userEmail, resetLink);
    }
}