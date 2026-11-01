package com.knowlink.api.community.data.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityAttachment {

    @Column(name = "attachment_path", length = 500)
    private String storagePath;

    @Column(name = "attachment_file_name", length = 255)
    private String originalFileName;

    @Column(name = "attachment_content_type", length = 100)
    private String contentType;

    @Column(name = "attachment_size_bytes")
    private Long sizeInBytes;
}
