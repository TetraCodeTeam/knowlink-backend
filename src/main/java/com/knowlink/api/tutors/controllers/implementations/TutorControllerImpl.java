package com.knowlink.api.tutors.controllers.implementations;

import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.controllers.interfaces.ITutorController;
import com.knowlink.api.tutors.controllers.requests.UpdateMinNoticeMinutesRequest;
import com.knowlink.api.tutors.controllers.responses.ActivateStudentRoleResponse;
import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSelfProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSubjectResponse;
import com.knowlink.api.tutors.data.models.TutorSearchResponse;
import com.knowlink.api.tutors.services.interfaces.ITutorProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TutorControllerImpl implements ITutorController {

    private final ITutorProfileService tutorProfileService;

    @Override
    public TutorProfileResponse getTutorProfile(UUID userId, UserPrincipal principal) {
        return this.tutorProfileService.getTutorProfile(userId, principal.getUser().getUserId());
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

    @Override
    public List<TutorSearchResponse> searchTutor(String query, UserPrincipal principal) {
        return this.tutorProfileService.searchTutor(query, principal.getUser().getUserId());
    }

    @Override
    public TutorSubjectResponse createTutorSubject(TutorSubjectRequest request, UserPrincipal principal) {
        return this.tutorProfileService.createTutorSubject(principal.getUser().getUserId(), request);
    }

    @Override
    public ActivateStudentRoleResponse activateStudentRole(UserPrincipal principal) {
        return tutorProfileService.activateStudentRole(principal.getUser().getUserId());
    }
}