package com.knowlink.api.tutors.availability;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.shared.utils.EndOfDay;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
import com.knowlink.api.tutors.availability.utils.AvailabilityBlockMerger;
import com.knowlink.api.tutors.availability.validations.AvailabilityBlockValidationServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilityBlockEndOfDayTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 28);

    private final AvailabilityBlockValidationServiceImpl validation =
            new AvailabilityBlockValidationServiceImpl(Mockito.mock(IBookingRepository.class));

    @Test
    void aBlockEndingAtMidnightIsStoredAsEndOfDay() {
        AvailabilityBlockRequest block = new AvailabilityBlockRequest(
                DATE, LocalTime.of(22, 0), LocalTime.MIDNIGHT, false);

        assertThat(block.endTime()).isEqualTo(EndOfDay.TIME);
    }

    @Test
    void aBlockEndingAtMidnightPassesValidation() {
        AvailabilityBlockRequest block = new AvailabilityBlockRequest(
                DATE, LocalTime.of(22, 0), LocalTime.MIDNIGHT, false);

        assertThatCode(() -> validation.validateBlocks(List.of(block))).doesNotThrowAnyException();
    }

    @Test
    void aBlockEndingBeforeItStartsIsStillRejected() {
        AvailabilityBlockRequest block = new AvailabilityBlockRequest(
                DATE, LocalTime.of(14, 0), LocalTime.of(13, 0), false);

        assertThatThrownBy(() -> validation.validateBlocks(List.of(block)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void midnightToMidnightBecomesAFullDayBlock() {
        // Un fin a las 00:00 siempre es medianoche del día siguiente, así que 00:00-00:00 cubre las 24 h.
        AvailabilityBlockRequest block = new AvailabilityBlockRequest(
                DATE, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, false);

        assertThatCode(() -> validation.validateBlocks(List.of(block))).doesNotThrowAnyException();
        assertThat(block.endTime()).isEqualTo(EndOfDay.TIME);
    }

    @Test
    void midnightBlockAfterAnotherDoesNotOverlapAndContiguousOnesMerge() {
        AvailabilityBlockRequest first = new AvailabilityBlockRequest(
                DATE, LocalTime.of(20, 0), LocalTime.of(22, 0), false);
        AvailabilityBlockRequest last = new AvailabilityBlockRequest(
                DATE, LocalTime.of(22, 0), LocalTime.MIDNIGHT, false);

        assertThatCode(() -> validation.validateBlocks(List.of(first, last))).doesNotThrowAnyException();
        List<AvailabilityBlockRequest> merged = AvailabilityBlockMerger.mergeContiguous(List.of(first, last));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).startTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(merged.get(0).endTime()).isEqualTo(EndOfDay.TIME);
    }

    @Test
    void aBlockAfterOneEndingAtMidnightOverlaps() {
        AvailabilityBlockRequest toMidnight = new AvailabilityBlockRequest(
                DATE, LocalTime.of(22, 0), LocalTime.MIDNIGHT, false);
        AvailabilityBlockRequest inside = new AvailabilityBlockRequest(
                DATE, LocalTime.of(23, 0), LocalTime.of(23, 30), false);

        assertThatThrownBy(() -> validation.validateBlocks(List.of(toMidnight, inside)))
                .isInstanceOf(ValidationException.class);
    }
}
