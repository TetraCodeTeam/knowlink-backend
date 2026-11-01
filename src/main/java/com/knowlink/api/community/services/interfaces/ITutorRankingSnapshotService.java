package com.knowlink.api.community.services.interfaces;

import java.time.LocalDate;

public interface ITutorRankingSnapshotService {

    /**
     * Calcula y persiste el ranking del período si todavía no existe. Nunca recalcula un período ya generado:
     * el ranking no se modifica entre actualizaciones mensuales.
     *
     * @param periodStart primer día del mes del ranking
     */
    void generateIfAbsent(LocalDate periodStart);
}
