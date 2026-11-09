package com.questionnaire.questionnaire_platform.web;

import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.service.AssessmentService;
import com.questionnaire.questionnaire_platform.service.QuestionService;
import com.questionnaire.questionnaire_platform.service.TemplateService;
import com.questionnaire.questionnaire_platform.service.WorkflowService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.questionnaire.questionnaire_platform.support.TestFixtures.createQuestion;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createTemplate;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createWorkflow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers the /pedigree-diagram endpoint end-to-end through MockMvc, against the same kind of fake
// PedigreeDraw stand-in as PedigreeDiagramServiceTest - this class is about the controller/exception
// wiring (status codes, headers), not the HTTP-proxying logic itself, which is already covered there.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PedigreeDiagramIntegrationTest {

    private static HttpServer fakeServer;
    private static volatile int responseStatus;
    private static volatile byte[] responseBody;

    @DynamicPropertySource
    static void overrideBaseUrl(DynamicPropertyRegistry registry) throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeServer.createContext("/render", exchange -> {
            byte[] body = responseBody;
            exchange.getResponseHeaders().add("Content-Type", "image/svg+xml");
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeServer.start();
        registry.add("pedigree.render.service.base-url", () -> "http://localhost:" + fakeServer.getAddress().getPort());
    }

    @BeforeEach
    void resetFakeResponse() {
        responseStatus = 200;
        responseBody = "<svg>fake</svg>".getBytes(StandardCharsets.UTF_8);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkflowService workflowService;
    @Autowired private TemplateService templateService;
    @Autowired private QuestionService questionService;
    @Autowired private AssessmentService assessmentService;

    private UUID sessionId;

    @BeforeEach
    void seedMinimalSession() {
        UUID workflowId = createWorkflow(workflowService, "PEDIGREE_DIAGRAM_HTTP_TEST_WF", 1);
        UUID templateId = createTemplate(templateService, workflowId, "PEDIGREE_DIAGRAM_HTTP_TEST_TPL", 1, null, SubjectScope.BOTH);
        createQuestion(questionService, templateId, "first_name", QuestionType.TEXT, 1, null, null, null);
        sessionId = assessmentService.start(new AssessmentStartRequest(null)).sessionId();
    }

    @Test
    void returnsSvgBytesWithContentTypeFromTheRenderService() throws Exception {
        mockMvc.perform(get("/api/v1/assessment/{id}/pedigree-diagram", sessionId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/svg+xml"))
                .andExpect(content().bytes("<svg>fake</svg>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void returns422WhenPedigreeDrawRejectsTheDocument() throws Exception {
        responseStatus = 422;
        responseBody = "{\"errors\":[]}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/v1/assessment/{id}/pedigree-diagram", sessionId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void returns502OnAnUnexpectedUpstreamFailure() throws Exception {
        responseStatus = 500;
        responseBody = "boom".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/v1/assessment/{id}/pedigree-diagram", sessionId))
                .andExpect(status().isBadGateway());
    }

    @Test
    void returns400ForAnUnrecognizedFormat() throws Exception {
        mockMvc.perform(get("/api/v1/assessment/{id}/pedigree-diagram?format=svg%26dpi%3D100000", sessionId))
                .andExpect(status().isBadRequest());
    }
}
