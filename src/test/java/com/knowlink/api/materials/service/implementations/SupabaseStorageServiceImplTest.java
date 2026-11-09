package com.knowlink.api.materials.service.implementations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseStorageServiceImplTest {

    private static final String SUPABASE_URL = "https://project.supabase.co";
    private static final String MATERIALS_BUCKET = "materiales";
    private static final String SIGNED_RESPONSE =
            "{\"signedURL\":\"/object/sign/materiales/subject/file.pdf?token=abc\"}";

    private MockRestServiceServer server;
    private SupabaseStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        storageService = new SupabaseStorageServiceImpl(builder);
        ReflectionTestUtils.setField(storageService, "materialsBucket", MATERIALS_BUCKET);
        ReflectionTestUtils.setField(storageService, "supabaseUrl", SUPABASE_URL);
        ReflectionTestUtils.setField(storageService, "serviceRoleKey", "service-role-key");
    }

    @Test
    @DisplayName("URL firmada de material - usa el bucket configurado, no un nombre fijo")
    void generateSignedUrl_withoutBucket_usesConfiguredMaterialsBucket() {
        server.expect(requestTo(SUPABASE_URL + "/storage/v1/object/sign/materiales/subject/file.pdf"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(SIGNED_RESPONSE, MediaType.APPLICATION_JSON));

        String signedUrl = storageService.generateSignedUrl("subject/file.pdf", 3600);

        assertThat(signedUrl)
                .isEqualTo(SUPABASE_URL + "/storage/v1/object/sign/materiales/subject/file.pdf?token=abc");
        server.verify();
    }

    @Test
    @DisplayName("URL firmada con bucket explícito - respeta el bucket indicado")
    void generateSignedUrl_withBucket_usesGivenBucket() {
        server.expect(requestTo(SUPABASE_URL + "/storage/v1/object/sign/claimEvidence/booking/file.pdf"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"signedURL\":\"/object/sign/claimEvidence/booking/file.pdf?token=abc\"}",
                        MediaType.APPLICATION_JSON));

        storageService.generateSignedUrl("claimEvidence", "booking/file.pdf", 3600);

        server.verify();
    }
}
