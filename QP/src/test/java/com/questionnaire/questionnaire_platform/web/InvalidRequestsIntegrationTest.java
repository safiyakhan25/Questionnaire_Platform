package com.questionnaire.questionnaire_platform.web;

import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.repository.DiseaseRepository;
import com.questionnaire.questionnaire_platform.service.QuestionService;
import com.questionnaire.questionnaire_platform.service.TemplateService;
import com.questionnaire.questionnaire_platform.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.questionnaire.questionnaire_platform.support.TestFixtures.createQuestion;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createTemplate;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createWorkflow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers: invalid requests, missing entities, and (via the disease-array parse-before-write
// design) a database-transaction-failure analog - a malformed mid-array entry must leave zero
// partial writes behind, confirmed directly against the repository, not just the HTTP response.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvalidRequestsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private TemplateService templateService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private DiseaseRepository diseaseRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private UUID firstNameQuestionId;
    private UUID diseasesQuestionId;

    @BeforeEach
    void seedWorkflow() {
        UUID workflowId = createWorkflow(workflowService, "TEST_WF", 1);
        UUID templateId = createTemplate(templateService, workflowId, "BASIC", 1, null, SubjectScope.PROBAND_ONLY);
        firstNameQuestionId = createQuestion(questionService, templateId, "first_name", QuestionType.TEXT, 1,
                null, objectMapper.readTree("{\"required\":true}"), null);
        diseasesQuestionId = createQuestion(questionService, templateId, "diseases", QuestionType.CHECKBOX, 2,
                null, objectMapper.readTree("{\"required\":false}"), null);
    }

    private String startSessionAndGetId() throws Exception {
        String body = mockMvc.perform(post("/api/v1/assessment/start").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"sessionId\":\"([a-f0-9-]+)\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void fetchingAnUnknownSessionReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/assessment/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void submittingAnAnswerForAnUnknownQuestionReturns404() throws Exception {
        String sessionId = startSessionAndGetId();
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + UUID.randomUUID() + "\",\"value\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedJsonBodyReturns400() throws Exception {
        String sessionId = startSessionAndGetId();
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingRequiredFieldInRequestBodyReturns400() throws Exception {
        String sessionId = startSessionAndGetId();
        // questionId is @NotNull - omitting it should fail bean validation, not NPE through.
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankRequiredAnswerReturns400() throws Exception {
        String sessionId = startSessionAndGetId();
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + firstNameQuestionId + "\",\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("'first_name' is required")));
    }

    @Test
    void malformedDiseaseArrayEntryLeavesZeroPartialDatabaseWrites() throws Exception {
        String sessionId = startSessionAndGetId();

        // First entry is well-formed, second is missing diseaseType - the whole submission must
        // be rejected, and NOTHING (not even the valid first entry) may land in Postgres.
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + diseasesQuestionId + "\","
                                + "\"value\":[{\"diseaseType\":\"Diabetes\"},{\"ageAtDiagnosis\":45}]}"))
                .andExpect(status().isBadRequest());

        assertThat(diseaseRepository.count()).isZero();
    }
}
