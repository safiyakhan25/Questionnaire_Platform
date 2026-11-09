package com.questionnaire.questionnaire_platform.web;

import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// End-to-end, through the real HTTP layer (MockMvc, not direct service calls): a complete
// assessment covering at least one conditional template and one family member, start to finish.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EndToEndAssessmentTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private TemplateService templateService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private ObjectMapper objectMapper;

    private UUID firstNameQuestionId;
    private UUID genderQuestionId;
    private UUID isPregnantQuestionId;

    @BeforeEach
    void seedWorkflow() {
        UUID workflowId = createWorkflow(workflowService, "E2E_WF", 1);
        UUID basicTemplateId = createTemplate(templateService, workflowId, "BASIC", 1, null, SubjectScope.BOTH);
        firstNameQuestionId = createQuestion(questionService, basicTemplateId, "first_name", QuestionType.TEXT, 1,
                null, objectMapper.readTree("{\"required\":true}"), null);
        genderQuestionId = createQuestion(questionService, basicTemplateId, "gender_at_birth", QuestionType.RADIO, 2,
                objectMapper.readTree("[{\"label\":\"Male\",\"value\":\"MALE\"},{\"label\":\"Female\",\"value\":\"FEMALE\"}]"),
                objectMapper.readTree("{\"required\":true}"), null);

        UUID womenTemplateId = createTemplate(templateService, workflowId, "WOMEN", 2,
                objectMapper.readTree("{\"==\":[{\"var\":\"gender_at_birth\"},\"FEMALE\"]}"), SubjectScope.PROBAND_ONLY);
        isPregnantQuestionId = createQuestion(questionService, womenTemplateId, "is_pregnant", QuestionType.RADIO, 1,
                objectMapper.readTree("[{\"label\":\"Yes\",\"value\":true},{\"label\":\"No\",\"value\":false}]"),
                objectMapper.readTree("{\"required\":true}"), null);
    }

    private String field(String json, String pattern) throws Exception {
        Matcher matcher = Pattern.compile(pattern).matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String answerBody(UUID questionId, String value, String familyMemberId) {
        String fm = familyMemberId == null ? "null" : "\"" + familyMemberId + "\"";
        return "{\"questionId\":\"" + questionId + "\",\"value\":" + value + ",\"familyMemberId\":" + fm + "}";
    }

    @Test
    void completeAssessmentWithAConditionalTemplateAndAFamilyMember() throws Exception {
        // 1. Start the proband's session.
        String startBody = mockMvc.perform(post("/api/v1/assessment/start")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateCode", is("BASIC")))
                .andReturn().getResponse().getContentAsString();
        String sessionId = field(startBody, "\"sessionId\":\"([a-f0-9-]+)\"");

        // 2. Answer the proband's required fields, choosing FEMALE to trigger the conditional template.
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/answer").contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(firstNameQuestionId, "\"Safiya\"", null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/answer").contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(genderQuestionId, "\"FEMALE\"", null)))
                .andExpect(status().isCreated());

        // 3. Advance - the conditional WOMEN template should now apply.
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/next-template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateCode", is("WOMEN")))
                .andExpect(jsonPath("$.completed", is(false)));

        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/answer").contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(isPregnantQuestionId, "false", null)))
                .andExpect(status().isCreated());

        // 4. Add a family member HERE, while the proband's own session is still IN_PROGRESS
        // (paused at WOMEN, not yet completed) - a COMPLETED session rejects this (Step 6). Adding
        // only adds her to the list - it does not start her pass (see the per-subject-progress
        // design) - "id" is her FamilyMember id, not a template to continue into yet.
        String familyMemberBody = mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/family-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relationshipType\":\"MOTHER\",\"maternalPaternalSide\":\"MATERNAL\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String familyMemberId = field(familyMemberBody, "\"id\":\"([a-f0-9-]+)\"");

        // Continuing her pass (GET current-template with her id) lazily resolves her entry
        // template the first time it's asked for - BASIC, the same row the proband started on.
        mockMvc.perform(get("/api/v1/assessment/" + sessionId + "/current-template")
                        .param("familyMemberId", familyMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateCode", is("BASIC")))
                .andExpect(jsonPath("$.familyMemberId", is(familyMemberId)));

        mockMvc.perform(get("/api/v1/assessment/" + sessionId))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        // 5. Answer the family member's required fields (MALE - WOMEN is PROBAND_ONLY, correctly skipped for her).
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/answer").contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(firstNameQuestionId, "\"Jane\"", familyMemberId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/answer").contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(genderQuestionId, "\"MALE\"", familyMemberId)))
                .andExpect(status().isCreated());

        // 6. Her pass exhausts (WOMEN is PROBAND_ONLY) - a subject-scoped "done", not a transition
        // back to the proband (each subject's position is independent, there's no "return" anymore).
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/next-template")
                        .param("familyMemberId", familyMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed", is(true)))
                .andExpect(jsonPath("$.familyMemberId", is(familyMemberId)));
        mockMvc.perform(get("/api/v1/assessment/" + sessionId))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        // The proband's own position, untouched by her pass throughout, is still exactly WOMEN.
        mockMvc.perform(get("/api/v1/assessment/" + sessionId + "/current-template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateCode", is("WOMEN")))
                .andExpect(jsonPath("$.familyMemberId", is((Object) null)));

        // 7. The proband now advances past their own last template - session completes.
        mockMvc.perform(post("/api/v1/assessment/" + sessionId + "/next-template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed", is(true)));
        mockMvc.perform(get("/api/v1/assessment/" + sessionId))
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        // 8. The full transcript shows both subjects, correctly separated.
        mockMvc.perform(get("/api/v1/assessment/" + sessionId + "/responses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].familyMemberId", is((Object) null)))
                .andExpect(jsonPath("$[0].answerValue", is("Safiya")))
                .andExpect(jsonPath("$[3].familyMemberId", is(familyMemberId)))
                .andExpect(jsonPath("$[3].answerValue", is("Jane")));
    }
}
