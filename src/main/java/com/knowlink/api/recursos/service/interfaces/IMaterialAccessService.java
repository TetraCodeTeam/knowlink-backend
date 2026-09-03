package com.knowlink.api.recursos.service.interfaces;

import com.knowlink.api.recursos.dto.MaterialResponse;

import java.util.List;
import java.util.UUID;

public interface IMaterialAccessService {
    boolean tieneAcceso(UUID studentId, UUID tutorUserId);

    List<MaterialResponse> listarMaterialesAccesibles(UUID studentId, UUID tutorUserId, UUID subjectId);

    void validarAccesoODenegar(UUID studentId, UUID materialId);
}
