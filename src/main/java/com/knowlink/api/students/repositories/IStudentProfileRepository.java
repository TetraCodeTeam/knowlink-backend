package com.knowlink.api.students.repositories;

import com.knowlink.api.students.data.models.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IStudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    boolean existsByUser_UserId(UUID userId);
}