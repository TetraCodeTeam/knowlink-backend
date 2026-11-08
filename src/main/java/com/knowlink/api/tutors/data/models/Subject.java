package com.knowlink.api.tutors.data.models;

import com.knowlink.api.tutors.data.enums.CareerType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "subjects", uniqueConstraints = @UniqueConstraint(
        name = "uk_subject_institution_name",
        columnNames = {"institution_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "subject_id", updatable = false, nullable = false)
    private UUID subjectId;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", nullable = false)
    private Career career;

    /**
     * Derived value, not persisted: a subject is basic when it belongs to the
     * shared career of its institution. JPA access is field-based (@Id lives on
     * a field), so this getter is not mapped.
     */
    public boolean isBasic() {
        return career != null && career.getType() == CareerType.SHARED;
    }
}
