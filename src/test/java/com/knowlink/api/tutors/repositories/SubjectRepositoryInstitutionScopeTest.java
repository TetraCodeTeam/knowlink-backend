package com.knowlink.api.tutors.repositories;

import com.knowlink.api.testsupport.CatalogFixtures;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SubjectRepositoryInstitutionScopeTest {

    @Autowired
    private IInstitutionRepository institutionRepository;
    @Autowired
    private ICareerRepository careerRepository;
    @Autowired
    private ISubjectRepository subjectRepository;

    private Institution utn;
    private Institution unvm;
    private Career utnRegular;
    private Career unvmRegular;
    private Subject utnShared;
    private Subject unvmShared;
    private Subject utnOwn;

    @BeforeEach
    void setUp() {
        utn = institutionRepository.save(CatalogFixtures.institution("UTN FRVM"));
        unvm = institutionRepository.save(CatalogFixtures.institution("UNVM"));
        utnRegular = careerRepository.save(CatalogFixtures.career(utn, "Ingeniería en Sistemas"));
        unvmRegular = careerRepository.save(CatalogFixtures.career(unvm, "Licenciatura en Administración"));
        Career utnSharedCareer = careerRepository.save(CatalogFixtures.sharedCareer(utn));
        Career unvmSharedCareer = careerRepository.save(CatalogFixtures.sharedCareer(unvm));

        utnShared = subjectRepository.save(CatalogFixtures.subject(utnSharedCareer, "Análisis Matemático"));
        unvmShared = subjectRepository.save(CatalogFixtures.subject(unvmSharedCareer, "Análisis Matemático"));
        utnOwn = subjectRepository.save(CatalogFixtures.subject(utnRegular, "Programación"));
        subjectRepository.save(CatalogFixtures.subject(unvmRegular, "Programación"));
    }

    @Test
    void findSharedByInstitutionId_returnsOnlyThatInstitutionSharedSubjects() {
        assertThat(subjectRepository.findSharedByInstitutionId(utn.getInstitutionId()))
                .extracting(Subject::getSubjectId)
                .containsExactly(utnShared.getSubjectId());
        assertThat(subjectRepository.findSharedByInstitutionId(unvm.getInstitutionId()))
                .extracting(Subject::getSubjectId)
                .containsExactly(unvmShared.getSubjectId());
    }

    @Test
    void findSharedAndByCareerId_combinesOwnAndSharedSubjectsOfTheSameInstitution() {
        List<Subject> utnSubjects = subjectRepository.findSharedAndByCareerId(utnRegular.getCareerId());

        assertThat(utnSubjects).extracting(Subject::getName)
                .containsExactlyInAnyOrder("Análisis Matemático", "Programación");
        assertThat(utnSubjects).extracting(Subject::getInstitution)
                .allMatch(institution -> institution.getInstitutionId().equals(utn.getInstitutionId()));
    }

    @Test
    void findSharedAndByCareerId_doesNotMixTheOtherInstitutionSharedSubjects() {
        assertThat(subjectRepository.findSharedAndByCareerId(unvmRegular.getCareerId()))
                .extracting(Subject::getName)
                .containsExactlyInAnyOrder("Análisis Matemático", "Programación")
                .doesNotContain("Sistemas Operativos");
        assertThat(subjectRepository.findSharedAndByCareerId(unvmRegular.getCareerId()))
                .extracting(Subject::getInstitution)
                .allMatch(institution -> institution.getInstitutionId().equals(unvm.getInstitutionId()));
    }
}
