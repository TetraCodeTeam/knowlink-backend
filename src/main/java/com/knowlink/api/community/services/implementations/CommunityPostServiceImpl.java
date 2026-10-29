package com.knowlink.api.community.services.implementations;

import com.knowlink.api.community.controllers.requests.CreatePostRequest;
import com.knowlink.api.community.controllers.requests.UpdatePostRequest;
import com.knowlink.api.community.controllers.responses.CommunityPostDetailResponse;
import com.knowlink.api.community.controllers.responses.CommunityPostResponse;
import com.knowlink.api.community.controllers.responses.CommunityReplyResponse;
import com.knowlink.api.community.controllers.responses.CommunitySubjectResponse;
import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.enums.PostType;
import com.knowlink.api.community.data.mappers.CommunityMapper;
import com.knowlink.api.community.data.models.CommunityAttachment;
import com.knowlink.api.community.data.models.CommunityPost;
import com.knowlink.api.community.data.models.CommunityReply;
import com.knowlink.api.community.data.projections.ReplyHelpfulCount;
import com.knowlink.api.community.repositories.ICommunityPostRepository;
import com.knowlink.api.community.repositories.ICommunityReplyRepository;
import com.knowlink.api.community.services.interfaces.ICommunityAttachmentService;
import com.knowlink.api.community.services.interfaces.ICommunityPostService;
import com.knowlink.api.community.utils.CommunityConstants;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.UnprocessableEntityException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.materials.exception.ResourceAccessDeniedException;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.shared.responses.PagedResponse;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityPostServiceImpl implements ICommunityPostService {

    private static final Sort FEED_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));

    private final ICommunityPostRepository postRepository;
    private final ICommunityReplyRepository replyRepository;
    private final IUserRepository userRepository;
    private final ISubjectRepository subjectRepository;
    private final ICommunityAttachmentService attachmentService;
    private final CommunityMapper mapper;
    private final Clock clock;

    @Override
    @Transactional
    public CommunityPostResponse create(UUID userId, Role activeRole, CreatePostRequest request,
            MultipartFile file) {
        if (activeRole != Role.STUDENT && activeRole != Role.TUTOR) {
            throw new AccessDeniedException("Only students and tutors can publish in the community");
        }

        String title = request.title().trim();
        String content = request.content().trim();
        validateTexts(request.type(), title, content);
        attachmentService.validate(file);

        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SUBJECT_NOT_FOUND",
                        "La materia seleccionada no existe.",
                        "Subject not found for id: " + request.subjectId()));
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND",
                        "No encontramos tu usuario.",
                        "User not found for id: " + userId));

        CommunityAttachment attachment = attachmentService.store(file, userId);
        try {
            CommunityPost saved = postRepository.save(CommunityPost.builder()
                    .author(author)
                    .authorRole(activeRole)
                    .type(request.type())
                    .status(PostStatus.OPEN)
                    .subject(subject)
                    .title(title)
                    .content(content)
                    .attachment(attachment)
                    .build());
            log.info("Community post {} ({}) created by user {}", saved.getPostId(), saved.getType(), userId);
            return mapper.toPostResponse(saved, userId);
        } catch (RuntimeException ex) {
            attachmentService.deleteQuietly(attachment);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CommunityPostResponse> getFeed(UUID userId, int page, int size) {
        Pageable pageable = pageable(page, size);
        return toPagedResponse(postRepository.findByDeletedAtIsNull(pageable), pageable, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CommunityPostResponse> getMyPosts(UUID userId, LocalDate date, int page, int size) {
        Pageable pageable = pageable(page, size);
        Page<CommunityPost> posts = date == null
                ? postRepository.findByAuthor_UserIdAndDeletedAtIsNull(userId, pageable)
                : postRepository.findByAuthorAndCreatedBetween(
                        userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), pageable);
        return toPagedResponse(posts, pageable, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityPostDetailResponse getDetail(UUID userId, UUID postId) {
        CommunityPost post = findActivePost(postId);
        List<CommunityReply> replies = replyRepository.findByPost_PostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId);

        CommunityPostResponse postResponse = mapper.toPostResponse(post, userId);
        UUID featuredReplyId = postResponse.featuredReply() == null ? null : postResponse.featuredReply().replyId();
        List<CommunityReplyResponse> replyResponses = mapper.toReplyResponses(replies, postId, featuredReplyId, userId);
        return new CommunityPostDetailResponse(postResponse, replyResponses);
    }

    @Override
    @Transactional
    public CommunityPostResponse update(UUID userId, UUID postId, UpdatePostRequest request,
            MultipartFile file) {
        CommunityPost post = findActivePost(postId);
        assertAuthor(post, userId, "Solo podés editar tus propias publicaciones.");
        if (post.getStatus() == PostStatus.RESOLVED) {
            throw new UnprocessableEntityException(
                    "POST_CLOSED",
                    "Una publicación resuelta ya no se puede editar.",
                    "Post " + postId + " is resolved and cannot be edited");
        }

        String title = request.title().trim();
        String content = request.content().trim();
        validateTexts(post.getType(), title, content);
        attachmentService.validate(file);

        // Un archivo nuevo reemplaza al actual; sin archivo, removeAttachment lo quita; si no, se conserva.
        CommunityAttachment previous = post.getAttachment();
        CommunityAttachment newAttachment = attachmentService.store(file, userId);
        boolean removeCurrent = Boolean.TRUE.equals(request.removeAttachment());
        boolean attachmentChanged = newAttachment != null || (removeCurrent && previous != null);

        try {
            post.setTitle(title);
            post.setContent(content);
            if (newAttachment != null) {
                post.setAttachment(newAttachment);
            } else if (removeCurrent) {
                post.setAttachment(null);
            }
            post.setEditedAt(LocalDateTime.now(clock));
            CommunityPost saved = postRepository.saveAndFlush(post);

            // El archivo anterior se borra del storage recién cuando la nueva versión quedó guardada.
            if (attachmentChanged) {
                attachmentService.deleteQuietly(previous);
            }
            log.info("Community post {} edited by user {}", postId, userId);
            return mapper.toPostResponse(saved, userId);
        } catch (RuntimeException ex) {
            attachmentService.deleteQuietly(newAttachment);
            throw ex;
        }
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID postId) {
        CommunityPost post = findActivePost(postId);
        assertAuthor(post, userId, "Solo podés eliminar tus propias publicaciones.");

        post.setDeletedAt(LocalDateTime.now(clock));
        postRepository.save(post);
        attachmentService.deleteQuietly(post.getAttachment());
        log.info("Community post {} deleted by user {}", postId, userId);
    }

    @Override
    @Transactional
    public CommunityPostResponse resolve(UUID userId, UUID postId) {
        CommunityPost post = findActivePost(postId);
        assertAuthor(post, userId, "Solo podés marcar como resuelta tu propia publicación.");

        if (post.getType() != PostType.HELP_REQUEST) {
            throw new UnprocessableEntityException(
                    "POST_NOT_RESOLVABLE",
                    "Solo los pedidos de ayuda pueden marcarse como resueltos.",
                    "Post " + postId + " is not a help request");
        }
        if (post.getStatus() == PostStatus.RESOLVED) {
            throw new UnprocessableEntityException(
                    "POST_ALREADY_RESOLVED",
                    "Esta publicación ya fue marcada como resuelta.",
                    "Post " + postId + " is already resolved");
        }

        post.setFeaturedReplyId(pickFeaturedReply(post).orElse(null));
        post.setStatus(PostStatus.RESOLVED);
        post.setResolvedAt(LocalDateTime.now(clock));
        CommunityPost saved = postRepository.save(post);
        log.info("Community post {} resolved by user {} with featured reply {}",
                postId, userId, saved.getFeaturedReplyId());
        return mapper.toPostResponse(saved, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunitySubjectResponse> getSubjects() {
        return subjectRepository.findAll(Sort.by("name")).stream()
                .map(subject -> new CommunitySubjectResponse(subject.getSubjectId(), subject.getName()))
                .toList();
    }

    /**
     * La respuesta con más marcas "Esto me sirvió" (ante empate, la más antigua). Las respuestas del propio autor
     * de la publicación y las que no recibieron ninguna marca no pueden destacarse.
     */
    private Optional<UUID> pickFeaturedReply(CommunityPost post) {
        List<CommunityReply> replies =
                replyRepository.findByPost_PostIdAndDeletedAtIsNullOrderByCreatedAtAsc(post.getPostId());
        if (replies.isEmpty()) {
            return Optional.empty();
        }
        Map<UUID, Long> helpfulByReply = replyRepository.countHelpfulByPostIds(List.of(post.getPostId())).stream()
                .collect(Collectors.toMap(ReplyHelpfulCount::getReplyId, ReplyHelpfulCount::getTotal));
        UUID postAuthorId = post.getAuthor().getUserId();

        return replies.stream()
                .filter(reply -> !reply.getAuthor().getUserId().equals(postAuthorId))
                .filter(reply -> helpfulByReply.getOrDefault(reply.getReplyId(), 0L) > 0)
                .max(Comparator
                        .comparingLong((CommunityReply reply) -> helpfulByReply.get(reply.getReplyId()))
                        .thenComparing(Comparator.comparing(CommunityReply::getCreatedAt).reversed()))
                .map(CommunityReply::getReplyId);
    }

    private void validateTexts(PostType type, String title, String content) {
        if (title.isEmpty()) {
            throw new ValidationException("El título es obligatorio.");
        }
        if (content.isEmpty()) {
            throw new ValidationException("La descripción es obligatoria.");
        }
        int maxContent = type == PostType.HELP_REQUEST
                ? CommunityConstants.HELP_REQUEST_MAX_CONTENT_LENGTH
                : CommunityConstants.SHARED_CONTENT_MAX_CONTENT_LENGTH;
        if (content.length() > maxContent) {
            throw new ValidationException("La descripción no puede superar los " + maxContent + " caracteres.");
        }
    }

    private CommunityPost findActivePost(UUID postId) {
        return postRepository.findByPostIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "POST_NOT_FOUND",
                        "La publicación no existe o fue eliminada.",
                        "Community post not found for id: " + postId));
    }

    private void assertAuthor(CommunityPost post, UUID userId, String message) {
        if (!post.getAuthor().getUserId().equals(userId)) {
            throw new ResourceAccessDeniedException(message);
        }
    }

    private Pageable pageable(int page, int size) {
        int safeSize = size <= 0 ? CommunityConstants.DEFAULT_PAGE_SIZE : Math.min(size, CommunityConstants.MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize, FEED_SORT);
    }

    private PagedResponse<CommunityPostResponse> toPagedResponse(Page<CommunityPost> posts, Pageable pageable,
            UUID userId) {
        List<CommunityPostResponse> content = mapper.toPostResponses(posts.getContent(), userId);
        return PagedResponse.from(new PageImpl<>(content, pageable, posts.getTotalElements()));
    }
}
