package com.knowlink.api.community.services.interfaces;

import com.knowlink.api.community.controllers.requests.CreatePostRequest;
import com.knowlink.api.community.controllers.requests.UpdatePostRequest;
import com.knowlink.api.community.controllers.responses.CommunityCareerResponse;
import com.knowlink.api.community.controllers.responses.CommunityFeedCountsResponse;
import com.knowlink.api.community.controllers.responses.CommunityPostDetailResponse;
import com.knowlink.api.community.controllers.responses.CommunityPostResponse;
import com.knowlink.api.community.controllers.responses.CommunitySubjectResponse;
import com.knowlink.api.community.data.filters.CommunityFeedFilter;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.responses.PagedResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ICommunityPostService {

    CommunityPostResponse create(UUID userId, Role activeRole, CreatePostRequest request, MultipartFile file);

    PagedResponse<CommunityPostResponse> getFeed(UUID userId, CommunityFeedFilter filter, int page, int size);

    /** Publicaciones por tipo de consulta con los filtros aplicados, ignorando el propio filtro de tipo. */
    CommunityFeedCountsResponse getFeedCounts(CommunityFeedFilter filter);

    /**
     * Publicaciones propias, de la más reciente a la más antigua.
     *
     * @param dateFrom primer día incluido; {@code null} = sin límite inferior
     * @param dateTo   último día incluido; {@code null} = sin límite superior
     */
    PagedResponse<CommunityPostResponse> getMyPosts(UUID userId, LocalDate dateFrom, LocalDate dateTo, int page,
            int size);

    CommunityPostDetailResponse getDetail(UUID userId, UUID postId);

    CommunityPostResponse update(UUID userId, UUID postId, UpdatePostRequest request, MultipartFile file);

    void delete(UUID userId, UUID postId);

    CommunityPostResponse resolve(UUID userId, UUID postId);

    /** Con careerId: materias de esa carrera más las compartidas de su institución; sin él, el catálogo de la institución del usuario. */
    List<CommunitySubjectResponse> getSubjects(UUID userId, UUID careerId);

    /** Carreras regulares de la institución del usuario; sin perfil, lista vacía. */
    List<CommunityCareerResponse> getCareers(UUID userId);

    /** Soft-deletes every active post of the author and returns attachment storage paths (US-24). */
    List<String> softDeleteAllByAuthor(UUID authorId);
}