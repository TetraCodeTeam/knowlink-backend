package com.knowlink.api.materials.data.models;

import com.knowlink.api.shared.utils.AppTimeZone;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "material_defense_attachment", indexes = @Index(
        name = "idx_material_defense_attachment_defense_id", columnList = "academic_material_defense_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialDefenseAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "material_defense_attachment_id", updatable = false, nullable = false)
    private UUID attachmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_material_defense_id", nullable = false)
    private AcademicMaterialDefense defense;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_in_bytes", nullable = false)
    private long sizeInBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(AppTimeZone.ZONE);
        }
    }
}
