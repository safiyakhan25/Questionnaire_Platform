package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.exception.InvalidPedigreeDocumentException;
import com.questionnaire.questionnaire_platform.exception.InvalidPedigreeFormatException;
import com.questionnaire.questionnaire_platform.exception.PedigreeRenderFailedException;
import com.questionnaire.questionnaire_platform.exception.PedigreeServiceUnavailableException;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.questionnaire.questionnaire_platform.support.TestFixtures.createQuestion;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createTemplate;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createWorkflow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// A fake PedigreeDraw: a bare-bones HttpServer (the same JDK class PedigreeDraw's own pedigree-http
// module is built on) standing on an ephemeral port, its /render response controlled per-test via
// the static fields below. Avoids needing PedigreeDraw itself running to verify the HTTP proxying
// and error-translation behavior in PedigreeDiagramService.
@SpringBootTest
@Transactional
class PedigreeDiagramServiceTest {

    private static HttpServer fakeServer;
    private static volatile int responseStatus;
    private static volatile String responseContentType;
    private static volatile byte[] responseBody;
    private static volatile long responseDelayMs;

    @DynamicPropertySource
    static void overrideBaseUrl(DynamicPropertyRegistry registry) throws IOException {
        fakeServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeServer.createContext("/render", exchange -> {
            if (responseDelayMs > 0) {
                try {
                    Thread.sleep(responseDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = responseBody;
            exchange.getResponseHeaders().add("Content-Type", responseContentType);
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
        responseContentType = "image/svg+xml";
        responseBody = "<svg>fake</svg>".getBytes(StandardCharsets.UTF_8);
        responseDelayMs = 0;
    }

    @AfterEach
    void resetToDefaults() {
        responseStatus = 200;
        responseContentType = "image/svg+xml";
        responseBody = "<svg>fake</svg>".getBytes(StandardCharsets.UTF_8);
        responseDelayMs = 0;
    }

    @Autowired private PedigreeDiagramService pedigreeDiagramService;
    @Autowired private PedigreeExportService pedigreeExportService;
    @Autowired private JsonColumnMapper jsonColumnMapper;
    @Autowired private WorkflowService workflowService;
    @Autowired private TemplateService templateService;
    @Autowired private QuestionService questionService;
    @Autowired private AssessmentService assessmentService;

    private UUID sessionId;

    @BeforeEach
    void seedMinimalSession() {
        UUID workflowId = createWorkflow(workflowService, "PEDIGREE_DIAGRAM_TEST_WF", 1);
        UUID templateId = createTemplate(templateService, workflowId, "PEDIGREE_DIAGRAM_TEST_TPL", 1, null, SubjectScope.BOTH);
        createQuestion(questionService, templateId, "first_name", QuestionType.TEXT, 1, null, null, null);
        sessionId = assessmentService.start(new AssessmentStartRequest(null)).sessionId();
    }

    @Test
    void rendersSvgBytesFromAMinimalSession() {
        PedigreeDiagram result = pedigreeDiagramService.render(sessionId, "svg");

        assertThat(result.contentType()).isEqualTo("image/svg+xml");
        assertThat(new String(result.bytes(), StandardCharsets.UTF_8)).isEqualTo("<svg>fake</svg>");
    }

    @Test
    void throwsInvalidPedigreeDocumentOn422WithoutLeakingTheUpstreamBodyInTheMessage() {
        responseStatus = 422;
        responseBody = "{\"errors\":[{\"path\":\"/individuals\",\"message\":\"Sarah Connor has an invalid field\"}]}"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> pedigreeDiagramService.render(sessionId, "svg"))
                .isInstanceOf(InvalidPedigreeDocumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Sarah Connor", "/individuals"));
    }

    @Test
    void throwsInvalidPedigreeFormatForAnUnrecognizedFormatWithoutCallingPedigreeDraw() {
        assertThatThrownBy(() -> pedigreeDiagramService.render(sessionId, "svg&dpi=100000"))
                .isInstanceOf(InvalidPedigreeFormatException.class);
    }

    // A short-timeout instance against the same fake server, which is told (via responseDelayMs)
    // to take far longer to respond than that timeout allows - proves the request itself is
    // bounded, not just the initial connection (HttpClient.Builder.connectTimeout alone does
    // nothing once the TCP connection to a local, already-listening server succeeds instantly).
    @Test
    void throwsPedigreeServiceUnavailableWhenTheResponseTakesLongerThanTheTimeout() {
        responseDelayMs = 2000;
        PedigreeDiagramService shortTimeout = new PedigreeDiagramService(
                pedigreeExportService, jsonColumnMapper,
                "http://localhost:" + fakeServer.getAddress().getPort(), 200);

        assertThatThrownBy(() -> shortTimeout.render(sessionId, "svg"))
                .isInstanceOf(PedigreeServiceUnavailableException.class);
    }

    @Test
    void throwsPedigreeRenderFailedOnOtherNon2xx() {
        responseStatus = 500;
        responseBody = "boom".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> pedigreeDiagramService.render(sessionId, "svg"))
                .isInstanceOf(PedigreeRenderFailedException.class);
    }

    // A separate, directly-constructed instance pointed at a port nothing listens on (1 is a
    // reserved/privileged port, never bound in a dev environment) rather than stopping the shared
    // fakeServer - stopping it here would break whichever other test in this class happens to run
    // after this one, since JUnit doesn't guarantee method order.
    @Test
    void throwsPedigreeServiceUnavailableWhenNothingIsListening() {
        PedigreeDiagramService unreachable =
                new PedigreeDiagramService(pedigreeExportService, jsonColumnMapper, "http://localhost:1", 2000);

        assertThatThrownBy(() -> unreachable.render(sessionId, "svg"))
                .isInstanceOf(PedigreeServiceUnavailableException.class);
    }
}
