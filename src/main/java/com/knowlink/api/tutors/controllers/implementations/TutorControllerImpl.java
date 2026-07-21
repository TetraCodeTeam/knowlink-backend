package com.knowlink.api.tutors.controllers.implementations;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.controllers.interfaces.ITutorController;
import com.knowlink.api.tutors.controllers.requests.UpdateMinNoticeMinutesRequest;
import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSelfProfileResponse;
import com.knowlink.api.tutors.services.interfaces.ITutorProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TutorControllerImpl implements ITutorController {

    private final ITutorProfileService tutorProfileService;

    @Override
    public TutorProfileResponse getTutorProfile(UUID userId, UserPrincipal principal) {
        UUID studentId = principal != null ? principal.getUser().getUserId() : null;
        return this.tutorProfileService.getTutorProfile(userId, studentId);
    }

    @Override
    public TutorSelfProfileResponse getMyProfile(UserPrincipal principal) {
        return this.tutorProfileService.getSelfProfile(principal.getUser().getUserId());
    }

    @Override
    public void updateMinNoticeMinutes(UserPrincipal principal, UpdateMinNoticeMinutesRequest request) {
        tutorProfileService.updateMinNoticeMinutes(principal.getUser().getUserId(), request.minNoticeMinutes());
    }

    @Override
    public UpdateMinNoticeMinutesRequest getMinNoticeMinutes(UserPrincipal principal) {
        Integer minutes = tutorProfileService.getMinNoticeMinutes(principal.getUser().getUserId());
        return new UpdateMinNoticeMinutesRequest(minutes);
    }
}