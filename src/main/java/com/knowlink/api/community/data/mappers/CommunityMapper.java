package com.knowlink.api.community.data.mappers;

import com.knowlink.api.community.controllers.responses.BookingCardResponse;
import com.knowlink.api.community.controllers.responses.CommunityAuthorResponse;
import com.knowlink.api.community.controllers.responses.CommunityPostResponse;
import com.knowlink.api.community.controllers.responses.CommunityReplyResponse;
import com.knowlink.api.community.controllers.responses.CommunitySubjectResponse;
import com.knowlink.api.community.controllers.responses.FeaturedReplyResponse;
import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.models.CommunityPost;
import com.knowlink.api.community.data.models.CommunityReply;
import com.knowlink.api.community.data.projections.PostReplyCount;
import com.knowlink.api.community.data.projections.ReplyHelpfulCount;
import com.knowlink.api.community.repositories.ICommunityReplyHelpfulRepository;
import com.knowlink.api.community.repositories.ICommunityReplyRepository;
import com.knowlink.api.community.services.interfaces.ICommunityAttachmentService;
import com.knowlink.api.community.utils.CommunityConstants;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arma las respuestas del módulo resolviendo en lote (una consulta por dato, no por publicación) los autores,
 * los conteos de respuestas y las marcas "Esto me sirvió". Debe invocarse dentro de una transacción.
 */
@Component
@RequiredArgsConstructor
public class CommunityMapper {

    private static final String UNKNOWN_AUTHOR_NAME = "Usuario";

    private final ITutorProfileRepository tutorProfileRepository;
    private final IStudentProfileRepository studentProfileRepository;
    private final ICommunityReplyRepository replyRepository;
    private final ICommunityReplyHelpfulRepository helpfulRepository;
    private final ICommunityAttachmentService attachmentService;

    private record AuthorRef(User user, Role role) {
    }

    private record AuthorKey(UUID userId, Role role) {
    }

    // ── Publicaciones ────────────────────────────────────────────────────────

    public CommunityPostResponse toPostResponse(CommunityPost post, UUID viewerId) {
        return toPostResponses(List.of(post), viewerId).get(0);
    }

    public List<CommunityPostResponse> toPostResponses(List<CommunityPost> posts, UUID viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(CommunityPost::getPostId).toList();

        Map<UUID, Long> replyCounts = replyRepository.countRepliesByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostReplyCount::getPostId, PostReplyCount::getTotal));

        Map<UUID, Long> repliesFromOthers = replyRepository.countRepliesFromOthersByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostReplyCount::getPostId, PostReplyCount::getTotal));

        List<ReplyHelpfulCount> helpfulRows = replyRepository.countHelpfulByPostIds(postIds);
        Map<UUID, Long> helpfulByReply = helpfulRows.stream()
                .collect(Collectors.toMap(ReplyHelpfulCount::getReplyId, ReplyHelpfulCount::getTotal));
        Map<UUID, Long> bestHelpfulByPost = new HashMap<>();
        helpfulRows.forEach(row -> bestHelpfulByPost.merge(row.getPostId(), row.getTotal(), Math::max));

        Set<UUID> featuredIds = posts.stream()
                .map(CommunityPost::getFeaturedReplyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, CommunityReply> featuredReplies = featuredIds.isEmpty()
                ? Map.of()
                : replyRepository.findByReplyIdInAndDeletedAtIsNull(featuredIds).stream()
                        .collect(Collectors.toMap(CommunityReply::getReplyId, Function.identity()));

        List<AuthorRef> refs = new ArrayList<>();
        posts.forEach(post -> refs.add(new AuthorRef(post.getAuthor(), post.getAuthorRole())));
        featuredReplies.values().forEach(reply -> refs.add(new AuthorRef(reply.getAuthor(), reply.getAuthorRole())));
        Map<AuthorKey, CommunityAuthorResponse> authors = resolveAuthors(refs);

        return posts.stream().map(post -> {
            CommunityReply featured = post.getStatus() == PostStatus.RESOLVED && post.getFeaturedReplyId() != null
                    ? featuredReplies.get(post.getFeaturedReplyId())
                    : null;
            long helpfulCount = featured != null
                    ? helpfulByReply.getOrDefault(featured.getReplyId(), 0L)
                    : bestHelpfulByPost.getOrDefault(post.getPostId(), 0L);
            FeaturedReplyResponse featuredResponse = featured == null ? null : new FeaturedReplyResponse(
                    featured.getReplyId(),
                    authors.get(new AuthorKey(featured.getAuthor().getUserId(), featured.getAuthorRole())),
                    excerpt(featured.getContent()),
                    helpfulCount,
                    featured.getCreatedAt());

            boolean ownedByViewer = post.getAuthor().getUserId().equals(viewerId);

            return new CommunityPostResponse(
                    post.getPostId(),
                    post.getType(),
                    post.getStatus(),
                    new CommunitySubjectResponse(post.getSubject().getSubjectId(), post.getSubject().getName()),
                    post.getTitle(),
                    post.getContent(),
                    attachmentService.toResponse(post.getAttachment()),
                    authors.get(new AuthorKey(post.getAuthor().getUserId(), post.getAuthorRole())),
                    post.getCreatedAt(),
                    post.getResolvedAt(),
                    replyCounts.getOrDefault(post.getPostId(), 0L).intValue(),
                    helpfulCount,
                    featuredResponse,
                    ownedByViewer,
                    post.getEditedAt() != null,
                    post.getStatus() == PostStatus.OPEN
                            && (!ownedByViewer || repliesFromOthers.getOrDefault(post.getPostId(), 0L) > 0));
        }).toList();
    }

    // ── Respuestas ───────────────────────────────────────────────────────────

    public CommunityReplyResponse toReplyResponse(CommunityReply reply, UUID postId, UUID featuredReplyId,
            UUID viewerId) {
        return toReplyResponses(List.of(reply), postId, featuredReplyId, viewerId).get(0);
    }

    /** Respuestas de un hilo: la destacada primero y el resto por antigüedad. */
    public List<CommunityReplyResponse> toReplyResponses(List<CommunityReply> replies, UUID postId,
            UUID featuredReplyId, UUID viewerId) {
        if (replies.isEmpty()) {
            return List.of();
        }
        List<UUID> replyIds = replies.stream().map(CommunityReply::getReplyId).toList();
        Map<UUID, Long> helpfulByReply = replyRepository.countHelpfulByPostIds(List.of(postId)).stream()
                .collect(Collectors.toMap(ReplyHelpfulCount::getReplyId, ReplyHelpfulCount::getTotal));
        Set<UUID> markedByViewer = new HashSet<>(helpfulRepository.findMarkedReplyIds(viewerId, replyIds));
        Map<AuthorKey, CommunityAuthorResponse> authors = resolveAuthors(replies.stream()
                .map(reply -> new AuthorRef(reply.getAuthor(), reply.getAuthorRole()))
                .toList());

        return replies.stream()
                .sorted(Comparator
                        .comparing((CommunityReply reply) -> !reply.getReplyId().equals(featuredReplyId))
                        .thenComparing(CommunityReply::getCreatedAt)
                        .thenComparing(CommunityReply::getReplyId))
                .map(reply -> {
                    CommunityAuthorResponse author =
                            authors.get(new AuthorKey(reply.getAuthor().getUserId(), reply.getAuthorRole()));
                    return buildReplyResponse(reply, postId, author,
                            helpfulByReply.getOrDefault(reply.getReplyId(), 0L),
                            markedByViewer.contains(reply.getReplyId()),
                            reply.getReplyId().equals(featuredReplyId),
                            viewerId);
                })
                .toList();
    }

    private CommunityReplyResponse buildReplyResponse(CommunityReply reply, UUID postId,
            CommunityAuthorResponse author, long helpfulCount, boolean markedByViewer, boolean featured,
            UUID viewerId) {
        BookingCardResponse bookingCard = reply.isIncludeBookingCard() && author != null && author.canBookClass()
                ? new BookingCardResponse(resolveBookingQuestion(reply.getBookingCardQuestion()))
                : null;
        return new CommunityReplyResponse(
                reply.getReplyId(),
                postId,
                author,
                reply.getContent(),
                attachmentService.toResponse(reply.getAttachment()),
                helpfulCount,
                markedByViewer,
                featured,
                bookingCard,
                reply.getCreatedAt(),
                reply.getEditedAt() != null,
                reply.getAuthor().getUserId().equals(viewerId));
    }

    // ── Autores ──────────────────────────────────────────────────────────────

    private Map<AuthorKey, CommunityAuthorResponse> resolveAuthors(Collection<AuthorRef> refs) {
        Set<UUID> tutorIds = refs.stream()
                .filter(ref -> ref.role() == Role.TUTOR)
                .map(ref -> ref.user().getUserId())
                .collect(Collectors.toSet());
        Set<UUID> studentIds = refs.stream()
                .filter(ref -> ref.role() != Role.TUTOR)
                .map(ref -> ref.user().getUserId())
                .collect(Collectors.toSet());

        Map<UUID, TutorProfile> tutorProfiles = tutorIds.isEmpty()
                ? Map.of()
                : tutorProfileRepository.findWithCareerByUserIdIn(tutorIds).stream()
                        .collect(Collectors.toMap(tp -> tp.getUser().getUserId(), Function.identity()));
        Map<UUID, StudentProfile> studentProfiles = studentIds.isEmpty()
                ? Map.of()
                : studentProfileRepository.findWithCareerByUserIdIn(studentIds).stream()
                        .collect(Collectors.toMap(sp -> sp.getUser().getUserId(), Function.identity()));

        Map<AuthorKey, CommunityAuthorResponse> authors = new HashMap<>();
        for (AuthorRef ref : refs) {
            User user = ref.user();
            AuthorKey key = new AuthorKey(user.getUserId(), ref.role());
            if (authors.containsKey(key)) {
                continue;
            }
            authors.put(key, ref.role() == Role.TUTOR
                    ? tutorAuthor(user, tutorProfiles.get(user.getUserId()))
                    : studentAuthor(user, ref.role(), studentProfiles.get(user.getUserId())));
        }
        return authors;
    }

    private CommunityAuthorResponse tutorAuthor(User user, TutorProfile profile) {
        boolean bookable = profile != null
                && user.getAccountStatus() == AccountStatus.ACTIVE
                && user.getDeletedAt() == null;
        String picture = profile != null && profile.getProfilePictureUrl() != null
                ? profile.getProfilePictureUrl()
                : user.getProfilePictureUrl();
        return new CommunityAuthorResponse(
                user.getUserId(),
                displayName(user),
                Role.TUTOR,
                picture,
                profile == null ? null : profile.getCareer().getName(),
                profile == null ? null : profile.getAverageRating(),
                profile != null && profile.isVerified(),
                bookable);
    }

    private CommunityAuthorResponse studentAuthor(User user, Role role, StudentProfile profile) {
        String picture = profile != null && profile.getProfilePictureUrl() != null
                ? profile.getProfilePictureUrl()
                : user.getProfilePictureUrl();
        return new CommunityAuthorResponse(
                user.getUserId(),
                displayName(user),
                role,
                picture,
                profile == null ? null : profile.getCareer().getName(),
                null,
                false,
                false);
    }

    private String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank() ? UNKNOWN_AUTHOR_NAME : user.getFullName();
    }

    private String resolveBookingQuestion(String question) {
        return question == null || question.isBlank() ? CommunityConstants.DEFAULT_BOOKING_QUESTION : question;
    }

    private String excerpt(String content) {
        if (content.length() <= CommunityConstants.FEATURED_EXCERPT_LENGTH) {
            return content;
        }
        return content.substring(0, CommunityConstants.FEATURED_EXCERPT_LENGTH).stripTrailing() + "…";
    }
}
