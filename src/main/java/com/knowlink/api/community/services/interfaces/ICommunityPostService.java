package com.knowlink.api.community.services.interfaces;

import com.knowlink.api.community.controllers.requests.CreatePostRequest;
import com.knowlink.api.community.controllers.requests.UpdatePostRequest;
import com.knowlink.api.community.controllers.responses.CommunityPostDetailResponse;
import com.knowlink.api.community.controllers.responses.CommunityPostResponse;
import com.knowlink.api.community.controllers.responses.CommunitySubjectResponse;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.responses.PagedResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ICommunityPostService {

    CommunityPostResponse create(UUID userId, Role activeRole, CreatePostRequest request, MultipartFile file);

    PagedResponse<CommunityPostResponse> getFeed(UUID userId, int page, int size);

    PagedResponse<CommunityPostResponse> getMyPosts(UUID userId, LocalDate date, int page, int size);

    CommunityPostDetailResponse getDetail(UUID userId, UUID postId);

    CommunityPostResponse update(UUID userId, UUID postId, UpdatePostRequest request, MultipartFile file);

    void delete(UUID userId, UUID postId);

    CommunityPostResponse resolve(UUID userId, UUID postId);

    List<CommunitySubjectResponse> getSubjects();
}
