package com.knowlink.api.admin.dashboard;

import com.knowlink.api.admin.dashboard.controllers.responses.VolumeSplit;
import com.knowlink.api.admin.dashboard.data.enums.UnresolvedLevel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardMathTest {

    @Test
    @DisplayName("Variación porcentual: sin base de comparación devuelve null")
    void percentageDelta_withoutBase_returnsNull() {
        assertThat(DashboardMath.percentageDelta(5, 0)).isNull();
        assertThat(DashboardMath.percentageDelta(0, 0)).isNull();
    }

    @Test
    @DisplayName("Variación porcentual: redondea a un decimal y conserva el signo")
    void percentageDelta_roundsToOneDecimal() {
        assertThat(DashboardMath.percentageDelta(3, 2)).isEqualTo(50.0);
        assertThat(DashboardMath.percentageDelta(2, 3)).isEqualTo(-33.3);
        assertThat(DashboardMath.percentageDelta(4, 4)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TTR: positivo cuando el período actual fue más rápido")
    void ttrDelta_positiveWhenCurrentIsFaster() {
        assertThat(DashboardMath.ttrDeltaPct(10.0, 8.0)).isEqualTo(20.0);
    }

    @Test
    @DisplayName("TTR: negativo cuando el período actual fue más lento")
    void ttrDelta_negativeWhenCurrentIsSlower() {
        assertThat(DashboardMath.ttrDeltaPct(10.0, 12.0)).isEqualTo(-20.0);
    }

    @Test
    @DisplayName("TTR: null cuando falta alguno de los promedios")
    void ttrDelta_withoutBase_returnsNull() {
        assertThat(DashboardMath.ttrDeltaPct(null, 8.0)).isNull();
        assertThat(DashboardMath.ttrDeltaPct(10.0, null)).isNull();
        assertThat(DashboardMath.ttrDeltaPct(0.0, 8.0)).isNull();
    }

    @Test
    @DisplayName("Nivel de tareas sin resolver: bordes 14.9 / 15 / 29.9 / 30 / 59.9 / 60 / 100")
    void unresolvedLevel_boundaries() {
        assertThat(DashboardMath.unresolvedLevel(0.0)).isEqualTo(UnresolvedLevel.UNDER_CONTROL);
        assertThat(DashboardMath.unresolvedLevel(14.9)).isEqualTo(UnresolvedLevel.UNDER_CONTROL);
        assertThat(DashboardMath.unresolvedLevel(15.0)).isEqualTo(UnresolvedLevel.ACCEPTABLE);
        assertThat(DashboardMath.unresolvedLevel(29.9)).isEqualTo(UnresolvedLevel.ACCEPTABLE);
        assertThat(DashboardMath.unresolvedLevel(30.0)).isEqualTo(UnresolvedLevel.RISKY);
        assertThat(DashboardMath.unresolvedLevel(59.9)).isEqualTo(UnresolvedLevel.RISKY);
        assertThat(DashboardMath.unresolvedLevel(60.0)).isEqualTo(UnresolvedLevel.CRITICAL);
        assertThat(DashboardMath.unresolvedLevel(100.0)).isEqualTo(UnresolvedLevel.CRITICAL);
    }

    @Test
    @DisplayName("Distribución del volumen: 6 y 4 casos dan 60 / 40")
    void volumeSplit_sixAndFour() {
        assertThat(DashboardMath.volumeSplit(6, 4)).isEqualTo(new VolumeSplit(60, 40));
    }

    @Test
    @DisplayName("Distribución del volumen: 1 y 1 dan 50 / 50")
    void volumeSplit_oneAndOne() {
        assertThat(DashboardMath.volumeSplit(1, 1)).isEqualTo(new VolumeSplit(50, 50));
    }

    @Test
    @DisplayName("Distribución del volumen: 1 y 2 dan 33 / 67 por mayor resto")
    void volumeSplit_oneAndTwo() {
        assertThat(DashboardMath.volumeSplit(1, 2)).isEqualTo(new VolumeSplit(33, 67));
        assertThat(DashboardMath.volumeSplit(2, 1)).isEqualTo(new VolumeSplit(67, 33));
    }

    @Test
    @DisplayName("Distribución del volumen: sin casos nuevos devuelve 0 / 0")
    void volumeSplit_withoutVolume() {
        assertThat(DashboardMath.volumeSplit(0, 0)).isEqualTo(new VolumeSplit(0, 0));
    }

    @Test
    @DisplayName("Distribución del volumen: siempre suma 100 cuando hay volumen")
    void volumeSplit_alwaysAddsUpToOneHundred() {
        long[][] cases = { { 1, 7 }, { 7, 1 }, { 3, 7 }, { 2, 5 }, { 13, 17 }, { 1, 999 } };
        for (long[] pair : cases) {
            VolumeSplit split = DashboardMath.volumeSplit(pair[0], pair[1]);
            assertThat(split.claimsPct() + split.reportsPct())
                    .as("split for %s/%s", pair[0], pair[1])
                    .isEqualTo(100);
        }
    }
}
