package com.knowlink.api.community.controllers.implementations;

import com.knowlink.api.community.controllers.interfaces.ICommunityRankingController;
import com.knowlink.api.community.controllers.responses.MyPodiumPositionResponse;
import com.knowlink.api.community.controllers.responses.TutorPodiumResponse;
import com.knowlink.api.community.services.interfaces.ITutorRankingService;
import com.knowlink.api.security.models.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CommunityRankingControllerImpl implements ICommunityRankingController {

    private final ITutorRankingService rankingService;

    @Override
    public TutorPodiumResponse getPodium() {
        return rankingService.getPodium();
    }

    @Override
    public MyPodiumPositionResponse getMyPosition(UserPrincipal principal) {
        return rankingService.getMyPosition(principal.getUser().getUserId());
    }
}
