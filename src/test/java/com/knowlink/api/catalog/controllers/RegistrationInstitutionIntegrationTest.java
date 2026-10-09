package com.knowlink.api.catalog.controllers;

import com.knowlink.api.catalog.data.enums.CareerType;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationInstitutionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IInstitutionRepository institutionRepository;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    private Institution utn;
    private Career sistemas;
    private Career civil;
    private Career unvmCareer;
    private Subject programacion;
    private Subject analisisMatematico;
    private Subject programacionWebUnvm;

    @BeforeEach
    void setUpCatalog() {
        utn = institutionRepository.findByName("UTN FRVM").orElseThrow();
        Institution unvm = institutionRepository.findByName("UNVM").orElseThrow();
        sistemas = careerRepository.findByName("Ingeniería en Sistemas").orElseThrow();
        civil = careerRepository.findByName("Ingeniería Civil").orElseThrow();
        unvmCareer = careerRepository.findByName("Profesorado de Matemática").orElseThrow();
        programacion = subjectRepository
                .findByNameAndInstitutionInstitutionId("Programación", utn.getInstitutionId()).orElseThrow();
        analisisMatematico = subjectRepository
                .findByNameAndInstitutionInstitutionId("Análisis Matemático", utn.getInstitutionId()).orElseThrow();
        programacionWebUnvm = subjectRepository
                .findByNameAndInstitutionInstitutionId("Programación Web", unvm.getInstitutionId()).orElseThrow();
    }

    private String uniqueEmail() {
        return "registro." + UUID.randomUUID() + "@test.com";
    }

    private String studentPayload(UUID institutionId, UUID careerId) {
        return "{"
                + "\"email\":\"" + uniqueEmail() + "\","
                + "\"password\":\"Password1!\","
                + "\"confirmPassword\":\"Password1!\","
                + "\"firstName\":\"Ana\","
                + "\"lastName\":\"Perez\","
                + "\"dni\":\"12345678\","
                + "\"phoneNumber\":\"1122334455\","
                + "\"institutionId\":\"" + institutionId + "\","
                + "\"careerId\":\"" + careerId + "\""
                + "}";
    }

    private String tutorPayload(UUID institutionId, UUID careerId, UUID... subjectIds) {
        StringBuilder subjects = new StringBuilder();
        for (UUID subjectId : subjectIds) {
            if (subjects.length() > 0) {
                subjects.append(",");
            }
            subjects.append("{\"subjectId\":\"").append(subjectId)
                    .append("\",\"modality\":\"VIRTUAL\",\"compensationType\":\"PAID\",")
                    .append("\"pricePerHour\":2000.00}");
        }
        return "{"
                + "\"email\":\"" + uniqueEmail() + "\","
                + "\"password\":\"Password1!\","
                + "\"confirmPassword\":\"Password1!\","
                + "\"firstName\":\"Laura\","
                + "\"lastName\":\"Gomez\","
                + "\"dni\":\"87654321\","
                + "\"phoneNumber\":\"1199988776\","
                + "\"institutionId\":\"" + institutionId + "\","
                + "\"careerId\":\"" + careerId + "\","
                + "\"biography\":\"Profe de prueba\","
                + "\"address\":\"Calle Falsa 123\","
                + "\"subjects\":[" + subjects + "]"
                + "}";
    }

    @Test
    @DisplayName("CP-043.31 - Registro de alumno con institucion y carrera validos responde 201 (US-01)")
    void registerStudent_withInstitutionAndCareer_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentPayload(utn.getInstitutionId(), sistemas.getCareerId())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CP-043.32 - Registro de alumno sin institutionId responde 400")
    void registerStudent_missingInstitutionId_returns400() throws Exception {
        String payload = studentPayload(utn.getInstitutionId(), sistemas.getCareerId())
                .replace("\"institutionId\":\"" + utn.getInstitutionId() + "\",", "");

        mockMvc.perform(post("/api/v1/auth/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-043.33 - Registro de alumno con carrera de otra institucion responde 400")
    void registerStudent_careerFromAnotherInstitution_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentPayload(utn.getInstitutionId(), unvmCareer.getCareerId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("La carrera seleccionada no pertenece a la institución indicada."));
    }

    @Test
    @DisplayName("CP-043.34 - Registro de tutor con materias de su carrera y de la carrera reservada responde 201 (US-42)")
    void registerTutor_withCareerAndSharedSubjects_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/tutor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tutorPayload(utn.getInstitutionId(), sistemas.getCareerId(),
                                programacion.getSubjectId(), analisisMatematico.getSubjectId())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CP-043.35 - Registro de tutor con materia de otra institucion responde 400")
    void registerTutor_subjectFromAnotherInstitution_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/tutor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tutorPayload(utn.getInstitutionId(), sistemas.getCareerId(),
                                programacionWebUnvm.getSubjectId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("La materia seleccionada no pertenece a la institución de la carrera elegida."));
    }

    @Test
    @DisplayName("CP-043.36 - Registro de tutor con materia no asociada a su carrera ni compartida responde 400")
    void registerTutor_subjectNotTeachableInCareer_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/tutor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tutorPayload(utn.getInstitutionId(), civil.getCareerId(),
                                programacion.getSubjectId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "La materia seleccionada no está asociada a esta carrera ni a las materias compartidas de la institución."));
    }

    @Test
    @DisplayName("CP-043.37 - El listado de carreras para el selector de registro es publico e incluye el tipo")
    void careersForRegistrationSelector_isPublicWithTypeInfo() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/institutions/" + utn.getInstitutionId() + "/careers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Materias Compartidas')].type", hasItem("COMPARTIDA")))
                .andExpect(jsonPath("$[?(@.name == 'Ingeniería en Sistemas')].type", hasItem("REGULAR")));
    }
}