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
                        <h2 style="color: #4361ee; margin-bottom: 20px;">Welcome to Knowlink!</h2>
                        <p style="font-size: 16px; color: #ffffff;"><strong>Hi %s!</strong></p>
                        <p style="font-size: 16px; color: #cccccc;">
                            Thanks for signing up. To complete your registration, please confirm your email address by clicking the button below:
                        </p>
                        <a href="%s" class="action-button">Confirm account</a>
                        <p style="margin-top: 25px; font-size: 14px; color: #888888;">
                            If you didn't create this account, you can safely ignore this email.
                        </p>
                    </div>
                </body>
            </html>
        """.formatted(user.getEmail(), confirmationLink);
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
                        <h2 style="color: #4361ee; margin-bottom: 20px;">New confirmation link</h2>
                        <p style="font-size: 16px; color: #ffffff;"><strong>Hi %s!</strong></p>
                        <p style="font-size: 16px; color: #cccccc;">
                            You requested a new confirmation link for your account.
                            Please confirm your email address by clicking the button below:
                        </p>
                        <a href="%s" class="action-button">Confirm account</a>
                        <p style="margin-top: 25px; font-size: 14px; color: #888888;">
                            If you didn't create this account, you can safely ignore this email.
                        </p>
                    </div>
                </body>
            </html>
        """.formatted(user.getEmail(), confirmationLink);
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
                        <h2 style="color: #4361ee; margin-bottom: 20px;">Reset your password</h2>
                        <p style="font-size: 16px; color: #ffffff;"><strong>Hi %s!</strong></p>
                        <p style="font-size: 16px; color: #cccccc;">
                            We received a request to reset your Knowlink account password.
                            Click the button below to continue:
                        </p>
                        <a href="%s" class="action-button">Reset password</a>
                        <p style="margin-top: 25px; font-size: 14px; color: #888888;">
                            If you didn't request this, you can safely ignore this email.
                        </p>
                    </div>
                </body>
            </html>
        """.formatted(userEmail, resetLink);
    }
}