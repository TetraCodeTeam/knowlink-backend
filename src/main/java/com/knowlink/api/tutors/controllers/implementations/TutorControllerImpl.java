package com.knowlink.api.tutors.controllers.implementations;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.controllers.interfaces.ITutorController;
import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.services.interfaces.ITutorProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TutorControllerImpl implements ITutorController {

    private final ITutorProfileService tutorProfileService;

    @Override
    public TutorProfileResponse getTutorProfile(UUID userId, Authentication authentication) {
        UUID studentId = null;
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            studentId = principal.getUser().getUserId();
        }

        return this.tutorProfileService.getTutorProfile(userId, studentId);
    }
}