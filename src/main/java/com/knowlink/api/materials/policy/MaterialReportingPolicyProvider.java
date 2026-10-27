package com.knowlink.api.materials.policy;

import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.support.config.SupportProperties;
import com.knowlink.api.support.utils.FileFormatConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MaterialReportingPolicyProvider {

    private final SupportProperties supportProperties;

    public MaterialReportingPolicyResponse getPolicy() {
        return new MaterialReportingPolicyResponse(
                FileFormatConstants.ACCEPTED_FORMAT_LABELS,
                2000,
                supportProperties.materialDefense().maxAttachments(),
                supportProperties.materialDefense().maxAttachmentBytes(),
                "El material académico cargado en KnowLink debe ser de autoría propia del tutor o contar "
                        + "con los derechos necesarios para su distribución. No está permitido subir material "
                        + "protegido por derechos de autor de terceros, contenido plagiado, ni archivos que no "
                        + "guarden relación directa con la materia a la que se asocian.",
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
                "El equipo administrativo evalúa cada denuncia según criterios de propiedad intelectual y "
                        + "pertinencia académica. Las denuncias de un mismo archivo se consolidan en una sola revisión.",
                "Si se confirma un incumplimiento, el material se retira y deja de estar disponible para los "
                        + "alumnos. El tutor recibe una notificación con el resultado.",
                "Si la denuncia se desestima, el material vuelve a estar disponible y el reporte se archiva.",
                "El alumno que realizó cada denuncia recibe una notificación con el resultado de su reporte.");
    }
}
