package com.abhiknow.store;

import com.abhiknow.support.StubServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static com.abhiknow.support.Fixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whole backend against a real PostgreSQL (migrations V1+V2 applied from a clean schema) with the real
 * adapters pointed at local stub servers. Runs only when ABHIKNOW_TEST_DB_URL is set.
 */
@EnabledIfEnvironmentVariable(named = "ABHIKNOW_TEST_DB_URL", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Import(FullStackIntegrationTest.CleanMigrate.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullStackIntegrationTest {
    private static final List<StubServer> stubs = new ArrayList<>();
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestConfiguration
    static class CleanMigrate {
        @Bean
        FlywayMigrationStrategy cleanThenMigrate() {
            return flyway -> { flyway.clean(); flyway.migrate(); };
        }
    }

    private static String stub(java.util.function.Function<String, StubServer.Response> h) {
        StubServer s = new StubServer(h);
        stubs.add(s);
        return s.url();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("ABHIKNOW_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> System.getenv().getOrDefault("ABHIKNOW_TEST_DB_USER", "abhiknow"));
        r.add("spring.datasource.password", () -> System.getenv().getOrDefault("ABHIKNOW_TEST_DB_PASSWORD", ""));
        r.add("spring.flyway.clean-disabled", () -> "false");
        String oa = stub(p -> StubServer.Response.json(read("openalex_constructed.json")));
        String cr = stub(p -> StubServer.Response.json(read("crossref_constructed.json")));
        String ax = stub(p -> StubServer.Response.xml(read("arxiv_attention_constructed.xml")));
        String ol = stub(p -> StubServer.Response.json(read("openlibrary_live.json")));
        String gu = stub(p -> StubServer.Response.status(503));
        r.add("abhiknow.urls.openalex", () -> oa);
        r.add("abhiknow.urls.crossref", () -> cr);
        r.add("abhiknow.urls.arxiv", () -> ax);
        r.add("abhiknow.urls.openlibrary", () -> ol);
        r.add("abhiknow.urls.gutendex", () -> gu);
        r.add("abhiknow.max-retries", () -> "0");
    }

    @AfterAll
    static void stop() { stubs.forEach(StubServer::close); }

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    private JsonNode search(String q) throws Exception {
        String body = mvc.perform(get("/api/search").param("q", q)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private static JsonNode byTitle(JsonNode results, String title) {
        for (JsonNode r : results) if (title.equals(r.get("title").asText())) return r;
        throw new AssertionError("No result titled " + title);
    }

    @Test @Order(1)
    void searchReturnsRealMergedResultsFromMultipleProvidersAndSurvivesOneFailing() throws Exception {
        JsonNode res = search("deep learning");
        JsonNode results = res.get("results");
        assertThat(results.size()).isGreaterThan(5);

        // OpenAlex + arXiv describe the same preprint (same arXiv DOI) -> one result, two sources, open access
        JsonNode attention = byTitle(results, "Attention Is All You Need");
        List<String> sources = new ArrayList<>();
        attention.get("sources").forEach(s -> sources.add(s.asText()));
        assertThat(sources).containsExactlyInAnyOrder("openalex", "arxiv");
        assertThat(attention.get("accessType").asText()).isEqualTo("OPEN_ACCESS");
        assertThat(attention.get("recommendedAction").asText()).isEqualTo("Read for free");
        int attentionCount = 0;
        for (JsonNode r : results) if (r.get("title").asText().equals("Attention Is All You Need")) attentionCount++;
        assertThat(attentionCount).isEqualTo(1);

        // Paywalled/unknown stays unknown: never labelled free
        assertThat(byTitle(results, "Deep learning").get("accessType").asText()).isEqualTo("UNKNOWN");
        // Open Library books carry their restrictions
        assertThat(byTitle(results, "Artificial Intelligence").get("accessType").asText()).isEqualTo("RESTRICTED");

        // provider outcomes are reported honestly
        java.util.Map<String, String> status = new java.util.HashMap<>();
        res.get("providers").forEach(p -> status.put(p.get("providerId").asText(), p.get("status").asText()));
        assertThat(status).containsEntry("gutenberg", "ERROR").containsEntry("openalex", "OK").containsEntry("crossref", "OK")
                .containsEntry("arxiv", "OK").containsEntry("openlibrary", "OK");
        assertThat(res.get("coverage").asText()).contains("Searched 4 of 5").contains("not exhaustive");
    }

    @Test @Order(2)
    void detailPageAndIdempotentPersistence() throws Exception {
        JsonNode first = byTitle(search("deep learning").get("results"), "Attention Is All You Need");
        String id = first.get("id").asText();
        int resourcesBefore = jdbc.sql("SELECT count(*) FROM resource").query(Integer.class).single();

        String body = mvc.perform(get("/api/resources/" + id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode detail = JSON.readTree(body);
        assertThat(detail.get("title").asText()).isEqualTo("Attention Is All You Need");
        assertThat(detail.get("authors").get(0).asText()).isEqualTo("Ashish Vaswani");
        assertThat(detail.get("locations").size()).isGreaterThanOrEqualTo(3);
        assertThat(detail.get("locations").get(0).get("accessType").asText()).isEqualTo("OPEN_ACCESS");
        assertThat(detail.get("identifiers").toString()).contains("10.48550/arxiv.1706.03762").contains("1706.03762");

        // a different query that returns the same works must not create duplicate rows
        JsonNode again = byTitle(search("attention transformers").get("results"), "Attention Is All You Need");
        assertThat(again.get("id").asText()).isEqualTo(id);
        assertThat(jdbc.sql("SELECT count(*) FROM resource WHERE title = 'Attention Is All You Need'").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM resource").query(Integer.class).single()).isGreaterThanOrEqualTo(resourcesBefore);
    }

    @Test @Order(3)
    void analyticsAndUsageAreRecorded() throws Exception {
        search("neural networks");
        assertThat(jdbc.sql("SELECT count(*) FROM search_session").query(Integer.class).single()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("SELECT coalesce(sum(failures),0) FROM provider_usage WHERE provider_id='gutenberg'").query(Long.class).single()).isGreaterThanOrEqualTo(1);
    }

    @Test @Order(4)
    void validationAndErrors() throws Exception {
        mvc.perform(get("/api/search").param("q", "   ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/search")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", "x".repeat(301))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", "ai").param("types", "NOPE")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/resources/00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
        mvc.perform(get("/api/resources/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test @Order(5)
    void providerRegistryIsExposedWithPendingTrustStatus() throws Exception {
        String body = mvc.perform(get("/api/providers")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode arr = JSON.readTree(body);
        assertThat(arr.size()).isEqualTo(5);
        arr.forEach(p -> assertThat(p.get("trustStatus").asText()).isEqualTo("PENDING_REVIEW"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test @Order(6)
    void blockedProviderIsNeverQueried() throws Exception {
        jdbc.sql("UPDATE provider SET trust_status='BLOCKED' WHERE id='crossref'").update();
        try {
            JsonNode res = search("quantum computing");
            for (JsonNode p : res.get("providers")) {
                if (p.get("providerId").asText().equals("crossref")) assertThat(p.get("status").asText()).isEqualTo("DISABLED");
            }
        } finally {
            jdbc.sql("UPDATE provider SET trust_status='PENDING_REVIEW' WHERE id='crossref'").update();
        }
    }
}
