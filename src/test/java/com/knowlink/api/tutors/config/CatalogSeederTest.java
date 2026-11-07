package com.knowlink.api.tutors.config;

import com.knowlink.api.tutors.data.enums.CareerType;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CatalogSeederTest {

    @Autowired
    private InstitutionSeeder institutionSeeder;
    @Autowired
    private CareerSeeder careerSeeder;
    @Autowired
    private SubjectSeeder subjectSeeder;
    @Autowired
    private IInstitutionRepository institutionRepository;
    @Autowired
    private ICareerRepository careerRepository;
    @Autowired
    private ISubjectRepository subjectRepository;

    private void runSeeders() {
        institutionSeeder.run();
        careerSeeder.run();
        subjectSeeder.run();
    }

    @Test
    @DisplayName("Ejecutar los seeders dos veces no duplica carreras ni materias")
    void runningSeedersTwice_doesNotDuplicateCatalog() {
        long institutions = institutionRepository.count();
        long careers = careerRepository.count();
        long subjects = subjectRepository.count();

        runSeeders();

        assertThat(institutionRepository.count()).isEqualTo(institutions);
        assertThat(careerRepository.count()).isEqualTo(careers);
        assertThat(subjectRepository.count()).isEqualTo(subjects);

        for (String institutionName : InstitutionCatalogData.catalog().keySet()) {
            Institution institution = institutionRepository.findByName(institutionName).orElseThrow();
            long shared = careerRepository.findByInstitution_InstitutionIdAndTypeOrderByName(
                    institution.getInstitutionId(), CareerType.SHARED).size();
            assertThat(shared).as("shared careers of %s", institutionName).isEqualTo(1);
        }
    }

    @Test
    @Transactional
    @DisplayName("Con una base que solo tiene UTN, el seeder carga UNVM completa")
    void seeders_loadUnvmWhenOnlyUtnWasMigrated() {
        Institution unvm = institutionRepository.findByName(InstitutionCatalogData.UNVM).orElseThrow();
        List<Career> unvmCareers = careerRepository.findByInstitution_InstitutionIdAndTypeOrderByName(
                unvm.getInstitutionId(), CareerType.REGULAR);

        subjectRepository.deleteAll(subjectRepository.findByInstitution_InstitutionIdOrderByName(
                unvm.getInstitutionId()));
        careerRepository.deleteAll(unvmCareers);
        careerRepository.findByInstitution_InstitutionIdAndType(unvm.getInstitutionId(), CareerType.SHARED)
                .ifPresent(careerRepository::delete);
        institutionRepository.delete(unvm);

        runSeeders();

        Institution reloaded = institutionRepository.findByName(InstitutionCatalogData.UNVM).orElseThrow();
        assertThat(careerRepository.findByInstitution_InstitutionIdAndTypeOrderByName(
                reloaded.getInstitutionId(), CareerType.REGULAR))
                .extracting(Career::getName)
                .containsExactlyInAnyOrder("Licenciatura en Administración", "Medicina Veterinaria");
        assertThat(careerRepository.findByInstitution_InstitutionIdAndType(
                reloaded.getInstitutionId(), CareerType.SHARED)).isPresent();
        assertThat(subjectRepository.findSharedByInstitutionId(reloaded.getInstitutionId()))
                .extracting(subject -> subject.getName())
                .containsExactlyInAnyOrder("Análisis Matemático", "Inglés");
    }
}
