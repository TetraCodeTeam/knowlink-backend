package com.knowlink.api.community.services.interfaces;

import com.knowlink.api.community.controllers.responses.MyPodiumPositionResponse;
import com.knowlink.api.community.controllers.responses.TutorPodiumResponse;

import java.util.UUID;

public interface ITutorRankingService {

    /**
     * Ranking del mes en curso: podio con los 3 primeros puestos y el resto del top 10. Si el ranking del mes
     * todavía no se generó (por ejemplo, el job del primer día no corrió), se genera en ese momento.
     */
    TutorPodiumResponse getPodium();

    /**
     * Posición del usuario autenticado en el ranking vigente, aunque quede fuera del top 10.
     */
    MyPodiumPositionResponse getMyPosition(UUID userId);
}
