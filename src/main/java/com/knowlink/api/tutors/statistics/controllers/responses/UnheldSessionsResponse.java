package com.knowlink.api.tutors.statistics.controllers.responses;

/**
 * {@code cancelledByStudentCredited}: cancelaciones del alumno con poco aviso, cuyo monto se acredita
 * al tutor (subconjunto de {@code cancelledByStudent}). {@code notHeld}: sesiones no cumplidas por el
 * tutor o no realizadas.
 */
public record UnheldSessionsResponse(
        int cancelledByTutor,
        int cancelledByStudent,
        int cancelledByStudentCredited,
        int notHeld) {
}
