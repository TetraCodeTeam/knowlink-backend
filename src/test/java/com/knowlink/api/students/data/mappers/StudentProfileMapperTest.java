package com.knowlink.api.students.data.mappers;

import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.students.controllers.responses.StudentSelfProfileResponse;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StudentProfileMapperTest {

    private final StudentProfileMapper mapper = new StudentProfileMapper();

    @Test
    @DisplayName("toSelfProfileResponse expone institutionId y careerId del perfil del alumno")
    void toSelfProfileResponseExposesInstitutionAndCareerIds() {
        UUID institutionId = UUID.randomUUID();
        UUID careerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Institution institution = Institution.builder()
                .institutionId(institutionId)
                .name("UTN")
                .build();
        Career career = Career.builder()
                .careerId(careerId)
                .name("Ingenieria en Sistemas")
                .institution(institution)
                .build();
        User user = User.builder()
                .userId(userId)
                .fullName("Juan Perez")
                .email("juan@test.com")
                .role(Role.STUDENT)
                .build();
        StudentProfile profile = StudentProfile.builder()
                .user(user)
                .career(career)
                .profilePictureUrl("https://example.com/pic.png")
                .build();

        StudentSelfProfileResponse response = mapper.toSelfProfileResponse(user, profile, true);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.institutionId()).isEqualTo(institutionId);
        assertThat(response.careerId()).isEqualTo(careerId);
        assertThat(response.career()).isEqualTo("Ingenieria en Sistemas");
        assertThat(response.role()).isEqualTo(Role.STUDENT);
        assertThat(response.hasTutorProfile()).isTrue();
    }
}