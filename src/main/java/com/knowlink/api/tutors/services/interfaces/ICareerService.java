package com.knowlink.api.tutors.services.interfaces;

import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;

import java.util.List;
import java.util.UUID;

public interface ICareerService {

    Career findRegularByNameAndInstitutionOrThrowException(String name, UUID institutionId);

    List<Career> findRegularByInstitution(UUID institutionId);

    Career findOrCreateSharedCareer(Institution institution);
}
