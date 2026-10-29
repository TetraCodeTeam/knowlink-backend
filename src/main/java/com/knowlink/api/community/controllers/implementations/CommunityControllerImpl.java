package com.knowlink.api.community.controllers.implementations;

import com.knowlink.api.community.controllers.interfaces.ICommunityController;
import com.knowlink.api.community.controllers.requests.CreatePostRequest;
import com.knowlink.api.community.controllers.requests.CreateReplyRequest;
import com.knowlink.api.community.controllers.requests.UpdatePostRequest;
import com.knowlink.api.community.controllers.requests.UpdateReplyRequest;
import com.knowlink.api.community.controllers.responses.CommunityPostDetailResponse;
import com.knowlink.api.community.controllers.responses.CommunityPostResponse;
import com.knowlink.api.community.controllers.responses.CommunityReplyResponse;
import com.knowlink.api.community.controllers.responses.CommunitySubjectResponse;
import com.knowlink.api.community.controllers.responses.HelpfulMarkResponse;
import com.knowlink.api.community.services.interfaces.ICommunityPostService;
import com.knowlink.api.community.services.interfaces.ICommunityReplyService;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.shared.responses.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CommunityControllerImpl implements ICommunityController {

    private final ICommunityPostService postService;
    private final ICommunityReplyService replyService;

    @Override
    public PagedResponse<CommunityPostResponse> getFeed(UserPrincipal principal, int page, int size) {
        return postService.getFeed(userId(principal), page, size);
    }

    @Override
    public PagedResponse<CommunityPostResponse> getMyPosts(UserPrincipal principal, LocalDate date, int page,
            int size) {
        return postService.getMyPosts(userId(principal), date, page, size);
    }

    @Override
    public CommunityPostDetailResponse getDetail(UserPrincipal principal, UUID postId) {
        return postService.getDetail(userId(principal), postId);
    }

    @Override
    public CommunityPostResponse createPost(UserPrincipal principal, CreatePostRequest request, MultipartFile file) {
        return postService.create(userId(principal), principal.getUser().getRole(), request, file);
    }

    @Override
    public CommunityPostResponse updatePost(UserPrincipal principal, UUID postId, UpdatePostRequest request,
            MultipartFile file) {
        return postService.update(userId(principal), postId, request, file);
    }

    @Override
    public void deletePost(UserPrincipal principal, UUID postId) {
        postService.delete(userId(principal), postId);
    }

    @Override
    public CommunityPostResponse resolvePost(UserPrincipal principal, UUID postId) {
        return postService.resolve(userId(principal), postId);
    }

    @Override
    public CommunityReplyResponse createReply(UserPrincipal principal, UUID postId, CreateReplyRequest request,
            MultipartFile file) {
        return replyService.create(userId(principal), principal.getUser().getRole(), postId, request, file);
    }

    @Override
    public CommunityReplyResponse updateReply(UserPrincipal principal, UUID postId, UUID replyId,
            UpdateReplyRequest request, MultipartFile file) {
        return replyService.update(userId(principal), postId, replyId, request, file);
    }

    @Override
    public void deleteReply(UserPrincipal principal, UUID postId, UUID replyId) {
        replyService.delete(userId(principal), postId, replyId);
    }

    @Override
    public HelpfulMarkResponse markHelpful(UserPrincipal principal, UUID postId, UUID replyId) {
        return replyService.markHelpful(userId(principal), postId, replyId);
    }

    @Override
    public HelpfulMarkResponse unmarkHelpful(UserPrincipal principal, UUID postId, UUID replyId) {
        return replyService.unmarkHelpful(userId(principal), postId, replyId);
    }

    @Override
    public List<CommunitySubjectResponse> getSubjects() {
        return postService.getSubjects();
    }

    private static UUID userId(UserPrincipal principal) {
        return principal.getUser().getUserId();
    }
}
