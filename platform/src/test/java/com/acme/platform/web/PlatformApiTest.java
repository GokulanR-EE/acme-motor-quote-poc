package com.acme.platform.web;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.acme.platform.quote.DemoSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void health() throws Exception {
        mvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void ping() throws Exception {
        mvc.perform(post("/ping").contentType(MediaType.APPLICATION_JSON).content("{\"hi\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pong").value(true))
            .andExpect(jsonPath("$.echo.hi").value(1));
    }

    @Test
    void postQuoteReturns201WithSessionAndStartedState() throws Exception {
        mvc.perform(post("/quotes"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.quoteId").exists())
            .andExpect(jsonPath("$.sessionId").exists())
            .andExpect(jsonPath("$.journeyState").value("quote_started"))
            .andExpect(jsonPath("$.missingFields").isNotEmpty());
    }

    @Test
    void getQuoteSessionGated() throws Exception {
        JsonNode created = create();
        String qid = created.get("quoteId").asText();
        String sid = created.get("sessionId").asText();

        mvc.perform(get("/quotes/" + qid).header("X-Session-Id", sid))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quoteId").value(qid))
            .andExpect(jsonPath("$.sessionId").doesNotExist())
            .andExpect(jsonPath("$.currentOutcome").doesNotExist()); // present-but-null

        mvc.perform(get("/quotes/" + qid)).andExpect(status().isNotFound());
        mvc.perform(get("/quotes/" + qid).header("X-Session-Id", "wrong")).andExpect(status().isNotFound());
    }

    @Test
    void patchDeepMergesAndDropsRegistrationFromMissing() throws Exception {
        JsonNode created = create();
        String qid = created.get("quoteId").asText();
        String sid = created.get("sessionId").asText();

        mvc.perform(patch("/quotes/" + qid)
                .header("X-Session-Id", sid)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patch\":{\"vehicle\":{\"registration\":\"FX19ZTC\"}}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.journeyState").value("collecting"))
            .andExpect(jsonPath("$.missingFields", not(hasItem("vehicle.registration"))))
            .andExpect(jsonPath("$.missingFields", hasItem("vehicle.make")));
    }

    @Test
    void patchWrongSessionIsNotFound() throws Exception {
        JsonNode created = create();
        mvc.perform(patch("/quotes/" + created.get("quoteId").asText())
                .header("X-Session-Id", "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patch\":{\"vehicle\":{\"registration\":\"X\"}}}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void vehicleLookup() throws Exception {
        mvc.perform(get("/vehicles/FX19ZTC"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.make").value("Ford"))
            .andExpect(jsonPath("$.model").value("Focus"))
            .andExpect(jsonPath("$.registration").value("FX19ZTC"));
    }

    @Test
    void addressLookup() throws Exception {
        mvc.perform(get("/addresses").param("postcode", "RG1 1AA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postcode").value("RG1 1AA"))
            .andExpect(jsonPath("$.candidates").isArray())
            .andExpect(jsonPath("$.candidates[1]").exists());
    }

    @Test
    void demoQuoteResolvesWithDemoSessionAndIsReadyToPrice() throws Exception {
        mvc.perform(get("/quotes/" + DemoSeeder.DEMO_QUOTE_ID).header("X-Session-Id", DemoSeeder.DEMO_SESSION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quoteId").value(DemoSeeder.DEMO_QUOTE_ID))
            .andExpect(jsonPath("$.missingFields", is(empty())))
            .andExpect(jsonPath("$.journeyState").value("ready_to_price"));
    }

    @Test
    void demoQuoteWrongSessionIsNotFound() throws Exception {
        mvc.perform(get("/quotes/" + DemoSeeder.DEMO_QUOTE_ID).header("X-Session-Id", "nope"))
            .andExpect(status().isNotFound());
    }

    private JsonNode create() throws Exception {
        MvcResult res = mvc.perform(post("/quotes")).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(res.getResponse().getContentAsString());
    }
}
