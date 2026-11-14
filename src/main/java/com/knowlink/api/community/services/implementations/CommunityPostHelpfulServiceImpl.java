package com.knowlink.api.community.services.implementations;

import com.knowlink.api.community.controllers.responses.PostHelpfulMarkResponse;
import com.knowlink.api.community.data.enums.PostType;
import com.knowlink.api.community.data.models.CommunityPost;
import com.knowlink.api.community.data.models.CommunityPostHelpful;
import com.knowlink.api.community.repositories.ICommunityPostHelpfulRepository;
import com.knowlink.api.community.repositories.ICommunityPostRepository;
import com.knowlink.api.community.services.interfaces.ICommunityPostHelpfulService;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.UnprocessableEntityException;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityPostHelpfulServiceImpl implements ICommunityPostHelpfulService {

    private final ICommunityPostRepository postRepository;
    private final ICommunityPostHelpfulRepository helpfulRepository;
    private final IUserRepository userRepository;

    @Override
    @Transactional
    public PostHelpfulMarkResponse markHelpful(UUID userId, UUID postId) {
        CommunityPost post = findActivePost(postId);
        // Las consultas (HELP_REQUEST) no se marcan: se marcan sus respuestas, y de ahí sale la destacada.
        if (post.getType() != PostType.SHARED_CONTENT) {
            throw new UnprocessableEntityException(
                    "POST_NOT_MARKABLE",
                    "Solo los aportes se pueden marcar como útiles.",
                    "User " + userId + " tried to mark post " + postId + " of type " + post.getType());
        }
        if (post.getAuthor().getUserId().equals(userId)) {
            throw new UnprocessableEntityException(
                    "OWN_POST_NOT_MARKABLE",
                    "No podés marcar tu propio aporte como útil.",
                    "User " + userId + " tried to mark own post " + postId);
        }
        if (!helpfulRepository.existsByPost_PostIdAndUser_UserId(postId, userId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "USER_NOT_FOUND",
                            "No encontramos tu usuario.",
                            "User not found for id: " + userId));
            helpfulRepository.save(CommunityPostHelpful.builder().post(post).user(user).build());
        }
        return new PostHelpfulMarkResponse(postId, helpfulRepository.countByPost_PostId(postId), true);
    }

    @Override
    @Transactional
    public PostHelpfulMarkResponse unmarkHelpful(UUID userId, UUID postId) {
        findActivePost(postId);
        helpfulRepository.findByPost_PostIdAndUser_UserId(postId, userId).ifPresent(helpfulRepository::delete);
        return new PostHelpfulMarkResponse(postId, helpfulRepository.countByPost_PostId(postId), false);
    }

    private CommunityPost findActivePost(UUID postId) {
        return postRepository.findByPostIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "POST_NOT_FOUND",
                        "La publicación no existe o fue eliminada.",
                        "Community post not found for id: " + postId));
    }
}
