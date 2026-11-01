package com.knowlink.api.tutors.data.specifications;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TimeFrame;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TutorSearchSpecificationsTest {

    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ITutorProfileRepository tutorProfileRepository;

    @BeforeEach
    void seedData() {
        Career career = careerRepository.save(Career.builder().name("Ingeniería en Sistemas").build());

        Subject algebra = subjectRepository.save(Subject.builder().name("Algebra").isBasic(true).career(career).build());
        Subject fisica = subjectRepository.save(Subject.builder().name("Fisica").isBasic(true).career(career).build());
        Subject quimica = subjectRepository.save(Subject.builder().name("Quimica").isBasic(true).career(career).build());
        Subject biologia = subjectRepository.save(Subject.builder().name("Biologia").isBasic(true).career(career).build());

        TutorProfile ana = tutor(career, "Ana Test", 4.5);
        tutorSubject(ana, algebra, Modality.VIRTUAL, CompensationType.PAID, TutorSubjectStatus.ACTIVE);

        TutorProfile marta = tutor(career, "Marta Test", 4.8);
        tutorSubject(marta, algebra, Modality.VIRTUAL, CompensationType.FREE, TutorSubjectStatus.ACTIVE);

        TutorProfile carlos = tutor(career, "Carlos Test", 3.9);
        tutorSubject(carlos, quimica, Modality.IN_PERSON, CompensationType.PAID, TutorSubjectStatus.PENDING);

        TutorProfile luis = tutor(career, "Luis Test", 4.2);
        tutorSubject(luis, fisica, Modality.BOTH, CompensationType.PAID, TutorSubjectStatus.ACTIVE);

        TutorProfile nora = tutor(career, "Nora Test", 4.0);
        tutorSubject(nora, biologia, Modality.VIRTUAL, CompensationType.PAID, TutorSubjectStatus.REJECTED);

        tutorSubjectRepository.flush();
    }

    private TutorProfile tutor(Career career, String fullName, double rating) {
        User user = userRepository.save(User.builder()
                .fullName(fullName)
                .email(fullName.replace(" ", ".").toLowerCase() + "@test.com")
                .password("hashed")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        return tutorProfileRepository.save(TutorProfile.builder()
                .user(user)
                .career(career)
                .averageRating(rating)
                .build());
    }

    private TutorSubject tutorSubject(TutorProfile profile, Subject subject, Modality modality,
            CompensationType compensation, TutorSubjectStatus status) {
        TutorSubject ts = TutorSubject.builder()
                .tutorProfile(profile)
                .subject(subject)
                .modality(modality)
                .compensationType(compensation)
                .tutorSubjectStatus(status)
                .build();
        profile.getSubjects().add(ts);
        return tutorSubjectRepository.save(ts);
    }

    private List<String> tutorNames(Specification<TutorSubject> spec) {
        return tutorSubjectRepository.findAll(spec).stream()
                .map(ts -> ts.getTutorProfile().getUser().getFullName())
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("subjectNameContains matchea por nombre de materia, sin importar mayúsculas")
    void subjectNameContains_isCaseInsensitiveAndPartial() {
        assertThat(tutorNames(TutorSearchSpecifications.subjectNameContains("alge")))
                .containsExactlyInAnyOrder("Ana Test", "Marta Test");
    }

    @Test
    @DisplayName("modalityIn(VIRTUAL) incluye subjects BOTH, exclude los IN_PERSON")
    void modalityIn_virtual_includesBoth() {
        assertThat(tutorNames(TutorSearchSpecifications.modalityIn(Modality.VIRTUAL)))
                .containsExactlyInAnyOrder("Ana Test", "Marta Test", "Luis Test", "Nora Test");
    }

    @Test
    @DisplayName("modalityIn(IN_PERSON) incluye subjects BOTH")
    void modalityIn_inPerson_includesBoth() {
        assertThat(tutorNames(TutorSearchSpecifications.modalityIn(Modality.IN_PERSON)))
                .containsExactlyInAnyOrder("Carlos Test", "Luis Test");
    }

    @Test
    @DisplayName("modalityIn(BOTH) matchea solo subjects BOTH")
    void modalityIn_both_onlyBoth() {
        assertThat(tutorNames(TutorSearchSpecifications.modalityIn(Modality.BOTH)))
                .containsExactlyInAnyOrder("Luis Test");
    }

    @Test
    @DisplayName("compensationIn filtra por tipo de compensación")
    void compensationIn_filtersByType() {
        assertThat(tutorNames(TutorSearchSpecifications.compensationIn(CompensationType.PAID)))
                .containsExactlyInAnyOrder("Ana Test", "Carlos Test", "Luis Test", "Nora Test");
        assertThat(tutorNames(TutorSearchSpecifications.compensationIn(CompensationType.FREE)))
                .containsExactlyInAnyOrder("Marta Test");
    }

    @Test
    @DisplayName("subjectValidated devuelve solo materias con tutorSubjectStatus ACTIVE (PENDING y REJECTED excluidas)")
    void subjectValidated_returnsOnlyActiveSubjectStatuses() {
        assertThat(tutorNames(TutorSearchSpecifications.subjectValidated()))
                .containsExactlyInAnyOrder("Ana Test", "Marta Test", "Luis Test");
    }

    @Test
    @DisplayName("minAverageRating filtra por calificación mínima")
    void minAverageRating_filtersByMinimum() {
        assertThat(tutorNames(TutorSearchSpecifications.minAverageRating(4.6)))
                .containsExactlyInAnyOrder("Marta Test");
    }

    @Test
    @DisplayName("matchesAvailability por día y franja horaria (en memoria, portable MySQL/H2)")
    void matchesAvailability_byDayAndFrame() {
        AvailabilityBlock block = AvailabilityBlock.builder()
                .date(LocalDate.of(2026, 1, 5))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        assertThat(TutorSearchSpecifications.matchesAvailability(block,
                Set.of(DayOfWeek.MONDAY), TimeFrame.MORNING)).isTrue();
        assertThat(TutorSearchSpecifications.matchesAvailability(block,
                Set.of(DayOfWeek.TUESDAY), TimeFrame.MORNING)).isFalse();
        assertThat(TutorSearchSpecifications.matchesAvailability(block,
                Set.of(DayOfWeek.MONDAY), TimeFrame.AFTERNOON)).isFalse();
        assertThat(TutorSearchSpecifications.matchesAvailability(block,
                Set.of(DayOfWeek.WEDNESDAY), TimeFrame.EVENING)).isFalse();
    }

    @Test
    @DisplayName("matchesAvailability con varios días combina los días con OR")
    void matchesAvailability_multipleDays_areOred() {
        AvailabilityBlock mondayLate = AvailabilityBlock.builder()
                .date(LocalDate.of(2026, 1, 5))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(16, 0))
                .build();
        AvailabilityBlock tuesdayMorning = AvailabilityBlock.builder()
                .date(LocalDate.of(2026, 1, 6))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        assertThat(TutorSearchSpecifications.matchesAvailability(mondayLate,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), null)).isTrue();
        assertThat(TutorSearchSpecifications.matchesAvailability(tuesdayMorning,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), null)).isTrue();
        assertThat(TutorSearchSpecifications.matchesAvailability(tuesdayMorning,
                Set.of(DayOfWeek.WEDNESDAY), null)).isFalse();
        assertThat(TutorSearchSpecifications.matchesAvailability(tuesdayMorning,
                Set.of(), TimeFrame.MORNING)).isTrue();
    }

    @Test
    @DisplayName("matchesAvailability evalúa día y franja sobre el mismo bloque (caso negativo)")
    void matchesAvailability_dayAndFrame_mustMatchSameBlock() {
        AvailabilityBlock mondayAfternoon = AvailabilityBlock.builder()
                .date(LocalDate.of(2026, 1, 5))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(16, 0))
                .build();
        AvailabilityBlock tuesdayMorning = AvailabilityBlock.builder()
                .date(LocalDate.of(2026, 1, 6))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        // Día correcto en el bloque A y franja correcta en el bloque B: ninguno cumple ambos.
        assertThat(TutorSearchSpecifications.matchesAvailability(mondayAfternoon,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), TimeFrame.MORNING)).isFalse();
        assertThat(TutorSearchSpecifications.matchesAvailability(tuesdayMorning,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), TimeFrame.MORNING)).isTrue();
    }

    @Test
    @DisplayName("matchesAvailability con solo día o solo franja (filtros neutrales)")
    void matchesAvailability_partialCombination() {
        AvailabilityBlock morningMonday = AvailabilityBlock.builder()
                .date(LocalDate.of(2026, 1, 5))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .build();
        AvailabilityBlock eveningWednesday = AvailabilityBlock.builder()
                .date(LocalDate.of(2026, 1, 7))
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .build();

        assertThat(TutorSearchSpecifications.matchesAvailability(morningMonday,
                Set.of(DayOfWeek.MONDAY), null)).isTrue();
        assertThat(TutorSearchSpecifications.matchesAvailability(morningMonday,
                Set.of(), TimeFrame.MORNING)).isTrue();
        assertThat(TutorSearchSpecifications.matchesAvailability(morningMonday,
                Set.of(), TimeFrame.EVENING)).isFalse();
        assertThat(TutorSearchSpecifications.matchesAvailability(eveningWednesday,
                Set.of(DayOfWeek.WEDNESDAY), null)).isTrue();
        assertThat(TutorSearchSpecifications.matchesAvailability(eveningWednesday,
                Set.of(), TimeFrame.EVENING)).isTrue();
    }

    @Test
    @DisplayName("Combinación AND produce intersección, no unión")
    void combination_andIsIntersection() {
        Specification<TutorSubject> combined = TutorSearchSpecifications.modalityIn(Modality.VIRTUAL)
                .and(TutorSearchSpecifications.subjectValidated());
        assertThat(tutorNames(combined))
                .containsExactlyInAnyOrder("Ana Test", "Marta Test", "Luis Test");
    }

    @Test
    @DisplayName("Sin filtros la specification no acota (todas las materias)")
    void noFilters_returnsAllRows() {
        assertThat(tutorNames(TutorSearchSpecifications.subjectNameContains(null)))
                .hasSize(5);
    }

    @Test
    @DisplayName("subjectActive excluye las materias desactivadas")
    void subjectActive_excludesInactiveSubjects() {
        Career career = careerRepository.findAll().get(0);
        Subject inactiveSubject = subjectRepository.save(Subject.builder()
                .name("Materia inactiva").isBasic(true).career(career).build());
        TutorProfile inactiveTutor = tutor(career, "Inactivo Test", 4.0);
        TutorSubject inactive = tutorSubject(inactiveTutor, inactiveSubject, Modality.VIRTUAL,
                CompensationType.PAID, TutorSubjectStatus.ACTIVE);
        inactive.setActive(false);
        tutorSubjectRepository.saveAndFlush(inactive);

        assertThat(tutorNames(TutorSearchSpecifications.subjectActive()))
                .containsExactlyInAnyOrder("Ana Test", "Marta Test", "Carlos Test", "Luis Test", "Nora Test");
    }
}
