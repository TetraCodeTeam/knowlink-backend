package com.knowlink.api.tutors.controllers.implementations;

import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.exceptions.custom_exceptions.UnauthorizedException;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.controllers.interfaces.ITutorController;
import com.knowlink.api.tutors.controllers.requests.UpdateMinNoticeMinutesRequest;
import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSelfProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSubjectResponse;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.models.TutorSearchFilters;
import com.knowlink.api.tutors.data.models.TutorSearchResponse;
import com.knowlink.api.tutors.services.interfaces.ITutorProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.util.List;
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

    @Override
    public List<TutorSearchResponse> searchTutor(String query, Modality modality, CompensationType compensation,
            DayOfWeek dayOfWeek, Boolean verifiedOnly, Double minRating,
            UserPrincipal principal) {
        UUID studentId = principal != null ? principal.getUser().getUserId() : null;
        TutorSearchFilters filters = new TutorSearchFilters(
                modality, compensation, dayOfWeek, verifiedOnly, minRating);
        return this.tutorProfileService.searchTutor(query, studentId, filters);
    }

    @Override
    public TutorSubjectResponse createTutorSubject(TutorSubjectRequest request, UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("No se pudo identificar al tutor autenticado");
        }
 
        UUID tutorUserId = principal.getUser().getUserId();
        return this.tutorProfileService.createTutorSubject(tutorUserId, request);
    }
}