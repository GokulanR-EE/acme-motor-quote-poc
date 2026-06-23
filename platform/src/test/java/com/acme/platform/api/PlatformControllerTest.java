package com.acme.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.acme.platform.events.Event;
import com.acme.platform.events.EventStore;

@SpringBootTest
class PlatformControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EventStore eventStore;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc().perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));
    }

    @Test
    void pingReturnsPongEchoAndVendorAndLogsThreeLayer() throws Exception {
        int before = eventStore.all().size();

        mockMvc().perform(post("/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hello\":\"world\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pong", is(true)))
                .andExpect(jsonPath("$.echo.hello", is("world")))
                .andExpect(jsonPath("$.vendor.vendor", is("MOCK")))
                .andExpect(jsonPath("$.vendor.status", is("UP")));

        List<Event> appended = eventStore.all().subList(before, eventStore.all().size());

        // Exactly one API_CALL (category "api") and one PING (category "domain").
        assertThat(appended)
                .filteredOn(e -> e.type().equals("API_CALL"))
                .singleElement()
                .satisfies(e -> assertThat(e.category()).isEqualTo("api"));

        assertThat(appended)
                .filteredOn(e -> e.type().equals("PING"))
                .singleElement()
                .satisfies(e -> assertThat(e.category()).isEqualTo("domain"));
    }
}
