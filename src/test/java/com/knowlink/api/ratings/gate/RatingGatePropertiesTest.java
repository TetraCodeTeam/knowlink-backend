package com.knowlink.api.ratings.gate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RatingGatePropertiesTest {

    @Test
    @DisplayName("enforced-from vacío desactiva el bloqueo")
    void blankEnforcedFromIsDisabled() {
        RatingGateProperties properties = new RatingGateProperties("", 15);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.enforcedFromAsDateTime()).isEmpty();
    }

    @Test
    @DisplayName("enforced-from inválido desactiva el bloqueo sin lanzar excepción")
    void invalidEnforcedFromIsDisabled() {
        RatingGateProperties properties = new RatingGateProperties("not-a-date", 15);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.enforcedFromAsDateTime()).isEmpty();
    }

    @Test
    @DisplayName("enforced-from ISO local se parsea como LocalDateTime")
    void validEnforcedFromIsParsed() {
        RatingGateProperties properties = new RatingGateProperties("2026-11-10T00:00:00", 15);

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.enforcedFromAsDateTime())
                .contains(java.time.LocalDateTime.of(2026, 11, 10, 0, 0));
    }
}
