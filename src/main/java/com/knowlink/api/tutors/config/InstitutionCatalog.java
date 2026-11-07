package com.knowlink.api.tutors.config;

import java.util.List;
import java.util.Map;

/**
 * MVP catalog for one institution: its regular careers, its shared subjects and
 * the subjects that belong to each regular career.
 */
public record InstitutionCatalog(
        List<String> careers,
        List<String> sharedSubjects,
        Map<String, List<String>> subjectsByCareer
) {
}
