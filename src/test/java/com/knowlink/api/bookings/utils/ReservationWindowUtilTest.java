package com.knowlink.api.bookings.utils;

import com.knowlink.api.shared.utils.EndOfDay;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationWindowUtilTest {

    @Test
    void generatesOneHourWindowsEveryThirtyMinutes() {
        List<LocalTime[]> windows = ReservationWindowUtil.getWindows(LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertThat(windows).extracting(w -> w[0] + "-" + w[1])
                .containsExactly("09:00-10:00", "09:30-10:30", "10:00-11:00");
    }

    @Test
    void blockEndingAtMidnightProducesWindowsUpToEndOfDayWithoutWrappingAround() {
        List<LocalTime[]> windows = ReservationWindowUtil.getWindows(LocalTime.of(22, 0), EndOfDay.TIME);

        assertThat(windows).hasSize(3);
        assertThat(windows.get(0)[0]).isEqualTo(LocalTime.of(22, 0));
        assertThat(windows.get(1)[0]).isEqualTo(LocalTime.of(22, 30));
        assertThat(windows.get(2)[0]).isEqualTo(LocalTime.of(23, 0));
        assertThat(windows.get(2)[1]).isEqualTo(EndOfDay.TIME);
        // Ninguna ventana empieza después de las 23:00 ni "da la vuelta" pasada la medianoche.
        assertThat(windows).allSatisfy(w -> assertThat(w[0]).isBeforeOrEqualTo(LocalTime.of(23, 0)));
    }

    @Test
    void blockShorterThanOneHourHasNoWindows() {
        assertThat(ReservationWindowUtil.getWindows(LocalTime.of(23, 30), EndOfDay.TIME)).isEmpty();
    }

    @Test
    void invalidBlocksHaveNoWindows() {
        assertThat(ReservationWindowUtil.getWindows(null, LocalTime.of(10, 0))).isEmpty();
        assertThat(ReservationWindowUtil.getWindows(LocalTime.of(10, 0), LocalTime.of(10, 0))).isEmpty();
        assertThat(ReservationWindowUtil.getWindows(EndOfDay.TIME, LocalTime.of(10, 0))).isEmpty();
    }
}
