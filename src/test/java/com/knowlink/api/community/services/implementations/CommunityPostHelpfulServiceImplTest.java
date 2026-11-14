package com.knowlink.api.community.services.implementations;

import com.knowlink.api.community.controllers.responses.PostHelpfulMarkResponse;
import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.enums.PostType;
import com.knowlink.api.community.data.models.CommunityPost;
import com.knowlink.api.community.data.models.CommunityPostHelpful;
import com.knowlink.api.community.repositories.ICommunityPostHelpfulRepository;
import com.knowlink.api.community.repositories.ICommunityPostRepository;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.UnprocessableEntityException;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityPostHelpfulServiceImplTest {

    @Mock
    private ICommunityPostRepository postRepository;
    @Mock
    private ICommunityPostHelpfulRepository helpfulRepository;
    @Mock
    private IUserRepository userRepository;
    @InjectMocks
    private CommunityPostHelpfulServiceImpl service;

    private final UUID authorId = UUID.randomUUID();
    private final UUID voterId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();

    private User voter;
    private CommunityPost sharedContent;
    private CommunityPost helpRequest;

    @BeforeEach
    void setUp() {
        User author = User.builder().userId(authorId).fullName("Autora").build();
        voter = User.builder().userId(voterId).fullName("Votante").build();
        sharedContent = post(author, PostType.SHARED_CONTENT);
        helpRequest = post(author, PostType.HELP_REQUEST);
    }

    @Test
    @DisplayName("Marcar un aporte guarda la marca y devuelve el total vigente")
    void markHelpfulSavesTheMarkAndReturnsTheCurrentTotal() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(sharedContent));
        when(helpfulRepository.existsByPost_PostIdAndUser_UserId(postId, voterId)).thenReturn(false);
        when(userRepository.findById(voterId)).thenReturn(Optional.of(voter));
        when(helpfulRepository.countByPost_PostId(postId)).thenReturn(4L);

        PostHelpfulMarkResponse response = service.markHelpful(voterId, postId);

        assertThat(response).isEqualTo(new PostHelpfulMarkResponse(postId, 4L, true));
        ArgumentCaptor<CommunityPostHelpful> saved = ArgumentCaptor.forClass(CommunityPostHelpful.class);
        verify(helpfulRepository).save(saved.capture());
        assertThat(saved.getValue().getPost()).isSameAs(sharedContent);
        assertThat(saved.getValue().getUser()).isSameAs(voter);
    }

    @Test
    @DisplayName("Marcar dos veces es idempotente: no se guarda una segunda marca")
    void markHelpfulIsIdempotent() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(sharedContent));
        when(helpfulRepository.existsByPost_PostIdAndUser_UserId(postId, voterId)).thenReturn(true);
        when(helpfulRepository.countByPost_PostId(postId)).thenReturn(1L);

        PostHelpfulMarkResponse response = service.markHelpful(voterId, postId);

        assertThat(response.markedByMe()).isTrue();
        assertThat(response.helpfulCount()).isEqualTo(1L);
        verify(helpfulRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una consulta no se puede marcar: se marcan sus respuestas")
    void markHelpfulRejectsHelpRequests() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(helpRequest));

        assertThatThrownBy(() -> service.markHelpful(voterId, postId))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting("errorCode").isEqualTo("POST_NOT_MARKABLE");
        verify(helpfulRepository, never()).save(any());
    }

    @Test
    @DisplayName("El autor no puede marcar su propio aporte")
    void markHelpfulRejectsTheAuthorsOwnPost() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(sharedContent));

        assertThatThrownBy(() -> service.markHelpful(authorId, postId))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting("errorCode").isEqualTo("OWN_POST_NOT_MARKABLE");
        verify(helpfulRepository, never()).save(any());
    }

    @Test
    @DisplayName("Marcar una publicación inexistente o eliminada responde 404")
    void markHelpfulFailsWhenThePostIsMissing() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markHelpful(voterId, postId))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo("POST_NOT_FOUND");
        verify(helpfulRepository, never()).save(any());
    }

    @Test
    @DisplayName("Si el usuario que marca no existe se responde 404 y no se guarda nada")
    void markHelpfulFailsWhenTheUserIsMissing() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(sharedContent));
        when(helpfulRepository.existsByPost_PostIdAndUser_UserId(postId, voterId)).thenReturn(false);
        when(userRepository.findById(voterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markHelpful(voterId, postId))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo("USER_NOT_FOUND");
        verify(helpfulRepository, never()).save(any());
    }

    @Test
    @DisplayName("Quitar la marca la elimina y devuelve el total sin ella")
    void unmarkHelpfulDeletesTheExistingMark() {
        CommunityPostHelpful existing = CommunityPostHelpful.builder().post(sharedContent).user(voter).build();
        when(postRepository.findByPostIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(sharedContent));
        when(helpfulRepository.findByPost_PostIdAndUser_UserId(postId, voterId)).thenReturn(Optional.of(existing));
        when(helpfulRepository.countByPost_PostId(postId)).thenReturn(0L);

        PostHelpfulMarkResponse response = service.unmarkHelpful(voterId, postId);

        assertThat(response).isEqualTo(new PostHelpfulMarkResponse(postId, 0L, false));
        verify(helpfulRepository).delete(existing);
    }

    @Test
    @DisplayName("Quitar una marca que no existe es idempotente: no borra nada")
    void unmarkHelpfulIsIdempotent() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(sharedContent));
        when(helpfulRepository.findByPost_PostIdAndUser_UserId(postId, voterId)).thenReturn(Optional.empty());
        when(helpfulRepository.countByPost_PostId(postId)).thenReturn(2L);

        PostHelpfulMarkResponse response = service.unmarkHelpful(voterId, postId);

        assertThat(response).isEqualTo(new PostHelpfulMarkResponse(postId, 2L, false));
        verify(helpfulRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Quitar la marca de una publicación inexistente responde 404")
    void unmarkHelpfulFailsWhenThePostIsMissing() {
        when(postRepository.findByPostIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unmarkHelpful(voterId, postId))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo("POST_NOT_FOUND");
    }

    private CommunityPost post(User author, PostType type) {
        return CommunityPost.builder()
                .postId(postId)
                .author(author)
                .authorRole(Role.TUTOR)
                .type(type)
                .status(PostStatus.OPEN)
                .title("Título")
                .content("Contenido")
                .build();
    }
}
