package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.AnswerSubmitRequest;
import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.dto.CurrentTemplateResponse;
import com.questionnaire.questionnaire_platform.dto.QuestionAnswerView;
import com.questionnaire.questionnaire_platform.dto.QuestionResponseView;
import com.questionnaire.questionnaire_platform.dto.SessionStatusResponse;
import com.questionnaire.questionnaire_platform.engine.PersonAnswerWriter;
import com.questionnaire.questionnaire_platform.engine.PersonFactsMapper;
import com.questionnaire.questionnaire_platform.engine.RuleEngine;
import com.questionnaire.questionnaire_platform.entity.AnswerType;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.entity.Question;
import com.questionnaire.questionnaire_platform.entity.QuestionResponse;
import com.questionnaire.questionnaire_platform.entity.QuestionnaireSession;
import com.questionnaire.questionnaire_platform.entity.SessionStatus;
import com.questionnaire.questionnaire_platform.entity.Template;
import com.questionnaire.questionnaire_platform.entity.Workflow;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import com.questionnaire.questionnaire_platform.repository.PersonRepository;
import com.questionnaire.questionnaire_platform.repository.QuestionRepository;
import com.questionnaire.questionnaire_platform.repository.QuestionResponseRepository;
import com.questionnaire.questionnaire_platform.repository.QuestionnaireSessionRepository;
import com.questionnaire.questionnaire_platform.repository.TemplateRepository;
import com.questionnaire.questionnaire_platform.repository.WorkflowRepository;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Plays the Workflow Engine's role for this first pass: owns session progress and decides
// what happens next. Template loading is simple enough at this scope to stay inlined here
// rather than as a separate TemplateEngine component.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssessmentService {

    private final QuestionnaireSessionRepository sessionRepository;
    private final PersonRepository personRepository;
    private final WorkflowRepository workflowRepository;
    private final TemplateRepository templateRepository;
    private final QuestionRepository questionRepository;
    private final QuestionResponseRepository questionResponseRepository;
    private final RuleEngine ruleEngine;
    private final PersonFactsMapper personFactsMapper;
    private final PersonAnswerWriter personAnswerWriter;
    private final JsonColumnMapper jsonColumnMapper;

    @Transactional
    public CurrentTemplateResponse start(AssessmentStartRequest request) {
        QuestionnaireSession session;
        if (request.sessionId() != null) {
            session = sessionRepository.findById(request.sessionId())
                    .filter(s -> s.getStatus() == SessionStatus.IN_PROGRESS)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No in-progress session " + request.sessionId() + " to resume"));
        } else {
            LocalDateTime now = LocalDateTime.now();
            Person person = new Person();
            person.setIsActive(true);
            person.setCreatedAt(now);
            person.setUpdatedAt(now);
            person = personRepository.save(person);

            session = new QuestionnaireSession();
            session.setPerson(person);
            session.setStatus(SessionStatus.IN_PROGRESS);
            session.setStartedAt(now);
            session = sessionRepository.save(session);
        }

        Template template = loadOrResolveEntryTemplate(session);
        return buildTemplateResponse(session, template);
    }

    public CurrentTemplateResponse currentTemplate(UUID sessionId) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        if (session.getStatus() == SessionStatus.COMPLETED) {
            return completedResponse(session);
        }
        Template template = session.getPreviousTemplate();
        if (template == null) {
            throw new ResourceNotFoundException(
                    "Session " + sessionId + " has not loaded a template yet - call start first");
        }
        return buildTemplateResponse(session, template);
    }

    // No re-evaluation happens here, deliberately: FR-11 guarantees a question's rule can
    // only reference an earlier template's answer_keys, never its own template's - so no
    // question in the currently-rendered template can ever be affected by an answer given
    // in that same template. Only a *later* template's questions (not yet rendered) or its
    // template_rule (checked separately, in nextTemplate()) can depend on this answer.
    @Transactional
    public QuestionResponseView submitAnswer(UUID sessionId, AnswerSubmitRequest request) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question " + request.questionId() + " not found"));

        AnswerType answerType = deriveAnswerType(request.value());

        QuestionResponse response = new QuestionResponse();
        response.setSession(session);
        response.setQuestion(question);
        response.setTemplate(question.getTemplate());
        response.setAnswerKey(question.getAnswerKey());
        response.setAnswerType(answerType);
        response.setAnswerValue(jsonColumnMapper.write(request.value()));
        response.setCreatedAt(LocalDateTime.now());
        response = questionResponseRepository.save(response);

        Person person = session.getPerson();
        personAnswerWriter.apply(person, question.getAnswerKey(), request.value());
        person.setUpdatedAt(LocalDateTime.now());

        return new QuestionResponseView(
                response.getId(), question.getId(), question.getTemplate().getId(), question.getAnswerKey(),
                response.getAnswerType(), jsonColumnMapper.read(response.getAnswerValue()), response.getCreatedAt());
    }

    @Transactional
    public CurrentTemplateResponse nextTemplate(UUID sessionId) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        if (session.getStatus() == SessionStatus.COMPLETED) {
            return completedResponse(session);
        }

        Template current = session.getPreviousTemplate();
        if (current == null) {
            throw new ResourceNotFoundException(
                    "Session " + sessionId + " has not loaded a template yet - call start first");
        }

        Optional<Template> next = resolveNextTemplate(session.getPerson(), current);
        if (next.isEmpty()) {
            session.setStatus(SessionStatus.COMPLETED);
            session.setCompletedAt(LocalDateTime.now());
            return completedResponse(session);
        }

        Template nextTemplate = next.get();
        session.setPreviousTemplate(nextTemplate);
        session.setPreviousWorkflow(nextTemplate.getWorkflow());
        return buildTemplateResponse(session, nextTemplate);
    }

    public SessionStatusResponse status(UUID sessionId) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        return new SessionStatusResponse(
                session.getId(), session.getStatus(), session.getStartedAt(), session.getCompletedAt());
    }

    public List<QuestionResponseView> responses(UUID sessionId) {
        findSessionOrThrow(sessionId);
        return questionResponseRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(r -> new QuestionResponseView(
                        r.getId(), r.getQuestion().getId(), r.getTemplate().getId(), r.getAnswerKey(),
                        r.getAnswerType(), jsonColumnMapper.read(r.getAnswerValue()), r.getCreatedAt()))
                .toList();
    }

    // --- template resolution -------------------------------------------------------------

    private Template loadOrResolveEntryTemplate(QuestionnaireSession session) {
        if (session.getPreviousTemplate() != null) {
            return session.getPreviousTemplate();
        }
        Template entry = resolveEntryTemplate();
        session.setPreviousTemplate(entry);
        session.setPreviousWorkflow(entry.getWorkflow());
        return entry;
    }

    // The first workflow's, first template - loaded unconditionally, its templateRule (if
    // any) is never evaluated (SRS 8.2).
    private Template resolveEntryTemplate() {
        Workflow firstWorkflow = workflowRepository.findAllByOrderByDisplayOrderAsc().stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No workflow configuration exists yet"));
        return templateRepository.findAllByWorkflowIdOrderByDisplayOrderAsc(firstWorkflow.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Workflow '" + firstWorkflow.getCode() + "' has no templates yet"));
    }

    // Next candidate template in the current workflow, gated by its templateRule (FR-5); once
    // exhausted, the next workflow's own default (lowest displayOrder) template loads
    // unconditionally, same rule as resolveEntryTemplate. Empty means the questionnaire is
    // complete - no further applicable template exists anywhere.
    private Optional<Template> resolveNextTemplate(Person person, Template current) {
        Map<String, Object> facts = personFactsMapper.toFacts(person);

        List<Template> siblings = templateRepository
                .findAllByWorkflowIdOrderByDisplayOrderAsc(current.getWorkflow().getId());
        boolean passedCurrent = false;
        for (Template candidate : siblings) {
            if (!passedCurrent) {
                passedCurrent = candidate.getId().equals(current.getId());
                continue;
            }
            if (candidate.getTemplateRule() == null || ruleEngine.evaluate(candidate.getTemplateRule(), facts)) {
                return Optional.of(candidate);
            }
        }

        List<Workflow> workflows = workflowRepository.findAllByOrderByDisplayOrderAsc();
        boolean passedCurrentWorkflow = false;
        for (Workflow workflow : workflows) {
            if (!passedCurrentWorkflow) {
                passedCurrentWorkflow = workflow.getId().equals(current.getWorkflow().getId());
                continue;
            }
            List<Template> nextWorkflowTemplates =
                    templateRepository.findAllByWorkflowIdOrderByDisplayOrderAsc(workflow.getId());
            if (!nextWorkflowTemplates.isEmpty()) {
                return Optional.of(nextWorkflowTemplates.get(0));
            }
        }

        return Optional.empty();
    }

    // --- response building -----------------------------------------------------------------

    private CurrentTemplateResponse buildTemplateResponse(QuestionnaireSession session, Template template) {
        Map<String, Object> facts = personFactsMapper.toFacts(session.getPerson());
        List<QuestionAnswerView> questions = questionRepository
                .findAllByTemplateIdOrderByDisplayOrderAsc(template.getId()).stream()
                .map(q -> toView(session, q, facts))
                .toList();

        return new CurrentTemplateResponse(
                session.getId(), template.getWorkflow().getId(), template.getId(),
                template.getCode(), template.getName(), questions, false);
    }

    private CurrentTemplateResponse completedResponse(QuestionnaireSession session) {
        return new CurrentTemplateResponse(session.getId(), null, null, null, null, List.of(), true);
    }

    private QuestionAnswerView toView(QuestionnaireSession session, Question question, Map<String, Object> facts) {
        String visibilityRule = question.getVisibilityRule();
        boolean visible = visibilityRule == null || ruleEngine.evaluate(visibilityRule, facts);

        JsonNode value = questionResponseRepository
                .findTopBySessionIdAndQuestionIdOrderByCreatedAtDesc(session.getId(), question.getId())
                .map(r -> jsonColumnMapper.read(r.getAnswerValue()))
                .orElse(null);

        return new QuestionAnswerView(
                question.getId(), question.getAnswerKey(), question.getText(), question.getType(),
                question.getDisplayOrder(), jsonColumnMapper.read(question.getOptions()),
                jsonColumnMapper.read(question.getValidation()), visible, value);
    }

    private AnswerType deriveAnswerType(JsonNode value) {
        return switch (value.getNodeType()) {
            case BOOLEAN -> AnswerType.BOOLEAN;
            case NUMBER -> AnswerType.NUMBER;
            case STRING -> AnswerType.STRING;
            case ARRAY -> AnswerType.ARRAY;
            case OBJECT -> AnswerType.OBJECT;
            default -> throw new InvalidAnswerException("Unsupported answer value type: " + value.getNodeType());
        };
    }

    private QuestionnaireSession findSessionOrThrow(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session " + sessionId + " not found"));
    }
}
