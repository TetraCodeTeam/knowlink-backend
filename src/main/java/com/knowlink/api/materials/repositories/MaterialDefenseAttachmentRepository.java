package com.knowlink.api.materials.repositories;

import com.knowlink.api.materials.data.models.MaterialDefenseAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaterialDefenseAttachmentRepository extends JpaRepository<MaterialDefenseAttachment, UUID> {

    List<MaterialDefenseAttachment> findByDefense_DefenseId(UUID defenseId);
}
