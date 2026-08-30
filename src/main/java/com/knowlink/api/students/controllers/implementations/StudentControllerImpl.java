package com.knowlink.api.students.controllers.implementations;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.students.controllers.interfaces.IStudentController;
import com.knowlink.api.students.controllers.requests.ActivateTutorRoleRequest;
import com.knowlink.api.students.controllers.responses.ActivateTutorRoleResponse;
import com.knowlink.api.students.controllers.responses.StudentSelfProfileResponse;
import com.knowlink.api.students.services.interfaces.IStudentProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StudentControllerImpl implements IStudentController {

    private final IStudentProfileService studentProfileService;

    @Override
    public StudentSelfProfileResponse getMyProfile(UserPrincipal principal) {
        return studentProfileService.getSelfProfile(principal.getUser().getUserId());
    }

    @Override
    public ActivateTutorRoleResponse activateTutorRole(ActivateTutorRoleRequest request, UserPrincipal principal) {
        return studentProfileService.activateTutorRole(principal.getUser().getUserId(), request);
    }
}
