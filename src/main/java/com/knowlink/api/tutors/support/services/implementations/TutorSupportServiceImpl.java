package com.knowlink.api.tutors.support.services.implementations;

import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.claims.data.models.SessionClaimComment;
import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.repositories.ClaimAttachmentRepository;
import com.knowlink.api.claims.repositories.SessionClaimCommentRepository;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;
import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.materials.data.models.AcademicMaterialDefense;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.MaterialDefenseAttachment;
import com.knowlink.api.materials.repositories.IAcademicMaterialDefenseRepository;
import com.knowlink.api.materials.repositories.MaterialDefenseAttachmentRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.materials.exception.FormatNotAllowedException;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.support.controllers.requests.SubmitMaterialDefenseRequest;
import com.knowlink.api.tutors.support.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.tutors.support.controllers.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportSummaryResponse;
import com.knowlink.api.tutors.support.services.interfaces.ITutorSupportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
public class TutorSupportServiceImpl implements ITutorSupportService {

    private static final int SIGNED_URL_EXPIRATION_SECONDS = 3600;
    private static final String EVIDENCE_BUCKET = "claimEvidence";
    private static final String DEFENSE_BUCKET = "materials";
    private static final Set<String> DEFENSE_EXTENSIONS = Set.of("pdf", "png", "xlsx");
    private static final Map<String, String> DEFENSE_CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SessionClaimRepository sessionClaimRepository;
    private final IAcademicMaterialReportRepository materialReportRepository;
    private final INotificationService notificationService;
    private final ClaimAttachmentRepository claimAttachmentRepository;
    private final SessionClaimCommentRepository claimCommentRepository;
    private final ISupabaseStorageService storageService;
    private final IFundsTransferRepository fundsTransferRepository;
    private final IStudentProfileRepository studentProfileRepository;
    private final int responseSlaHours;
    private final String materialReviewTeamName;
    private final IAcademicMaterialRepository materialRepository;
    private final IAcademicMaterialDefenseRepository materialDefenseRepository;
    private final MaterialDefenseAttachmentRepository defenseAttachmentRepository;
    private final int maxDefenseAttachments;
    private final long maxDefenseAttachmentBytes;

    public TutorSupportServiceImpl(
            SessionClaimRepository sessionClaimRepository,
            IAcademicMaterialReportRepository materialReportRepository,
            INotificationService notificationService,
            ClaimAttachmentRepository claimAttachmentRepository,
            SessionClaimCommentRepository claimCommentRepository,
            ISupabaseStorageService storageService,
            IFundsTransferRepository fundsTransferRepository,
            IStudentProfileRepository studentProfileRepository,
            @Value("${knowlink.support.claim-response-hours:24}") int responseSlaHours,
            @Value("${knowlink.support.material-review-team-name:Equipo de Propiedad Intelectual}")
            String materialReviewTeamName,
            IAcademicMaterialRepository materialRepository,
            IAcademicMaterialDefenseRepository materialDefenseRepository,
            MaterialDefenseAttachmentRepository defenseAttachmentRepository,
            @Value("${knowlink.support.material-defense.max-attachments:5}") int maxDefenseAttachments,
            @Value("${knowlink.support.material-defense.max-attachment-mb:10}") long maxDefenseAttachmentMb) {
        this.sessionClaimRepository = sessionClaimRepository;
        this.materialReportRepository = materialReportRepository;
        this.notificationService = notificationService;
        this.claimAttachmentRepository = claimAttachmentRepository;
        this.claimCommentRepository = claimCommentRepository;
        this.storageService = storageService;
        this.fundsTransferRepository = fundsTransferRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.responseSlaHours = responseSlaHours;
        this.materialReviewTeamName = materialReviewTeamName;
        this.materialRepository = materialRepository;
        this.materialDefenseRepository = materialDefenseRepository;
        this.defenseAttachmentRepository = defenseAttachmentRepository;
        this.maxDefenseAttachments = maxDefenseAttachments;
        this.maxDefenseAttachmentBytes = maxDefenseAttachmentMb * 1024 * 1024;
    }

    @Override
    @Transactional
    public TutorSupportResponse getSupport(UUID tutorUserId) {
        List<TutorSupportClaimResponse> claims = sessionClaimRepository
                .findByBooking_Tutor_UserIdOrderByCreatedAtDesc(tutorUserId).stream()
                .map(this::toClaimResponse)
                .toList();

        List<TutorReportedMaterialResponse> reportedMaterials = toReportedMaterials(
                materialReportRepository
                        .findByMaterial_TutorSubject_TutorProfile_User_UserIdOrderByReportedAtDesc(tutorUserId));

        notificationService.markSupportAsRead(tutorUserId);
        TutorSupportSummaryResponse summary = new TutorSupportSummaryResponse(
                claims.size(),
                reportedMaterials.size(),
                (int) reportedMaterials.stream()
                        .filter(material -> material.status() == MaterialReportStatus.IN_REVIEW)
                        .count());
        return new TutorSupportResponse(summary, claims, reportedMaterials);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialReportingPolicyResponse getMaterialReportingPolicy() {
        return new MaterialReportingPolicyResponse(
                List.of("PNG", "PDF", "XLSX"),
                2000,
                maxDefenseAttachments,
                maxDefenseAttachmentBytes,
                "El material académico cargado en KnowLink debe ser de autoría propia del tutor o contar con los derechos necesarios para su distribución. No está permitido subir material protegido por derechos de autor de terceros, contenido plagiado, ni archivos que no guarden relación directa con la materia a la que se asocian.",
                List.of(
                        new MaterialReportingPolicyResponse.ReasonPolicy(
                                MaterialReportReason.INAPPROPRIATE_CONTENT.name(),
                                "Contenido inapropiado o no pertinente",
                                "Material que no corresponde a la materia o contiene contenido inapropiado."),
                        new MaterialReportingPolicyResponse.ReasonPolicy(
                                MaterialReportReason.PLAGIARISM.name(),
                                "Plagio o derechos de autor",
                                "Contenido de terceros presentado sin autorización o atribución."),
                        new MaterialReportingPolicyResponse.ReasonPolicy(
                                MaterialReportReason.FALSE_INFORMATION.name(),
                                "Información falsa",
                                "Información académica incorrecta o engañosa."),
                        new MaterialReportingPolicyResponse.ReasonPolicy(
                                MaterialReportReason.OTHER.name(),
                                "Otro motivo",
                                "Motivo detallado por el alumno en la descripción del reporte.")),
                "El equipo administrativo evalúa cada denuncia según criterios de propiedad intelectual y pertinencia académica. Las denuncias de un mismo archivo se consolidan en una sola revisión.",
                "Si se confirma un incumplimiento, el material se retira y deja de estar disponible para los alumnos. El tutor recibe una notificación con el resultado.",
                "Si la denuncia se desestima, el material vuelve a estar disponible y el reporte se archiva.",
                "El alumno que realizó cada denuncia recibe una notificación con el resultado de su reporte.");
    }

    @Override
    @Transactional(readOnly = true)
    public TutorSupportClaimDetailResponse getClaimDetails(UUID tutorUserId, UUID claimId) {
        SessionClaim claim = sessionClaimRepository.findByClaimIdAndTutorUserId(claimId, tutorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CLAIM_NOT_FOUND",
                        "El reclamo no existe.",
                        "Claim " + claimId + " not found for tutor " + tutorUserId));

        var booking = claim.getBooking();
        var student = booking.getStudent();
        StudentProfile studentProfile = studentProfileRepository.findByUserId(student.getUserId()).orElse(null);
        String studentCareer = studentProfile == null ? null : studentProfile.getCareer().getName();
        String studentPicture = studentProfile == null
                ? student.getProfilePictureUrl()
                : studentProfile.getProfilePictureUrl();

        FundsTransfer transfer = fundsTransferRepository.findByBooking_BookingId(booking.getBookingId()).orElse(null);
        TutorSupportClaimDetailResponse.FundsSummary funds = toFundsSummary(booking.getAmount(), transfer);
        TutorSupportClaimDetailResponse.ReviewProgress progress = toReviewProgress(claim);
        List<ClaimAttachmentUrlResponse> attachments = claimAttachmentRepository
                .findByClaim_ClaimId(claimId).stream()
                .map(attachment -> new ClaimAttachmentUrlResponse(
                        attachment.getAttachmentId(),
                        attachment.getOriginalFileName(),
                        attachment.getContentType(),
                        attachment.getSizeInBytes(),
                        storageService.generateSignedUrl(
                                EVIDENCE_BUCKET, attachment.getStoragePath(), SIGNED_URL_EXPIRATION_SECONDS)))
                .toList();
        List<ClaimCommentResponse> comments = claimCommentRepository
                .findByClaim_ClaimIdOrderByCreatedAtAsc(claimId).stream()
                .map(this::toCommentResponse)
                .toList();

        return new TutorSupportClaimDetailResponse(
                claim.getClaimId(),
                booking.getBookingId(),
                new TutorSupportClaimDetailResponse.StudentSummary(
                        student.getFullName(), studentPicture, studentCareer),
                booking.getTutorSubject().getSubject().getName(),
                booking.getTutorSubject().getSubject().getCareer().getName(),
                booking.getSessionDate(),
                booking.getStartTime(),
                booking.getEndTime(),
                claim.getReason(),
                claim.getStatus(),
                claim.getClaimantRole(),
                claim.getClaimant().getFullName(),
                claim.getComment(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                funds,
                progress,
                attachments,
                comments);
    }

    @Override
    @Transactional
    public ClaimCommentResponse addClaimComment(UUID tutorUserId, UUID claimId, AddClaimCommentRequest request) {
        SessionClaim claim = sessionClaimRepository.findByClaimIdAndTutorUserId(claimId, tutorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CLAIM_NOT_FOUND",
                        "El reclamo no existe.",
                        "Claim " + claimId + " not found for tutor " + tutorUserId));
        if (claim.getStatus() == com.knowlink.api.claims.data.enums.ClaimStatus.RESOLVED) {
            throw new DuplicateResourceException(
                    "CLAIM_ALREADY_RESOLVED",
                    "No podés comentar un reclamo que ya fue resuelto.",
                    "Comment attempted on resolved claim " + claimId);
        }

        SessionClaimComment comment = claimCommentRepository.save(SessionClaimComment.builder()
                .claim(claim)
                .author(claim.getBooking().getTutor())
                .message(request.comment())
                .build());
        notificationService.createSupportNotification(
                claim.getBooking().getStudent().getUserId(),
                NotificationType.SESSION_CLAIM_COMMENT,
                claim.getClaimId(),
                "Nuevo comentario en un reclamo",
                "El tutor agregó un comentario al reclamo de la sesión de "
                        + claim.getBooking().getTutorSubject().getSubject().getName() + ".");
        return toCommentResponse(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public TutorReportedMaterialDetailResponse getReportedMaterialDetails(UUID tutorUserId, UUID materialId) {
        var material = findReportedMaterialOrThrow(tutorUserId, materialId);
        return toReportedMaterialDetail(materialId, material);
    }

    @Override
    @Transactional(readOnly = true)
    public TutorReportedMaterialDetailResponse getAdminReportedMaterialDetails(UUID materialId) {
        var material = materialRepository.findByAcademicMaterialIdAndActiveTrue(materialId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REPORTED_MATERIAL_NOT_FOUND",
                        "El material denunciado no existe.",
                        "Reported material not found for admin: " + materialId));
        return toReportedMaterialDetail(materialId, material);
    }

    private TutorReportedMaterialDetailResponse toReportedMaterialDetail(
            UUID materialId, AcademicMaterial material) {
        List<AcademicMaterialReport> reports = materialReportRepository.findDetailsByMaterialId(materialId);
        if (reports.isEmpty()) {
            throw new ResourceNotFoundException(
                    "REPORTED_MATERIAL_NOT_FOUND",
                    "El material denunciado no existe.",
                    "No reports found for material " + materialId);
        }
        AcademicMaterialDefense defense = materialDefenseRepository
                .findFirstByMaterial_AcademicMaterialIdOrderBySubmittedAtDesc(materialId)
                .orElse(null);
        List<TutorReportedMaterialDetailResponse.DefenseAttachment> attachments = defense == null
                ? List.of()
                : defenseAttachmentRepository.findByDefense_DefenseId(defense.getDefenseId()).stream()
                        .map(attachment -> toDefenseAttachment(attachment))
                        .toList();
        String reviewerName = reports.stream()
                .map(AcademicMaterialReport::getReviewedBy)
                .filter(Objects::nonNull)
                .map(user -> user.getFullName())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return new TutorReportedMaterialDetailResponse(
                material.getAcademicMaterialId(),
                material.getName(),
                material.getOriginalFileName(),
                material.getMaterialType().name(),
                material.getSizeInBytes(),
                material.getTutorSubject().getSubject().getName(),
                material.getTutorSubject().getSubject().getCareer().getName(),
                material.getUploadedAt(),
                material.isAvailable(),
                storageService.generateSignedUrl("materials", material.getStoragePath(), SIGNED_URL_EXPIRATION_SECONDS),
                reports.size(),
                aggregateStatus(reports),
                materialReviewTeamName,
                reviewerName,
                reports.stream().map(this::toReportDetail).toList(),
                new TutorReportedMaterialDetailResponse.DefenseDetail(
                        defense != null,
                        defense == null ? null : defense.getDescription(),
                        defense == null ? null : defense.getSubmittedAt(),
                        attachments));
    }

    @Override
    @Transactional
    public TutorReportedMaterialDetailResponse submitMaterialDefense(
            UUID tutorUserId,
            UUID materialId,
            SubmitMaterialDefenseRequest request,
            List<MultipartFile> files) {
        var material = findReportedMaterialOrThrow(tutorUserId, materialId);
        List<MultipartFile> attachments = normalizeFiles(files);
        validateDefense(request, attachments);

        boolean unresolvedReports = materialReportRepository.findDetailsByMaterialId(materialId).stream()
                .anyMatch(report -> report.getStatus() == null
                        || report.getStatus() != MaterialReportStatus.RESOLVED);
        if (!unresolvedReports) {
            throw new DuplicateResourceException(
                    "MATERIAL_REVIEW_COMPLETED",
                    "La revisión de este material ya finalizó.",
                    "Cannot submit defense after all reports are resolved for material " + materialId);
        }
        List<AcademicMaterialReport> reports = materialReportRepository.findDetailsByMaterialId(materialId);
        LocalDateTime mostRecentReportAt = reports.get(0).getReportedAt();
        AcademicMaterialDefense latestDefense = materialDefenseRepository
                .findFirstByMaterial_AcademicMaterialIdOrderBySubmittedAtDesc(materialId)
                .orElse(null);
        if (latestDefense != null && !latestDefense.getSubmittedAt().isBefore(mostRecentReportAt)) {
            throw new DuplicateResourceException(
                    "MATERIAL_DEFENSE_ALREADY_SUBMITTED",
                    "Ya presentaste un descargo para este material.",
                    "Material defense already exists for material " + materialId);
        }

        AcademicMaterialDefense defense = materialDefenseRepository.saveAndFlush(AcademicMaterialDefense.builder()
                .material(material)
                .tutor(material.getTutorSubject().getTutorProfile().getUser())
                .description(request.description())
                .build());

        List<String> uploadedPaths = new ArrayList<>();
        try {
            List<MaterialDefenseAttachment> entities = new ArrayList<>();
            for (MultipartFile file : attachments) {
                String path = storageService.upload(file, DEFENSE_BUCKET, "material-defense/" + materialId);
                uploadedPaths.add(path);
                entities.add(MaterialDefenseAttachment.builder()
                        .defense(defense)
                        .storagePath(path)
                        .originalFileName(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .sizeInBytes(file.getSize())
                        .build());
            }
            defenseAttachmentRepository.saveAll(entities);
        } catch (RuntimeException exception) {
            cleanupDefenseUploads(uploadedPaths);
            throw exception;
        }

        return getReportedMaterialDetails(tutorUserId, materialId);
    }

    private TutorSupportClaimResponse toClaimResponse(SessionClaim claim) {
        return new TutorSupportClaimResponse(
                claim.getClaimId(),
                claim.getBooking().getBookingId(),
                claim.getBooking().getTutorSubject().getSubject().getName(),
                claim.getBooking().getStudent().getFullName(),
                claim.getBooking().getSessionDate(),
                claim.getBooking().getStartTime(),
                claim.getBooking().getEndTime(),
                claim.getReason(),
                claim.getStatus(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    private List<TutorReportedMaterialResponse> toReportedMaterials(List<AcademicMaterialReport> reports) {
        Map<UUID, List<AcademicMaterialReport>> reportsByMaterial = new LinkedHashMap<>();
        for (AcademicMaterialReport report : reports) {
            UUID materialId = report.getMaterial().getAcademicMaterialId();
            reportsByMaterial.computeIfAbsent(materialId, ignored -> new ArrayList<>()).add(report);
        }

        return reportsByMaterial.values().stream()
                .map(this::toReportedMaterialResponse)
                .toList();
    }

    private TutorReportedMaterialResponse toReportedMaterialResponse(List<AcademicMaterialReport> reports) {
        AcademicMaterialReport mostRecentReport = reports.get(0);
        var material = mostRecentReport.getMaterial();

        return new TutorReportedMaterialResponse(
                material.getAcademicMaterialId(),
                material.getName(),
                material.getOriginalFileName(),
                material.getMaterialType().name(),
                material.getSizeInBytes(),
                material.getTutorSubject().getSubject().getName(),
                material.getUploadedAt(),
                material.isAvailable(),
                reports.size(),
                aggregateStatus(reports),
                mostRecentReport.getReportedAt());
    }

    private MaterialReportStatus aggregateStatus(List<AcademicMaterialReport> reports) {
        if (reports.stream().anyMatch(report -> report.getStatus() == MaterialReportStatus.IN_REVIEW)) {
            return MaterialReportStatus.IN_REVIEW;
        }
        if (reports.stream().anyMatch(report -> report.getStatus() == null
                || report.getStatus() == MaterialReportStatus.PENDING)) {
            return MaterialReportStatus.PENDING;
        }
        return MaterialReportStatus.RESOLVED;
    }

    private TutorSupportClaimDetailResponse.FundsSummary toFundsSummary(
            BigDecimal bookingAmount, FundsTransfer transfer) {
        if (transfer == null) {
            return new TutorSupportClaimDetailResponse.FundsSummary(
                    bookingAmount, BigDecimal.ZERO, BigDecimal.ZERO, null, "ARS");
        }

        boolean protectedFunds = transfer.getFundsStatus() == FundsStatus.HELD
                || transfer.getFundsStatus() == FundsStatus.SUSPENDED_BY_CLAIM;
        BigDecimal protectedAmount = protectedFunds ? transfer.getOriginalAmount() : BigDecimal.ZERO;
        BigDecimal protectedPercentage = transfer.getOriginalAmount().signum() == 0
                ? BigDecimal.ZERO
                : protectedAmount.multiply(BigDecimal.valueOf(100))
                        .divide(transfer.getOriginalAmount(), 2, RoundingMode.HALF_UP);

        return new TutorSupportClaimDetailResponse.FundsSummary(
                transfer.getOriginalAmount(), protectedAmount, protectedPercentage,
                transfer.getFundsStatus(), "ARS");
    }

    private TutorSupportClaimDetailResponse.ReviewProgress toReviewProgress(SessionClaim claim) {
        String phase = switch (claim.getStatus()) {
            case OPEN -> "MEDIATOR_ASSIGNMENT_QUEUED";
            case IN_REVIEW -> "IN_REVIEW";
            case RESOLVED -> "RESOLVED";
        };
        LocalDateTime dueAt = claim.getCreatedAt().plusHours(responseSlaHours);
        long remainingMinutes = Math.max(0,
                Duration.between(LocalDateTime.now(AppTimeZone.ZONE), dueAt).toMinutes());
        Integer queuePosition = null;
        if (claim.getStatus() == com.knowlink.api.claims.data.enums.ClaimStatus.OPEN) {
            List<SessionClaim> queuedClaims = sessionClaimRepository
                    .findByStatusInOrderByCreatedAtAsc(com.knowlink.api.claims.data.enums.ClaimStatus.ACTIVE_STATUSES);
            int position = 0;
            for (SessionClaim queuedClaim : queuedClaims) {
                if (queuedClaim.getStatus() != com.knowlink.api.claims.data.enums.ClaimStatus.OPEN) {
                    continue;
                }
                position++;
                if (queuedClaim.getClaimId().equals(claim.getClaimId())) {
                    queuePosition = position;
                    break;
                }
            }
        }
        return new TutorSupportClaimDetailResponse.ReviewProgress(phase, queuePosition, dueAt, remainingMinutes);
    }

    private AcademicMaterial findReportedMaterialOrThrow(UUID tutorUserId, UUID materialId) {
        var material = materialRepository.findActiveByIdAndTutorUserId(materialId, tutorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REPORTED_MATERIAL_NOT_FOUND",
                        "El material denunciado no existe.",
                        "Reported material " + materialId + " not found for tutor " + tutorUserId));
        if (materialReportRepository.findDetailsByMaterialId(materialId).isEmpty()) {
            throw new ResourceNotFoundException(
                    "REPORTED_MATERIAL_NOT_FOUND",
                    "El material denunciado no existe.",
                    "No reports found for material " + materialId);
        }
        return material;
    }

    private TutorReportedMaterialDetailResponse.ReportDetail toReportDetail(AcademicMaterialReport report) {
        return new TutorReportedMaterialDetailResponse.ReportDetail(
                report.getAcademicMaterialReportId(),
                report.getReason(),
                report.getDescription(),
                report.getReportedAt(),
                report.getStatus() == null ? MaterialReportStatus.PENDING : report.getStatus(),
                report.getResolution());
    }

    private ClaimCommentResponse toCommentResponse(SessionClaimComment comment) {
        return new ClaimCommentResponse(
                comment.getCommentId(),
                comment.getAuthor().getFullName(),
                comment.getAuthor().getRole(),
                comment.getMessage(),
                comment.getCreatedAt());
    }

    private TutorReportedMaterialDetailResponse.DefenseAttachment toDefenseAttachment(
            MaterialDefenseAttachment attachment) {
        return new TutorReportedMaterialDetailResponse.DefenseAttachment(
                attachment.getAttachmentId(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getSizeInBytes(),
                storageService.generateSignedUrl(
                        DEFENSE_BUCKET, attachment.getStoragePath(), SIGNED_URL_EXPIRATION_SECONDS));
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream().filter(Objects::nonNull).toList();
    }

    private void validateDefense(SubmitMaterialDefenseRequest request, List<MultipartFile> files) {
        if ((request.description() == null || request.description().isBlank()) && files.isEmpty()) {
            throw new ValidationException("Incluí una explicación o al menos una prueba de autoría.");
        }
        if (files.size() > maxDefenseAttachments) {
            throw new ValidationException("No podés adjuntar más de " + maxDefenseAttachments + " archivos.");
        }
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new ValidationException("El archivo adjunto no puede estar vacío.");
            }
            if (file.getSize() > maxDefenseAttachmentBytes) {
                throw new ValidationException("Cada archivo adjunto no puede superar los "
                        + (maxDefenseAttachmentBytes / (1024 * 1024)) + "MB.");
            }
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.length() > 255) {
                throw new FormatNotAllowedException("El formato del archivo no está permitido");
            }
            int dot = fileName.lastIndexOf('.');
            String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (!DEFENSE_EXTENSIONS.contains(extension)
                    || !Objects.equals(DEFENSE_CONTENT_TYPES.get(extension), file.getContentType())) {
                throw new FormatNotAllowedException("El formato del archivo no está permitido");
            }
        }
    }

    private void cleanupDefenseUploads(List<String> paths) {
        for (String path : paths) {
            try {
                storageService.delete(DEFENSE_BUCKET, path);
            } catch (Exception exception) {
                log.warn("Failed to clean up material defense attachment {}: {}", path, exception.getMessage());
            }
        }
    }
}
