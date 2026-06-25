package com.acme.platform.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Error taxonomy + validation: every error path returns the structured
 * {@code {code, message, error, details?}} body with the correct status, and the
 * legacy {@code error} / {@code missingFields} keys are preserved so the
 * unchanged MCP / backend / dashboard keep working.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorTaxonomyApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void unknownQuoteIs404WithStructuredNotFoundBody() throws Exception {
        mvc.perform(get("/quotes/does-not-exist").header("X-Session-Id", "whatever"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("not_found"))
            .andExpect(jsonPath("$.error").value("not_found"))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void missingSessionStillReads404NotFoundNeverRevealingExistence() throws Exception {
        // Session-gated route: a missing header is treated as not-found (404), not 400.
        JsonNode created = create();
        mvc.perform(get("/quotes/" + created.get("quoteId").asText()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void incompleteQuotePriceIs422WithCodeAndMissingFields() throws Exception {
        JsonNode created = create();
        String qid = created.get("quoteId").asText();
        String sid = created.get("sessionId").asText();

        mvc.perform(post("/quotes/" + qid + "/price").header("X-Session-Id", sid))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("not_ready_to_price"))
            .andExpect(jsonPath("$.error").value("not_ready_to_price"))
            .andExpect(jsonPath("$.missingFields").isNotEmpty())
            .andExpect(jsonPath("$.details.missingFields").isNotEmpty());
    }

    @Test
    void purchaseLinkForUnpricedQuoteIs409NotPurchasable() throws Exception {
        JsonNode created = create();
        String qid = created.get("quoteId").asText();
        String sid = created.get("sessionId").asText();

        mvc.perform(post("/quotes/" + qid + "/purchase-link").header("X-Session-Id", sid))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("not_purchasable"))
            .andExpect(jsonPath("$.error").value("not_purchasable"));
    }

    @Test
    void issuePolicyForUnpricedQuoteIs409NotIssuable() throws Exception {
        JsonNode created = create();
        String qid = created.get("quoteId").asText();
        String sid = created.get("sessionId").asText();

        mvc.perform(post("/quotes/" + qid + "/issue-policy").header("X-Session-Id", sid))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("not_issuable"))
            .andExpect(jsonPath("$.error").value("not_issuable"));
    }

    @Test
    void malformedJsonBodyIs400BadRequest() throws Exception {
        JsonNode created = create();
        String qid = created.get("quoteId").asText();
        String sid = created.get("sessionId").asText();
        mvc.perform(patch("/quotes/" + qid)
                .header("X-Session-Id", sid)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not valid json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void blankQueryParamFailsValidationWith422() throws Exception {
        // postcode is @NotBlank — a blank value is a semantic validation failure (422).
        mvc.perform(get("/addresses").param("postcode", "   "))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void oversizedPatchBodyIs400() throws Exception {
        JsonNode created = create();
        String qid = created.get("quoteId").asText();
        String sid = created.get("sessionId").asText();

        ObjectNode patch = mapper.createObjectNode();
        String big = "x".repeat(70_000);
        patch.put("note", big);
        ObjectNode body = mapper.createObjectNode();
        body.set("patch", patch);

        mvc.perform(patch("/quotes/" + qid)
                .header("X-Session-Id", sid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"));
    }

    private JsonNode create() throws Exception {
        MvcResult res = mvc.perform(post("/quotes")).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(res.getResponse().getContentAsString());
    }
}
