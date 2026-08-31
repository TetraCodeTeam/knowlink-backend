package com.knowlink.api.recursos.services;

import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.recursos.dto.MaterialResponse;
import com.knowlink.api.recursos.dto.MaterialUploadRequest;
import com.knowlink.api.recursos.exception.FormatNotAllowedException;
import com.knowlink.api.recursos.exception.SinReservaActivaException;
import com.knowlink.api.recursos.exception.SubjectNotAssociatedException;
import com.knowlink.api.recursos.service.implementations.MaterialServiceImpl;
import com.knowlink.api.recursos.service.interfaces.IReservationService;
import com.knowlink.api.recursos.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.tutors.data.enums.MaterialType;
import com.knowlink.api.tutors.data.models.*;
import com.knowlink.api.tutors.repositories.IAcademicMaterialRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialServiceImplTest {

        @Mock
        private IAcademicMaterialRepository materialRepository;
        @Mock
        private ISubjectRepository subjectRepository;
        @Mock
        private ITutorProfileRepository tutorProfileRepository;
        @Mock
        private ITutorSubjectRepository tutorSubjectRepository;
        @Mock
        private ISupabaseStorageService supabaseStorageService;
        @Mock
        private IReservationService reservationService;

        private MaterialServiceImpl materialService;

        private UUID tutorUserId;
        private UUID subjectId;
        private UUID tutorProfileId;
        private Subject subject;
        private TutorProfile tutorProfile;
        private User tutorUser;
        private TutorSubject tutorSubject;

        @BeforeEach
        void setUp() {
                materialService = new MaterialServiceImpl(
                                materialRepository,
                                subjectRepository,
                                tutorProfileRepository,
                                tutorSubjectRepository,
                                supabaseStorageService,
                                reservationService);

                tutorUserId = UUID.randomUUID();
                subjectId = UUID.randomUUID();
                tutorProfileId = UUID.randomUUID();

                tutorUser = User.builder()
                                .userId(tutorUserId)
                                .fullName("Tutor Test")
                                .role(Role.TUTOR)
                                .build();

                tutorProfile = TutorProfile.builder()
                                .tutorProfileId(tutorProfileId)
                                .user(tutorUser)
                                .build();

                subject = Subject.builder()
                                .subjectId(subjectId)
                                .name("Análisis Matemático II")
                                .build();

                tutorSubject = TutorSubject.builder()
                                .tutorProfile(tutorProfile)
                                .subject(subject)
                                .build();
        }

        // ==================== AC3 — Materia obligatoria ====================

        @Test
        @DisplayName("AC3: upload without subjectId throws SubjectNotAssociatedException with Spanish message")
        void uploadWithoutSubjectId_throwsSubjectNotAssociatedException() {
                MaterialUploadRequest request = new MaterialUploadRequest("Resumen", null);
                MockMultipartFile file = createValidFile();

                assertThatThrownBy(() -> materialService.upload(request, file, tutorUserId))
                                .isInstanceOf(SubjectNotAssociatedException.class)
                                .hasMessage("Debés asociar el material a una materia");

                verifyNoInteractions(supabaseStorageService);
                verifyNoInteractions(materialRepository);
        }

        // ==================== AC1 — Nombre requerido ====================

        @Test
        @DisplayName("AC1: upload with blank name throws ValidationException with Spanish message")
        void uploadWithBlankName_throwsValidationException() {
                MaterialUploadRequest request = new MaterialUploadRequest("  ", subjectId);
                MockMultipartFile file = createValidFile();

                assertThatThrownBy(() -> materialService.upload(request, file, tutorUserId))
                                .isInstanceOf(ValidationException.class)
                                .hasMessage("Debés indicar un nombre para el material");

                verifyNoInteractions(supabaseStorageService);
                verifyNoInteractions(materialRepository);
        }

        @Test
        @DisplayName("AC1: upload with null name throws ValidationException with Spanish message")
        void uploadWithNullName_throwsValidationException() {
                MaterialUploadRequest request = new MaterialUploadRequest(null, subjectId);
                MockMultipartFile file = createValidFile();

                assertThatThrownBy(() -> materialService.upload(request, file, tutorUserId))
                                .isInstanceOf(ValidationException.class)
                                .hasMessage("Debés indicar un nombre para el material");

                verifyNoInteractions(supabaseStorageService);
                verifyNoInteractions(materialRepository);
        }

        // ==================== AC4 — Formatos no permitidos ====================

        @Test
        @DisplayName("AC4: upload with .docx extension throws FormatNotAllowedException")
        void uploadWithDocxExtension_throwsFormatNotAllowedException() {
                MaterialUploadRequest request = new MaterialUploadRequest("Resumen", subjectId);
                MockMultipartFile file = new MockMultipartFile(
                                "file", "resumen.docx", "application/msword", new byte[] { 1, 2, 3 });

                assertThatThrownBy(() -> materialService.upload(request, file, tutorUserId))
                                .isInstanceOf(FormatNotAllowedException.class)
                                .hasMessage("El formato del archivo no está permitido");

                verifyNoInteractions(supabaseStorageService);
                verifyNoInteractions(materialRepository);
        }

        @Test
        @DisplayName("AC4: upload with .jpg extension throws FormatNotAllowedException")
        void uploadWithJpgExtension_throwsFormatNotAllowedException() {
                MaterialUploadRequest request = new MaterialUploadRequest("Resumen", subjectId);
                MockMultipartFile file = new MockMultipartFile(
                                "file", "foto.jpg", "image/jpeg", new byte[] { 1, 2, 3 });

                assertThatThrownBy(() -> materialService.upload(request, file, tutorUserId))
                                .isInstanceOf(FormatNotAllowedException.class)
                                .hasMessage("El formato del archivo no está permitido");

                verifyNoInteractions(supabaseStorageService);
                verifyNoInteractions(materialRepository);
        }

        @Test
        @DisplayName("AC4: upload with .zip extension throws FormatNotAllowedException")
        void uploadWithZipExtension_throwsFormatNotAllowedException() {
                MaterialUploadRequest request = new MaterialUploadRequest("Resumen", subjectId);
                MockMultipartFile file = new MockMultipartFile(
                                "file", "archive.zip", "application/zip", new byte[] { 1, 2, 3 });

                assertThatThrownBy(() -> materialService.upload(request, file, tutorUserId))
                                .isInstanceOf(FormatNotAllowedException.class)
                                .hasMessage("El formato del archivo no está permitido");

                verifyNoInteractions(supabaseStorageService);
                verifyNoInteractions(materialRepository);
        }

        @Test
        @DisplayName("AC4: upload with .mp4 extension throws FormatNotAllowedException")
        void uploadWithMp4Extension_throwsFormatNotAllowedException() {
                MaterialUploadRequest request = new MaterialUploadRequest("Resumen", subjectId);
                MockMultipartFile file = new MockMultipartFile(
                                "file", "video.mp4", "video/mp4", new byte[] { 1, 2, 3 });

                assertThatThrownBy(() -> materialService.upload(request, file, tutorUserId))
                                .isInstanceOf(FormatNotAllowedException.class)
                                .hasMessage("El formato del archivo no está permitido");

                verifyNoInteractions(supabaseStorageService);
                verifyNoInteractions(materialRepository);
        }

        @Test
        @DisplayName("AC4: upload with mismatched content-type throws FormatNotAllowedException")
        void uploadWithMismatchedContentType_throwsFormatNotAllowedException() {
                MaterialUploadRequest request = new MaterialUploadRequest("Resumen", subjectId);
                MockMultipartFile file = new MockMultipartFile(
                                "file", "resumen.pdf", "application/msword", new byte[] { 1, 2, 3 });

                assertThatThrownBy(() -> materialService.upload(request, file, tutorUserId))
                                .isInstanceOf(FormatNotAllowedException.class)
                                .hasMessage("El formato del archivo no está permitido");

                verifyNoInteractions(supabaseStorageService);
                verifyNoInteractions(materialRepository);
        }

        // ==================== AC4 — Formatos permitidos ====================

        @Test
        @DisplayName("AC4: upload with .pdf extension succeeds")
        void uploadWithPdfExtension_succeeds() {
                MaterialUploadRequest request = new MaterialUploadRequest("Resumen Unidad 3", subjectId);
                MockMultipartFile file = new MockMultipartFile(
                                "file", "resumen.pdf", "application/pdf", new byte[] { 1, 2, 3 });

                when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
                when(tutorProfileRepository.findByUserId(tutorUserId)).thenReturn(Optional.of(tutorProfile));
                when(tutorSubjectRepository.findByTutorProfileId(tutorProfileId)).thenReturn(List.of(tutorSubject));
                when(supabaseStorageService.upload(any(MultipartFile.class), eq(subjectId)))
                                .thenReturn(subjectId + "/uuid-resumen.pdf");
                when(materialRepository.save(any(AcademicMaterial.class))).thenAnswer(inv -> {
                        AcademicMaterial m = inv.getArgument(0);
                        m.setAcademicMaterialId(UUID.randomUUID());
                        return m;
                });

                var response = materialService.upload(request, file, tutorUserId);

                assertThat(response).isNotNull();
                assertThat(response.name()).isEqualTo("Resumen Unidad 3");
                assertThat(response.format()).isEqualTo("PDF");
                verify(supabaseStorageService).upload(any(MultipartFile.class), eq(subjectId));
                verify(materialRepository).save(any(AcademicMaterial.class));
        }

        @Test
        @DisplayName("AC4: upload with .png extension succeeds")
        void uploadWithPngExtension_succeeds() {
                MaterialUploadRequest request = new MaterialUploadRequest("Diagrama", subjectId);
                MockMultipartFile file = new MockMultipartFile(
                                "file", "diagrama.png", "image/png", new byte[] { 1, 2, 3 });

                when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
                when(tutorProfileRepository.findByUserId(tutorUserId)).thenReturn(Optional.of(tutorProfile));
                when(tutorSubjectRepository.findByTutorProfileId(tutorProfileId)).thenReturn(List.of(tutorSubject));
                when(supabaseStorageService.upload(any(MultipartFile.class), eq(subjectId)))
                                .thenReturn(subjectId + "/uuid-diagrama.png");
                when(materialRepository.save(any(AcademicMaterial.class))).thenAnswer(inv -> {
                        AcademicMaterial m = inv.getArgument(0);
                        m.setAcademicMaterialId(UUID.randomUUID());
                        return m;
                });

                var response = materialService.upload(request, file, tutorUserId);

                assertThat(response).isNotNull();
                assertThat(response.name()).isEqualTo("Diagrama");
                assertThat(response.format()).isEqualTo("PNG");
        }

        @Test
        @DisplayName("AC4: upload with .xlsx extension succeeds")
        void uploadWithXlsxExtension_succeeds() {
                MaterialUploadRequest request = new MaterialUploadRequest("Datos", subjectId);
                MockMultipartFile file = new MockMultipartFile(
                                "file", "datos.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                new byte[] { 1, 2, 3 });

                when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
                when(tutorProfileRepository.findByUserId(tutorUserId)).thenReturn(Optional.of(tutorProfile));
                when(tutorSubjectRepository.findByTutorProfileId(tutorProfileId)).thenReturn(List.of(tutorSubject));
                when(supabaseStorageService.upload(any(MultipartFile.class), eq(subjectId)))
                                .thenReturn(subjectId + "/uuid-datos.xlsx");
                when(materialRepository.save(any(AcademicMaterial.class))).thenAnswer(inv -> {
                        AcademicMaterial m = inv.getArgument(0);
                        m.setAcademicMaterialId(UUID.randomUUID());
                        return m;
                });

                var response = materialService.upload(request, file, tutorUserId);

                assertThat(response).isNotNull();
                assertThat(response.name()).isEqualTo("Datos");
                assertThat(response.format()).isEqualTo("XLSX");
        }

        // ==================== AC2 — Reserva validation ====================

        @Test
        @DisplayName("AC2: student without any reservation gets SinReservaActivaException on listBySubject")
        void listByStudentWithoutReservation_throwsSinReservaActivaException() {
                UUID studentId = UUID.randomUUID();
                when(reservationService.tieneAlgunaReservaEnMateria(studentId, subjectId)).thenReturn(false);

                assertThatThrownBy(() -> materialService.listBySubject(subjectId, studentId, Role.STUDENT.name()))
                                .isInstanceOf(SinReservaActivaException.class)
                                .hasMessage("El alumno no tiene reserva activa con este tutor");

                verify(materialRepository, never()).findActiveBySubjectId(any());
        }

        @Test
        @DisplayName("AC2: student without reservation for specific tutor gets SinReservaActivaException on download")
        void downloadByStudentWithoutReservation_throwsSinReservaActivaException() {
                UUID studentId = UUID.randomUUID();
                UUID materialId = UUID.randomUUID();

                AcademicMaterial material = AcademicMaterial.builder()
                                .academicMaterialId(materialId)
                                .tutorSubject(tutorSubject)
                                .storagePath(subjectId + "/test.pdf")
                                .materialType(MaterialType.PDF)
                                .active(true)
                                .build();

                when(materialRepository.findByAcademicMaterialIdAndActiveTrue(materialId))
                                .thenReturn(Optional.of(material));
                when(reservationService.tieneReservaConTutorEnMateria(studentId, tutorUserId, subjectId))
                                .thenReturn(false);

                assertThatThrownBy(() -> materialService.getDownloadUrl(materialId, studentId, Role.STUDENT.name()))
                                .isInstanceOf(SinReservaActivaException.class)
                                .hasMessage("El alumno no tiene reserva activa con este tutor");

                verifyNoInteractions(supabaseStorageService);
        }

        @Test
        @DisplayName("BUG-REGRESION: TUTOR listando su propia materia NO debe recibir material cargado por otro tutor de esa misma materia")
        void listBySubject_asTutor_excludesMaterialFromOtherTutorsOfSameSubject() {
                // Tutor A (el de setUp) sube material para "Análisis Matemático II"
                AcademicMaterial materialDeTutorA = AcademicMaterial.builder()
                                .academicMaterialId(UUID.randomUUID())
                                .tutorSubject(tutorSubject) // vinculado a tutorUserId (Tutor A)
                                .name("Resumen de Tutor A")
                                .active(true)
                                .build();

                // Tutor B, distinto de Tutor A, también dicta la misma materia y sube su propio
                // material
                UUID tutorBUserId = UUID.randomUUID();
                User tutorBUser = User.builder()
                                .userId(tutorBUserId)
                                .fullName("Tutor B")
                                .role(Role.TUTOR)
                                .build();
                TutorProfile tutorProfileB = TutorProfile.builder()
                                .tutorProfileId(UUID.randomUUID())
                                .user(tutorBUser)
                                .build();
                TutorSubject tutorSubjectB = TutorSubject.builder()
                                .tutorProfile(tutorProfileB)
                                .subject(subject) // misma materia que Tutor A
                                .build();
                AcademicMaterial materialDeTutorB = AcademicMaterial.builder()
                                .academicMaterialId(UUID.randomUUID())
                                .tutorSubject(tutorSubjectB)
                                .name("Resumen de Tutor B")
                                .active(true)
                                .build();

                // El repositorio, tal como está hoy, devuelve TODO el material activo de la
                // materia
                // sin distinguir tutor — esto es correcto a nivel de datos, el filtro debe
                // pasar por el service.
                when(materialRepository.findActiveBySubjectId(subjectId))
                                .thenReturn(List.of(materialDeTutorA, materialDeTutorB));

                // Tutor A consulta el listado de SU materia
                List<MaterialResponse> resultado = materialService.listBySubject(subjectId, tutorUserId,
                                Role.TUTOR.name());

                // Solo debe ver su propio material, nunca el de Tutor B
                assertThat(resultado)
                                .hasSize(1)
                                .extracting(MaterialResponse::name)
                                .containsExactly("Resumen de Tutor A");

                // No debe haberse llamado a ninguna validación de reserva (eso es solo para
                // STUDENT)
                verifyNoInteractions(reservationService);
        }

        // ==================== Helper ====================

        private MockMultipartFile createValidFile() {
                return new MockMultipartFile(
                                "file", "test.pdf", "application/pdf", new byte[] { 1, 2, 3 });
        }
}
