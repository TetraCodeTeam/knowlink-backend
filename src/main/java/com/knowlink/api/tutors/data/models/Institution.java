package com.knowlink.api.tutors.data.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "institution")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "institution_id", updatable = false, nullable = false)
    private UUID institutionId;

    @Column(name = "name", nullable = false, unique = true)
    private String name;
}
