package com.knowlink.api.community.data.specifications;

import com.knowlink.api.community.data.enums.PostStatus;
import com.knowlink.api.community.data.enums.PostType;
import com.knowlink.api.community.data.filters.CommunityFeedFilter;
import com.knowlink.api.community.data.models.CommunityPost;
import com.knowlink.api.community.repositories.ICommunityPostRepository;
import com.knowlink.api.security.enums.Role;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CommunityPostSpecificationsTest {

    @Autowired
    private ICommunityPostRepository postRepository;
    @Autowired
    private IInstitutionRepository institutionRepository;
    @Autowired
    private ICareerRepository careerRepository;
    @Autowired
    private ISubjectRepository subjectRepository;
    @Autowired
    private IUserRepository userRepository;

    private User author;
    private Career utnRegular;
    private Career unvmRegular;
    private Subject utnOwn;
    private Subject utnShared;
    private Subject unvmOwn;
    private Subject unvmShared;
    private CommunityPost utnOwnPost;
    private CommunityPost utnSharedPost;
    private CommunityPost unvmOwnPost;
    private CommunityPost unvmSharedPost;

    @BeforeEach
    void setUp() {
        Institution utn = institutionRepository.save(CatalogFixtures.institution("UTN FRVM"));
        Institution unvm = institutionRepository.save(CatalogFixtures.institution("UNVM"));
        utnRegular = careerRepository.save(CatalogFixtures.career(utn, "Ingeniería en Sistemas"));
        unvmRegular = careerRepository.save(CatalogFixtures.career(unvm, "Licenciatura en Administración"));
        Career utnSharedCareer = careerRepository.save(CatalogFixtures.sharedCareer(utn));
        Career unvmSharedCareer = careerRepository.save(CatalogFixtures.sharedCareer(unvm));

        utnOwn = subjectRepository.save(CatalogFixtures.subject(utnRegular, "Programación"));
        utnShared = subjectRepository.save(CatalogFixtures.subject(utnSharedCareer, "Análisis Matemático"));
        unvmOwn = subjectRepository.save(CatalogFixtures.subject(unvmRegular, "Contabilidad"));
        unvmShared = subjectRepository.save(CatalogFixtures.subject(unvmSharedCareer, "Análisis Matemático"));

        author = userRepository.save(User.builder()
                .fullName("Author")
                .email("author." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        utnOwnPost = post(utnOwn);
        utnSharedPost = post(utnShared);
        unvmOwnPost = post(unvmOwn);
        unvmSharedPost = post(unvmShared);
    }

    private CommunityPost post(Subject subject) {
        return postRepository.save(CommunityPost.builder()
                .author(author)
                .authorRole(Role.STUDENT)
                .type(PostType.HELP_REQUEST)
                .status(PostStatus.OPEN)
                .subject(subject)
                .title("Title " + UUID.randomUUID())
                .content("Content")
                .build());
    }

    private List<CommunityPost> postsFor(UUID careerId) {
        return postRepository.findAll(CommunityPostSpecifications.fromFilter(
                new CommunityFeedFilter(null, null, careerId, null, null), null));
    }

    @Test
    void inCareer_includesOwnAndSharedSubjectsOfTheSameInstitution() {
        List<CommunityPost> posts = postsFor(utnRegular.getCareerId());

        assertThat(posts).extracting(CommunityPost::getPostId)
                .containsExactlyInAnyOrder(utnOwnPost.getPostId(), utnSharedPost.getPostId());
    }

    @Test
    void inCareer_doesNotIncludeSharedSubjectsOfAnotherInstitution() {
        List<CommunityPost> utnPosts = postsFor(utnRegular.getCareerId());
        List<CommunityPost> unvmPosts = postsFor(unvmRegular.getCareerId());

        assertThat(utnPosts).extracting(CommunityPost::getPostId)
                .doesNotContain(unvmSharedPost.getPostId(), unvmOwnPost.getPostId());
        assertThat(unvmPosts).extracting(CommunityPost::getPostId)
                .containsExactlyInAnyOrder(unvmOwnPost.getPostId(), unvmSharedPost.getPostId());
    }
}
