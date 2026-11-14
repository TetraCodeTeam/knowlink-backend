package com.knowlink.api.tutors.validations;

import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Regla de la carrera del tutor: no puede cambiarla mientras tenga materias registradas, porque
 * las reservas, los materiales y las calificaciones cuelgan de esas materias.
 */
class TutorProfileValidationServiceImplTest {

    private TutorProfileValidationServiceImpl validationService;

    private Career currentCareer;
    private Career otherCareer;

    @BeforeEach
    void setUp() {
        validationService = new TutorProfileValidationServiceImpl(
                mock(ITutorProfileRepository.class),
                mock(ITutorSubjectRepository.class),
                mock(IBookingRepository.class));
        currentCareer = Career.builder().careerId(UUID.randomUUID()).name("Ingeniería en Sistemas").build();
        otherCareer = Career.builder().careerId(UUID.randomUUID()).name("Arquitectura").build();
    }

    private TutorProfile tutorWithSubjects(int subjectCount) {
        List<TutorSubject> subjects = new ArrayList<>();
        for (int i = 0; i < subjectCount; i++) {
            subjects.add(new TutorSubject());
        }
        return TutorProfile.builder().career(currentCareer).subjects(subjects).build();
    }

    @Test
    @DisplayName("cambiar de carrera con materias registradas se rechaza")
    void careerChangeWithSubjects_isRejected() {
        TutorProfile tutor = tutorWithSubjects(1);

        assertThatThrownBy(() -> validationService.ifCareerChangeBlockedBySubjectsThrowException(tutor, otherCareer))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No podés cambiar tu carrera mientras tengas materias registradas");
    }

    @Test
    @DisplayName("el mensaje no manda a eliminar materias, porque no existe forma de hacerlo")
    void rejectionMessage_doesNotSuggestDeletingSubjects() {
        TutorProfile tutor = tutorWithSubjects(2);

        assertThatThrownBy(() -> validationService.ifCareerChangeBlockedBySubjectsThrowException(tutor, otherCareer))
                .hasMessageContaining("tus materias dependen de ella")
                .hasMessageNotContaining("Eliminá");
    }

    @Test
    @DisplayName("sin materias registradas puede cambiar de carrera")
    void careerChangeWithoutSubjects_isAllowed() {
        TutorProfile tutor = tutorWithSubjects(0);

        assertThatCode(() -> validationService.ifCareerChangeBlockedBySubjectsThrowException(tutor, otherCareer))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("con materias registradas puede guardar su perfil si la carrera no cambia")
    void sameCareerWithSubjects_isAllowed() {
        TutorProfile tutor = tutorWithSubjects(3);

        assertThatCode(() -> validationService.ifCareerChangeBlockedBySubjectsThrowException(tutor, currentCareer))
                .doesNotThrowAnyException();
    }
}
