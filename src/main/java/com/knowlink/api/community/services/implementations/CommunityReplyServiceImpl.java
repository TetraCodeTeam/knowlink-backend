package com.knowlink.api.community.services.implementations;

import com.knowlink.api.community.controllers.requests.CreateReplyRequest;
import com.knowlink.api.community.controllers.requests.UpdateReplyRequest;
import com.knowlink.api.community.controllers.responses.CommunityReplyResponse;
import com.knowlink.api.community.controllers.responses.HelpfulMarkResponse;
import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.mappers.CommunityMapper;
import com.knowlink.api.community.data.models.CommunityAttachment;
import com.knowlink.api.community.data.models.CommunityPost;
import com.knowlink.api.community.data.models.CommunityReply;
import com.knowlink.api.community.data.models.CommunityReplyHelpful;
import com.knowlink.api.community.events.CommunityReplyCreatedEvent;
import com.knowlink.api.community.repositories.ICommunityPostRepository;
import com.knowlink.api.community.repositories.ICommunityReplyHelpfulRepository;
import com.knowlink.api.community.repositories.ICommunityReplyRepository;
import com.knowlink.api.community.services.interfaces.ICommunityAttachmentService;
import com.knowlink.api.community.services.interfaces.ICommunityReplyService;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.UnprocessableEntityException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.materials.exception.ResourceAccessDeniedException;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityReplyServiceImpl implements ICommunityReplyService {

    private static final String UNKNOWN_AUTHOR_NAME = "Alguien";

    private final ICommunityPostRepository postRepository;
    private final ICommunityReplyRepository replyRepository;
    private final ICommunityReplyHelpfulRepository helpfulRepository;
    private final IUserRepository userRepository;
    private final ICommunityAttachmentService attachmentService;
    private final CommunityMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional
    public CommunityReplyResponse create(UUID userId, Role activeRole, UUID postId, CreateReplyRequest request,
            MultipartFile file) {
        if (activeRole != Role.STUDENT && activeRole != Role.TUTOR) {
            throw new AccessDeniedException("Only students and tutors can reply in the community");
        }
        CommunityPost post = findActivePost(postId);
        if (post.getStatus() == PostStatus.RESOLVED) {
            throw new UnprocessableEntityException(
                    "POST_CLOSED",
                    "Esta publicación ya fue resuelta y no acepta nuevas respuestas.",
                    "Post " + postId + " is resolved and does not accept replies");
        }

        // El autor no puede responderse a sí mismo hasta que otra persona participe del hilo.
        if (post.getAuthor().getUserId().equals(userId)
                && !replyRepository.existsByPost_PostIdAndAuthor_UserIdNotAndDeletedAtIsNull(postId, userId)) {
            throw new UnprocessableEntityException(
                    "OWN_POST_WITHOUT_REPLIES",
                    "Podrás responder cuando alguien más conteste tu publicación.",
                    "Author " + userId + " tried to reply own post " + postId + " with no replies from others");
        }

        String content = requireContent(request.content());
        attachmentService.validate(file);
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND",
                        "No encontramos tu usuario.",
                        "User not found for id: " + userId));

        // La tarjeta de vinculación solo existe en respuestas de un tutor; por defecto se incluye.
        boolean includeBookingCard = activeRole == Role.TUTOR
                && (request.includeBookingCard() == null || request.includeBookingCard());
        String bookingQuestion = includeBookingCard ? blankToNull(request.bookingCardQuestion()) : null;

        CommunityAttachment attachment = attachmentService.store(file, userId);
        try {
            CommunityReply saved = replyRepository.save(CommunityReply.builder()
                    .post(post)
                    .author(author)
                    .authorRole(activeRole)
                    .content(content)
                    .attachment(attachment)
                    .includeBookingCard(includeBookingCard)
                    .bookingCardQuestion(bookingQuestion)
                    .build());

            eventPublisher.publishEvent(new CommunityReplyCreatedEvent(
                    saved.getReplyId(),
                    postId,
                    post.getTitle(),
                    post.getAuthor().getUserId(),
                    post.getAuthor().getRole(),
                    userId,
                    author.getFullName() == null || author.getFullName().isBlank()
                            ? UNKNOWN_AUTHOR_NAME
                            : author.getFullName()));
            log.info("Community reply {} created on post {} by user {}", saved.getReplyId(), postId, userId);
            return mapper.toReplyResponse(saved, postId, post.getFeaturedReplyId(), userId);
        } catch (RuntimeException ex) {
            attachmentService.deleteQuietly(attachment);
            throw ex;
        }
    }

    @Override
    @Transactional
    public CommunityReplyResponse update(UUID userId, UUID postId, UUID replyId, UpdateReplyRequest request,
            MultipartFile file) {
        CommunityPost post = findActivePost(postId);
        CommunityReply reply = findActiveReply(postId, replyId);
        assertAuthor(reply, userId, "Solo podés editar tus propias respuestas.");

        String content = requireContent(request.content());
        attachmentService.validate(file);

        // Un archivo nuevo reemplaza al actual; sin archivo, removeAttachment lo quita; si no, se conserva.
        CommunityAttachment previous = reply.getAttachment();
        CommunityAttachment newAttachment = attachmentService.store(file, userId);
        boolean removeCurrent = Boolean.TRUE.equals(request.removeAttachment());
        boolean attachmentChanged = newAttachment != null || (removeCurrent && previous != null);

        try {
            reply.setContent(content);
            if (newAttachment != null) {
                reply.setAttachment(newAttachment);
            } else if (removeCurrent) {
                reply.setAttachment(null);
            }
            reply.setEditedAt(LocalDateTime.now(clock));
            CommunityReply saved = replyRepository.saveAndFlush(reply);

            // El archivo anterior se borra del storage recién cuando la nueva versión quedó guardada.
            if (attachmentChanged) {
                attachmentService.deleteQuietly(previous);
            }
            log.info("Community reply {} edited by user {}", replyId, userId);
            return mapper.toReplyResponse(saved, postId, post.getFeaturedReplyId(), userId);
        } catch (RuntimeException ex) {
            attachmentService.deleteQuietly(newAttachment);
            throw ex;
        }
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID postId, UUID replyId) {
        findActivePost(postId);
        CommunityReply reply = findActiveReply(postId, replyId);
        assertAuthor(reply, userId, "Solo podés eliminar tus propias respuestas.");

        reply.setDeletedAt(LocalDateTime.now(clock));
        replyRepository.save(reply);
        attachmentService.deleteQuietly(reply.getAttachment());
        log.info("Community reply {} deleted by user {}", replyId, userId);
    }

    @Override
    @Transactional
    public HelpfulMarkResponse markHelpful(UUID userId, UUID postId, UUID replyId) {
        findActivePost(postId);
        CommunityReply reply = findActiveReply(postId, replyId);
        if (reply.getAuthor().getUserId().equals(userId)) {
            throw new UnprocessableEntityException(
                    "OWN_REPLY_NOT_MARKABLE",
                    "No podés marcar tu propia respuesta como útil.",
                    "User " + userId + " tried to mark own reply " + replyId);
        }
        if (!helpfulRepository.existsByReply_ReplyIdAndUser_UserId(replyId, userId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "USER_NOT_FOUND",
                            "No encontramos tu usuario.",
                            "User not found for id: " + userId));
            helpfulRepository.save(CommunityReplyHelpful.builder().reply(reply).user(user).build());
        }
        return new HelpfulMarkResponse(replyId, helpfulRepository.countByReply_ReplyId(replyId), true);
    }

    @Override
    @Transactional
    public HelpfulMarkResponse unmarkHelpful(UUID userId, UUID postId, UUID replyId) {
        findActivePost(postId);
        findActiveReply(postId, replyId);
        helpfulRepository.findByReply_ReplyIdAndUser_UserId(replyId, userId).ifPresent(helpfulRepository::delete);
        return new HelpfulMarkResponse(replyId, helpfulRepository.countByReply_ReplyId(replyId), false);
    }

    private CommunityPost findActivePost(UUID postId) {
        return postRepository.findByPostIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "POST_NOT_FOUND",
                        "La publicación no existe o fue eliminada.",
                        "Community post not found for id: " + postId));
    }

    private CommunityReply findActiveReply(UUID postId, UUID replyId) {
        return replyRepository.findByReplyIdAndPost_PostIdAndDeletedAtIsNull(replyId, postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REPLY_NOT_FOUND",
                        "La respuesta no existe o fue eliminada.",
                        "Community reply " + replyId + " not found in post " + postId));
    }

    private void assertAuthor(CommunityReply reply, UUID userId, String message) {
        if (!reply.getAuthor().getUserId().equals(userId)) {
            throw new ResourceAccessDeniedException(message);
        }
    }

    private String requireContent(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("La respuesta no puede estar vacía.");
        }
        return trimmed;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
