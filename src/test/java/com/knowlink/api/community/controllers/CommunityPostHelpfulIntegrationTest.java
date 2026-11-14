package com.knowlink.api.community.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.enums.PostType;
import com.knowlink.api.community.data.models.CommunityPost;
import com.knowlink.api.community.data.models.CommunityPostHelpful;
import com.knowlink.api.community.data.models.CommunityReply;
import com.knowlink.api.community.data.models.CommunityReplyHelpful;
import com.knowlink.api.community.repositories.ICommunityPostHelpfulRepository;
import com.knowlink.api.community.repositories.ICommunityPostRepository;
import com.knowlink.api.community.repositories.ICommunityReplyHelpfulRepository;
import com.knowlink.api.community.repositories.ICommunityReplyRepository;
import com.knowlink.api.community.services.interfaces.ICommunityReplyService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Esto me sirvió" sobre aportes, de punta a punta: endpoints reales con autenticación, consultas por lote del
 * feed contra H2 y limpieza de marcas al eliminar una cuenta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommunityPostHelpfulIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String POSTS_PATH = "/api/v1/community/posts";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private IUserRepository userRepository;
    @Autowired
    private IInstitutionRepository institutionRepository;
    @Autowired
    private ICareerRepository careerRepository;
    @Autowired
    private ISubjectRepository subjectRepository;
    @Autowired
    private IStudentProfileRepository studentProfileRepository;
    @Autowired
    private ICommunityPostRepository postRepository;
    @Autowired
    private ICommunityReplyRepository replyRepository;
    @Autowired
    private ICommunityPostHelpfulRepository postHelpfulRepository;
    @Autowired
    private ICommunityReplyHelpfulRepository replyHelpfulRepository;
    @Autowired
    private ICommunityReplyService replyService;

    private Career career;
    private Subject subject;
    private User author;
    private User voter;
    private User otherVoter;
    private User replier;

    private CommunityPost sharedContent;
    private CommunityPost anotherSharedContent;
    private CommunityPost helpRequest;

    @BeforeEach
    void setUp() {
        Institution institution = CatalogFixtures.saveInstitution(institutionRepository, "Test " + System.nanoTime());
        career = careerRepository.save(CatalogFixtures.career(institution, "Career " + UUID.randomUUID()));
        subject = subjectRepository.save(CatalogFixtures.subject(career, "Subject " + UUID.randomUUID()));

        author = saveUser("Author");
        voter = saveUser("Voter");
        otherVoter = saveUser("Other Voter");
        replier = saveUser("Replier");

        sharedContent = savePost(PostType.SHARED_CONTENT, "Aporte A");
        anotherSharedContent = savePost(PostType.SHARED_CONTENT, "Aporte B");
        helpRequest = savePost(PostType.HELP_REQUEST, "Consulta");
    }

    // ── Marcar y quitar ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Marcar un aporte devuelve el total con la marca propia, y repetirlo no suma otra")
    void markingIsReflectedAndIdempotent() throws Exception {
        String token = login(voter);

        mockMvc.perform(put(helpfulPath(sharedContent)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(sharedContent.getPostId().toString()))
                .andExpect(jsonPath("$.helpfulCount").value(1))
                .andExpect(jsonPath("$.markedByMe").value(true));

        mockMvc.perform(put(helpfulPath(sharedContent)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(1))
                .andExpect(jsonPath("$.markedByMe").value(true));

        assertThat(postHelpfulRepository.countByPost_PostId(sharedContent.getPostId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Quitar la marca resta el voto, y repetirlo no falla ni resta de más")
    void unmarkingIsReflectedAndIdempotent() throws Exception {
        mark(sharedContent, voter);
        mark(sharedContent, otherVoter);
        String token = login(voter);

        mockMvc.perform(delete(helpfulPath(sharedContent)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(1))
                .andExpect(jsonPath("$.markedByMe").value(false));

        mockMvc.perform(delete(helpfulPath(sharedContent)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(1))
                .andExpect(jsonPath("$.markedByMe").value(false));
    }

    @Test
    @DisplayName("Dos personas distintas suman dos votos")
    void differentUsersAddUp() throws Exception {
        mockMvc.perform(put(helpfulPath(sharedContent)).header(HttpHeaders.AUTHORIZATION, bearer(login(voter))))
                .andExpect(status().isOk());

        mockMvc.perform(put(helpfulPath(sharedContent)).header(HttpHeaders.AUTHORIZATION, bearer(login(otherVoter))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(2));
    }

    // ── Reglas ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("El autor no puede marcar su propio aporte (422)")
    void authorCannotMarkOwnPost() throws Exception {
        mockMvc.perform(put(helpfulPath(sharedContent)).header(HttpHeaders.AUTHORIZATION, bearer(login(author))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("OWN_POST_NOT_MARKABLE"));

        assertThat(postHelpfulRepository.countByPost_PostId(sharedContent.getPostId())).isZero();
    }

    @Test
    @DisplayName("Una consulta no se puede marcar (422): se marcan sus respuestas")
    void helpRequestCannotBeMarked() throws Exception {
        mockMvc.perform(put(helpfulPath(helpRequest)).header(HttpHeaders.AUTHORIZATION, bearer(login(voter))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("POST_NOT_MARKABLE"));

        assertThat(postHelpfulRepository.countByPost_PostId(helpRequest.getPostId())).isZero();
    }

    @Test
    @DisplayName("Una publicación inexistente o eliminada responde 404")
    void missingOrDeletedPostIsNotFound() throws Exception {
        String token = login(voter);

        mockMvc.perform(put(POSTS_PATH + "/{id}/helpful", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("POST_NOT_FOUND"));

        anotherSharedContent.setDeletedAt(LocalDateTime.now());
        postRepository.save(anotherSharedContent);

        mockMvc.perform(put(helpfulPath(anotherSharedContent)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sin sesión no se puede marcar")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(put(helpfulPath(sharedContent)))
                .andExpect(status().is4xxClientError())
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));

        assertThat(postHelpfulRepository.countByPost_PostId(sharedContent.getPostId())).isZero();
    }

    // ── Cómo se ven en el feed y en el hilo ──────────────────────────────────

    @Test
    @DisplayName("El feed trae el conteo propio de cada aporte y si lo marcó quien consulta")
    void feedShowsEachPostsOwnCountAndTheViewersMark() throws Exception {
        mark(sharedContent, voter);
        mark(sharedContent, otherVoter);
        mark(anotherSharedContent, voter);

        JsonNode asVoter = feedItem(login(voter), sharedContent);
        assertThat(asVoter.get("helpfulCount").asLong()).isEqualTo(2);
        assertThat(asVoter.get("markedHelpfulByMe").asBoolean()).isTrue();
        JsonNode otherAsVoter = feedItem(login(voter), anotherSharedContent);
        assertThat(otherAsVoter.get("helpfulCount").asLong()).isEqualTo(1);
        assertThat(otherAsVoter.get("markedHelpfulByMe").asBoolean()).isTrue();

        // otherVoter marcó el primero pero no el segundo: el conteo es el mismo, la marca propia no.
        assertThat(feedItem(login(otherVoter), sharedContent).get("markedHelpfulByMe").asBoolean()).isTrue();
        JsonNode otherPostAsOtherVoter = feedItem(login(otherVoter), anotherSharedContent);
        assertThat(otherPostAsOtherVoter.get("helpfulCount").asLong()).isEqualTo(1);
        assertThat(otherPostAsOtherVoter.get("markedHelpfulByMe").asBoolean()).isFalse();

        // El autor ve el total de su aporte, pero no tiene marca propia.
        JsonNode asAuthor = feedItem(login(author), sharedContent);
        assertThat(asAuthor.get("helpfulCount").asLong()).isEqualTo(2);
        assertThat(asAuthor.get("markedHelpfulByMe").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("El hilo también refleja la marca de quien consulta")
    void threadReflectsTheViewersMark() throws Exception {
        mark(sharedContent, voter);

        mockMvc.perform(get(POSTS_PATH + "/{id}", sharedContent.getPostId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(voter))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.helpfulCount").value(1))
                .andExpect(jsonPath("$.post.markedHelpfulByMe").value(true));

        mockMvc.perform(get(POSTS_PATH + "/{id}", sharedContent.getPostId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(otherVoter))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.helpfulCount").value(1))
                .andExpect(jsonPath("$.post.markedHelpfulByMe").value(false));
    }

    @Test
    @DisplayName("En una consulta el contador sigue siendo el de su mejor respuesta y nunca hay marca propia")
    void helpRequestKeepsTheBestRepliesCount() throws Exception {
        CommunityReply reply = replyRepository.save(CommunityReply.builder()
                .post(helpRequest)
                .author(replier)
                .authorRole(Role.STUDENT)
                .content("Una respuesta")
                .build());
        replyHelpfulRepository.save(CommunityReplyHelpful.builder().reply(reply).user(voter).build());
        replyHelpfulRepository.save(CommunityReplyHelpful.builder().reply(reply).user(otherVoter).build());

        // voter marcó la respuesta, pero la consulta en sí no se marca.
        JsonNode item = feedItem(login(voter), helpRequest);

        assertThat(item.get("helpfulCount").asLong()).isEqualTo(2);
        assertThat(item.get("markedHelpfulByMe").asBoolean()).isFalse();
    }

    // ── Eliminación de cuenta (US-24) ────────────────────────────────────────

    @Test
    @DisplayName("Al eliminar una cuenta se borran sus marcas sobre aportes y sobre respuestas")
    void removingAUsersMarksAlsoRemovesTheOnesOnPosts() {
        CommunityReply reply = replyRepository.save(CommunityReply.builder()
                .post(helpRequest)
                .author(replier)
                .authorRole(Role.STUDENT)
                .content("Una respuesta")
                .build());
        mark(sharedContent, voter);
        mark(sharedContent, otherVoter);
        replyHelpfulRepository.save(CommunityReplyHelpful.builder().reply(reply).user(voter).build());

        replyService.removeHelpfulMarksByUser(voter.getUserId());

        // Quedan solo las de la otra persona.
        assertThat(postHelpfulRepository.countByPost_PostId(sharedContent.getPostId())).isEqualTo(1);
        assertThat(postHelpfulRepository.existsByPost_PostIdAndUser_UserId(
                sharedContent.getPostId(), voter.getUserId())).isFalse();
        assertThat(postHelpfulRepository.existsByPost_PostIdAndUser_UserId(
                sharedContent.getPostId(), otherVoter.getUserId())).isTrue();
        assertThat(replyHelpfulRepository.countByReply_ReplyId(reply.getReplyId())).isZero();
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private String helpfulPath(CommunityPost post) {
        return POSTS_PATH + "/" + post.getPostId() + "/helpful";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private void mark(CommunityPost post, User user) {
        postHelpfulRepository.save(CommunityPostHelpful.builder().post(post).user(user).build());
    }

    private CommunityPost savePost(PostType type, String title) {
        return postRepository.save(CommunityPost.builder()
                .author(author)
                .authorRole(Role.STUDENT)
                .type(type)
                .status(PostStatus.OPEN)
                .subject(subject)
                .title(title)
                .content("Contenido")
                .build());
    }

    private User saveUser(String fullName) {
        User user = userRepository.save(User.builder()
                .fullName(fullName)
                .email(fullName.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        // El login de un alumno exige un perfil de alumno activo.
        studentProfileRepository.save(StudentProfile.builder()
                .user(user)
                .career(career)
                .createdAt(LocalDateTime.now())
                .build());
        return user;
    }

    private String login(User user) throws Exception {
        var result = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andReturn().getResponse();
        assertThat(result.getStatus())
                .as("login de %s: %s", user.getEmail(), result.getContentAsString())
                .isEqualTo(200);
        return objectMapper.readTree(result.getContentAsString()).get("token").asText();
    }

    /** La publicación tal como la devuelve el feed a quien tiene ese token. */
    private JsonNode feedItem(String token, CommunityPost post) throws Exception {
        String body = mockMvc.perform(get(POSTS_PATH).param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode item : objectMapper.readTree(body).get("content")) {
            if (item.get("postId").asText().equals(post.getPostId().toString())) {
                return item;
            }
        }
        throw new AssertionError("La publicación " + post.getTitle() + " no está en la primera página del feed");
    }
}
