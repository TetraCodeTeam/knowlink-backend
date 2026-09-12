package com.knowlink.api.email;

public interface EmailService {

    void enviarCorreo(String destinatario, String asunto, String cuerpoHtml);
}
