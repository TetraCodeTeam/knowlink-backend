package com.knowlink.api.tutors.availability;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.shared.utils.EndOfDay;
import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.availability.validations.AvailabilityBlockValidationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regla de los bloques con reservas: deben volver en la solicitud sin cambios y se excluyen de lo que se
 * regraba; todo lo demás de la semana se puede reemplazar.
 */
class AvailabilityBlockValidationServiceImplTest {

    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 17);
    private static final LocalDate WEDNESDAY = TUESDAY.plusDays(1);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 17, 10);

    private final AvailabilityBlockValidationServiceImpl validation = new AvailabilityBlockValidationServiceImpl();

    @Test
    @DisplayName("Un bloque bloqueado que vuelve sin cambios se excluye y el resto queda editable")
    void unchangedLockedBlockIsExcludedAndTheRestStaysEditable() {
        AvailabilityBlock locked = block(TUESDAY, 16, 20);
        AvailabilityBlockRequest newBlock = request(WEDNESDAY, 10, 14, false);

        List<AvailabilityBlockRequest> editable = validation.excludeLockedBlocks(
                List.of(locked), List.of(request(TUESDAY, 16, 20, false), newBlock), NOW);

        assertThat(editable).containsExactly(newBlock);
    }

    @Test
    @DisplayName("Sin bloques bloqueados todo lo solicitado es editable")
    void withoutLockedBlocksEverythingIsEditable() {
        List<AvailabilityBlockRequest> requested =
                List.of(request(TUESDAY, 10, 14, false), request(WEDNESDAY, 16, 20, true));

        assertThat(validation.excludeLockedBlocks(List.of(), requested, NOW)).containsExactlyElementsOf(requested);
    }

    @Test
    @DisplayName("Quitar un bloque bloqueado se rechaza e indica cuál es")
    void removingALockedBlockIsRejectedNamingIt() {
        AvailabilityBlock locked = block(TUESDAY, 16, 20);

        assertThatThrownBy(() -> validation.excludeLockedBlocks(
                List.of(locked), List.of(request(WEDNESDAY, 10, 14, false)), NOW))
                .isInstanceOf(ValidationException.class)
                .hasMessage("No es posible modificar ni quitar el bloque del 17/11 de 16:00 a 20:00: "
                        + "tiene reservas asociadas.");
    }

    @Test
    @DisplayName("Acortar, extender o mover de día un bloque bloqueado cuenta como modificarlo")
    void changingALockedBlockIsRejected() {
        AvailabilityBlock locked = block(TUESDAY, 16, 20);

        assertThatThrownBy(() -> validation.excludeLockedBlocks(
                List.of(locked), List.of(request(TUESDAY, 16, 19, false)), NOW))
                .as("acortado").isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validation.excludeLockedBlocks(
                List.of(locked), List.of(request(TUESDAY, 16, 22, false)), NOW))
                .as("extendido").isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validation.excludeLockedBlocks(
                List.of(locked), List.of(request(TUESDAY, 15, 20, false)), NOW))
                .as("inicio movido").isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validation.excludeLockedBlocks(
                List.of(locked), List.of(request(WEDNESDAY, 16, 20, false)), NOW))
                .as("cambiado de día").isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Un bloque contiguo al bloqueado se puede agregar: no se confunde con él")
    void aContiguousNewBlockIsStillEditable() {
        AvailabilityBlockRequest contiguous = request(TUESDAY, 20, 22, false);

        List<AvailabilityBlockRequest> editable = validation.excludeLockedBlocks(
                List.of(block(TUESDAY, 16, 20)), List.of(request(TUESDAY, 16, 20, false), contiguous), NOW);

        assertThat(editable).containsExactly(contiguous);
    }

    @Test
    @DisplayName("Un bloque de hoy que ya empezó vuelve con el inicio recortado, y se reconoce igual")
    void todaysStartedBlockIsMatchedByItsDisplayedStart() {
        // Son las 17:10: el editor muestra el bloque desde las 17:30 (próxima media hora).
        LocalDate today = NOW.toLocalDate();
        AvailabilityBlock locked = block(today, LocalTime.of(14, 0), LocalTime.of(20, 0));

        assertThatCode(() -> validation.excludeLockedBlocks(
                List.of(locked), List.of(new AvailabilityBlockRequest(today, LocalTime.of(17, 30), LocalTime.of(20, 0), false)), NOW))
                .as("con el inicio recortado que recibe el editor").doesNotThrowAnyException();
        assertThatCode(() -> validation.excludeLockedBlocks(
                List.of(locked), List.of(new AvailabilityBlockRequest(today, LocalTime.of(14, 0), LocalTime.of(20, 0), false)), NOW))
                .as("también con el inicio guardado").doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El mensaje de un bloque de hoy usa el inicio que ve el tutor")
    void todaysBlockMessageUsesTheDisplayedStart() {
        LocalDate today = NOW.toLocalDate();
        AvailabilityBlock locked = block(today, LocalTime.of(14, 0), LocalTime.of(20, 0));

        assertThatThrownBy(() -> validation.excludeLockedBlocks(List.of(locked), List.of(), NOW))
                .hasMessage("No es posible modificar ni quitar el bloque del 14/11 de 17:30 a 20:00: "
                        + "tiene reservas asociadas.");
    }

    @Test
    @DisplayName("Un bloque que termina a medianoche se reconoce y se muestra como 24:00")
    void endOfDayBlockIsMatchedAndShownAsMidnight() {
        AvailabilityBlock locked = block(TUESDAY, LocalTime.of(22, 0), EndOfDay.TIME);
        // 00:00 llega normalizado a fin de día en el propio request.
        AvailabilityBlockRequest sameBlock =
                new AvailabilityBlockRequest(TUESDAY, LocalTime.of(22, 0), LocalTime.MIDNIGHT, false);

        assertThat(validation.excludeLockedBlocks(List.of(locked), List.of(sameBlock), NOW)).isEmpty();
        assertThatThrownBy(() -> validation.excludeLockedBlocks(List.of(locked), List.of(), NOW))
                .hasMessage("No es posible modificar ni quitar el bloque del 17/11 de 22:00 a 24:00: "
                        + "tiene reservas asociadas.");
    }

    @Test
    @DisplayName("La configuración de repetición no cuenta: el editor la unifica para toda la semana")
    void repeatWeeklyDifferenceIsIgnored() {
        AvailabilityBlock locked = block(TUESDAY, 16, 20); // se guardó sin repetir

        assertThat(validation.excludeLockedBlocks(
                List.of(locked), List.of(request(TUESDAY, 16, 20, true)), NOW)).isEmpty();
    }

    @Test
    @DisplayName("Una diferencia de microsegundos no hace distinto al bloque")
    void subSecondDifferencesAreIgnored() {
        AvailabilityBlock locked = block(TUESDAY, LocalTime.of(16, 0), LocalTime.of(20, 0, 0, 500_000));

        assertThat(validation.excludeLockedBlocks(
                List.of(locked), List.of(request(TUESDAY, 16, 20, false)), NOW)).isEmpty();
    }

    @Test
    @DisplayName("Con varios bloques bloqueados, cada uno debe volver")
    void everyLockedBlockMustComeBack() {
        AvailabilityBlock first = block(TUESDAY, 10, 14);
        AvailabilityBlock second = block(WEDNESDAY, 16, 20);

        assertThat(validation.excludeLockedBlocks(
                List.of(first, second),
                List.of(request(TUESDAY, 10, 14, false), request(WEDNESDAY, 16, 20, false)), NOW)).isEmpty();

        assertThatThrownBy(() -> validation.excludeLockedBlocks(
                List.of(first, second), List.of(request(TUESDAY, 10, 14, false)), NOW))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("18/11 de 16:00 a 20:00");
    }

    private static AvailabilityBlock block(LocalDate date, int startHour, int endHour) {
        return block(date, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private static AvailabilityBlock block(LocalDate date, LocalTime start, LocalTime end) {
        return AvailabilityBlock.builder()
                .availabilityBlockId(UUID.randomUUID())
                .date(date)
                .startTime(start)
                .endTime(end)
                .build();
    }

    private static AvailabilityBlockRequest request(LocalDate date, int startHour, int endHour, boolean repeat) {
        return new AvailabilityBlockRequest(date, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), repeat);
    }
}
