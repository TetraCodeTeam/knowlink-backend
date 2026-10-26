package com.knowlink.api.payments.domain;

/**
 * Business concepts for a funds movement. The label is persisted in
 * funds_transfer.concept for historical readability, but consumers must
 * switch on the enum constant, never on the label text.
 */
public enum FundsConcept {

    HELD("Fondos retenidos para reserva"),
    RELEASED_SESSION_COMPLETED("Liberacion por sesion completada"),
    RELEASED_STUDENT_ABSENT("Liberacion por alumno ausente"),
    REFUNDED_TUTOR_ABSENT("Devolucion por tutor ausente"),
    REFUNDED_SESSION_NOT_HELD("Devolucion por sesion no realizada");

    private final String label;

    FundsConcept(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}