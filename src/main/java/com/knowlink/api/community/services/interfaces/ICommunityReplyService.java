package com.knowlink.api.community.services.interfaces;

import com.knowlink.api.community.controllers.requests.CreateReplyRequest;
import com.knowlink.api.community.controllers.requests.UpdateReplyRequest;
import com.knowlink.api.community.controllers.responses.CommunityReplyResponse;
import com.knowlink.api.community.controllers.responses.HelpfulMarkResponse;
import com.knowlink.api.security.enums.Role;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ICommunityReplyService {

    CommunityReplyResponse create(UUID userId, Role activeRole, UUID postId, CreateReplyRequest request,
            MultipartFile file);

    CommunityReplyResponse update(UUID userId, UUID postId, UUID replyId, UpdateReplyRequest request,
            MultipartFile file);

    void delete(UUID userId, UUID postId, UUID replyId);

    HelpfulMarkResponse markHelpful(UUID userId, UUID postId, UUID replyId);

    HelpfulMarkResponse unmarkHelpful(UUID userId, UUID postId, UUID replyId);
}
