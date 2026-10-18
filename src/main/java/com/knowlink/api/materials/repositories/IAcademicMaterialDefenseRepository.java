package com.knowlink.api.materials.repositories;

import com.knowlink.api.materials.data.models.AcademicMaterialDefense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IAcademicMaterialDefenseRepository extends JpaRepository<AcademicMaterialDefense, UUID> {

    Optional<AcademicMaterialDefense> findFirstByMaterial_AcademicMaterialIdOrderBySubmittedAtDesc(UUID materialId);
}
