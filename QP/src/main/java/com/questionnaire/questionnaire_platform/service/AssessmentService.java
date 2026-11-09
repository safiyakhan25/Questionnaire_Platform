package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.AnswerSubmitRequest;
import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.dto.CurrentTemplateResponse;
import com.questionnaire.questionnaire_platform.dto.QuestionAnswerView;
import com.questionnaire.questionnaire_platform.dto.QuestionResponseView;
import com.questionnaire.questionnaire_platform.dto.SessionStatusResponse;
import com.questionnaire.questionnaire_platform.dto.SessionSummaryResponse;
import com.questionnaire.questionnaire_platform.dto.VisitedTemplateResponse;
import com.questionnaire.questionnaire_platform.engine.AdoptionAnswerWriter;
import com.questionnaire.questionnaire_platform.engine.AnswerValidator;
import com.questionnaire.questionnaire_platform.engine.AssistedReproductionAnswerWriter;
import com.questionnaire.questionnaire_platform.engine.DiseaseAnswerWriter;
import com.questionnaire.questionnaire_platform.engine.FamilyMemberAnswerWriter;
import com.questionnaire.questionnaire_platform.engine.FamilyMemberFactsMapper;
import com.questionnaire.questionnaire_platform.engine.GenderTransitionAnswerWriter;
import com.questionnaire.questionnaire_platform.engine.MutationAnswerWriter;
import com.questionnaire.questionnaire_platform.engine.PersonAnswerWriter;
import com.questionnaire.questionnaire_platform.engine.PersonFactsMapper;
import com.questionnaire.questionnaire_platform.engine.PregnancyOutcomeAnswerWriter;
import com.questionnaire.questionnaire_platform.engine.RuleEngine;
import com.questionnaire.questionnaire_platform.engine.TwinshipAnswerWriter;
import com.questionnaire.questionnaire_platform.entity.AnswerType;
import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.entity.Question;
import com.questionnaire.questionnaire_platform.entity.QuestionResponse;
import com.questionnaire.questionnaire_platform.entity.QuestionnaireSession;
import com.questionnaire.questionnaire_platform.entity.SessionStatus;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.entity.Template;
import com.questionnaire.questionnaire_platform.entity.Workflow;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.exception.ResourceInUseException;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import com.questionnaire.questionnaire_platform.exception.TemplateIncompleteException;
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
import tools.jackson.databind.node.NullNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Plays the Workflow Engine's role for this first pass: owns session progress and decides
// what happens next. Template loading is simple enough at this scope to stay inlined here
// rather than as a separate TemplateEngine component.
//
// Subject model: every subject (the proband, or any one family member) owns its OWN position,
// independently of every other subject's - there is no single "active subject" anymore. The
// proband's position lives on QuestionnaireSession.previousTemplate/previousWorkflow, untouched by
// any family member's pass. Each FamilyMember owns its own currentTemplate/currentWorkflow. Every
// caller passes which subject it means (null = the proband) explicitly on every call; nothing about
// "who's active" is read from hidden session state. See
// docs/superpowers/specs/2026-10-29-auth-removal-and-independent-passes-design.md §4.
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
    private final FamilyMemberFactsMapper familyMemberFactsMapper;
    private final AnswerValidator answerValidator;
    private final PersonAnswerWriter personAnswerWriter;
    private final FamilyMemberAnswerWriter familyMemberAnswerWriter;
    private final DiseaseAnswerWriter diseaseAnswerWriter;
    private final MutationAnswerWriter mutationAnswerWriter;
    private final GenderTransitionAnswerWriter genderTransitionAnswerWriter;
    private final TwinshipAnswerWriter twinshipAnswerWriter;
    private final AdoptionAnswerWriter adoptionAnswerWriter;
    private final PregnancyOutcomeAnswerWriter pregnancyOutcomeAnswerWriter;
    private final AssistedReproductionAnswerWriter assistedReproductionAnswerWriter;
    private final FamilyMemberService familyMemberService;
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

        Subject subject = new Subject(null, personFactsMapper.toFacts(session.getPerson()));
        Template template = resolvePosition(session, subject);
        return buildTemplateResponse(session, template, subject);
    }

    // Writable, not read-only: resolving a subject's position for the first time (a family member's
    // pass that hasn't started, or - defensively - a proband row somehow missing previousTemplate)
    // persists that resolution via resolvePosition(). A read-only transaction here would let
    // Hibernate silently drop that write at commit instead of flushing it.
    @Transactional
    public CurrentTemplateResponse currentTemplate(UUID sessionId, UUID familyMemberId) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        if (session.getStatus() == SessionStatus.COMPLETED) {
            return completedResponse(session, familyMemberId);
        }
        Subject subject = resolveSubject(session, familyMemberId);
        Template template = resolvePosition(session, subject);
        return buildTemplateResponse(session, template, subject);
    }

    // No re-evaluation happens here, deliberately: FR-11 guarantees a question's rule can
    // only reference an earlier template's answer_keys, never its own template's - so no
    // question in the currently-rendered template can ever be affected by an answer given
    // in that same template. Only a *later* template's questions (not yet rendered) or its
    // template_rule (checked separately, in nextTemplate()) can depend on this answer.
    @Transactional
    public QuestionResponseView submitAnswer(UUID sessionId, AnswerSubmitRequest request) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        requireInProgress(session);
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question " + request.questionId() + " not found"));

        // Who this answer is about is the request's own familyMemberId - resolveSubject validates
        // it belongs to this session's proband (rejecting a spoofed/foreign id) and, for a family
        // member, hands back the actual entity so its position can be read and written below.
        Subject subject = resolveSubject(session, request.familyMemberId());

        // Session/request integrity: only the subject's own current position can be answered
        // against. The ONLY path to answer against a different, earlier template is
        // resume-at-template (FR-8.6), which moves this same position there first - so this check
        // alone is sufficient, it doesn't need its own separate "or an already-visited template"
        // allowance.
        Template currentTemplate = resolvePosition(session, subject);
        if (!question.getTemplate().getId().equals(currentTemplate.getId())) {
            throw new InvalidAnswerException(
                    "Question " + question.getId() + " belongs to template '" + question.getTemplate().getCode()
                            + "', but " + subjectLabel(subject) + "'s current template is '"
                            + currentTemplate.getCode() + "'");
        }

        // FR-15: validated before anything is persisted or written to a business entity.
        answerValidator.validate(question, request.value());

        AnswerType answerType = deriveAnswerType(request.value());

        QuestionResponse response = new QuestionResponse();
        response.setSession(session);
        response.setQuestion(question);
        response.setTemplate(question.getTemplate());
        response.setAnswerKey(question.getAnswerKey());
        response.setAnswerType(answerType);
        response.setAnswerValue(jsonColumnMapper.write(request.value()));
        response.setCreatedAt(LocalDateTime.now());

        if (subject.familyMember() != null) {
            response.setRelationshipInstance(subject.familyMember());
            applyAnswerToSubject(null, subject.familyMember(), question.getAnswerKey(), request.value());
            subject.familyMember().setUpdatedAt(LocalDateTime.now());
        } else {
            Person person = session.getPerson();
            applyAnswerToSubject(person, null, question.getAnswerKey(), request.value());
            person.setUpdatedAt(LocalDateTime.now());
        }

        response = questionResponseRepository.save(response);

        // FR-9: this may be a correction to an earlier template's answer (reachable via
        // resume-at-template, FR-8.6) - re-check everything downstream for the same subject and
        // nullify (never delete) whatever current data no longer applies. A no-op for an ordinary
        // forward answer, since nothing downstream has been reached/answered yet in that case.
        List<String> invalidated = cascadeInvalidateDownstream(session, question.getTemplate(), subject);

        return new QuestionResponseView(
                response.getId(), question.getId(), question.getTemplate().getId(), question.getAnswerKey(),
                subject.familyMemberId(), response.getAnswerType(),
                jsonColumnMapper.read(response.getAnswerValue()), response.getCreatedAt(), invalidated);
    }

    // Subject-aware: advances within whichever subject's own walk this call names, filtered to
    // templates applicable to that subject (SubjectScope). Exhausting the proband's walk completes
    // the whole session; exhausting a family member's walk only completes THEIR pass (a
    // subject-scoped "done") - the proband's own position is never touched by this, since it was
    // never shared with the family member's in the first place.
    @Transactional
    public CurrentTemplateResponse nextTemplate(UUID sessionId, UUID familyMemberId) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        if (session.getStatus() == SessionStatus.COMPLETED) {
            return completedResponse(session, familyMemberId);
        }

        Subject subject = resolveSubject(session, familyMemberId);
        Template current = resolvePosition(session, subject);
        validateTemplateCompletion(session, current, subject);

        boolean forFamilyMember = subject.familyMember() != null;
        Optional<Template> next = resolveNextApplicableTemplate(current, forFamilyMember, subject.facts());
        if (next.isPresent()) {
            Template nextTemplate = next.get();
            advancePosition(session, subject, nextTemplate);
            return buildTemplateResponse(session, nextTemplate, subject);
        }

        if (forFamilyMember) {
            return completedResponse(session, familyMemberId);
        }

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        return completedResponse(session, null);
    }

    // For the landing page's session list - every session, most recently started first. Display
    // name is the proband's own name once DEMOGRAPHICS has been answered; until then a session has
    // no name yet, so a cosmetic placeholder stands in (not a structural distinction - the session
    // itself is identical either way).
    public List<SessionSummaryResponse> listSessions() {
        return sessionRepository.findAllByOrderByStartedAtDesc().stream()
                .map(session -> new SessionSummaryResponse(
                        session.getId(), displayName(session.getPerson()), session.getStatus(), session.getStartedAt()))
                .toList();
    }

    private String displayName(Person person) {
        String firstName = person.getFirstName();
        String lastName = person.getLastName();
        String fullName = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return fullName.isEmpty() ? "Unnamed - started " + person.getCreatedAt().toLocalDate() : fullName;
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
                        r.getRelationshipInstance() == null ? null : r.getRelationshipInstance().getId(),
                        r.getAnswerType(), jsonColumnMapper.read(r.getAnswerValue()), r.getCreatedAt(), List.of()))
                .toList();
    }

    // --- previous/forward navigation and editing (FR-8, FR-9) -------------------------------

    // FR-8.1/8.2: the named subject's actual answered path - never display_order, never
    // recalculated from current rules. A template the subject never answered anything in (skipped,
    // or never reached) simply never appears here, even if it was technically rendered once.
    //
    // Exception: a template with zero questions (a pure navigation waypoint, e.g.
    // FAMILY_MEMBER_HUB) can never acquire a QUESTION_RESPONSE row, so answer history alone could
    // never surface it - there's no "answered path" concept that applies to a template with nothing
    // to answer. It's always included instead, so a waypoint like the family-member hub stays
    // reachable via the normal resume-at-template navigation even after the subject's walk has
    // moved past it (final-review finding, 2026-10-30).
    public List<VisitedTemplateResponse> visitedTemplates(UUID sessionId, UUID familyMemberId) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        Subject subject = resolveSubject(session, familyMemberId);
        List<VisitedTemplateResponse> answered = questionResponseRepository
                .findVisitedTemplates(sessionId, subject.familyMemberId()).stream()
                .map(row -> {
                    Template template = templateRepository.findById(row.templateId())
                            .orElseThrow(() -> new ResourceNotFoundException("Template " + row.templateId() + " not found"));
                    return new VisitedTemplateResponse(template.getId(), template.getCode(), template.getName(), row.firstAnsweredAt());
                })
                .toList();

        List<VisitedTemplateResponse> result = new ArrayList<>(answered);
        Set<UUID> alreadyIncluded = answered.stream().map(VisitedTemplateResponse::templateId).collect(Collectors.toSet());
        for (Template template : zeroQuestionTemplates(subject.familyMember() != null)) {
            if (alreadyIncluded.add(template.getId())) {
                result.add(new VisitedTemplateResponse(template.getId(), template.getCode(), template.getName(), null));
            }
        }
        return result;
    }

    // Every template applicable to the given subject that has no QUESTION rows at all - see
    // visitedTemplates' header comment for why these are always reachable regardless of history.
    private List<Template> zeroQuestionTemplates(boolean forFamilyMember) {
        return applicableTemplatesInOrder(forFamilyMember).stream()
                .filter(template -> questionRepository.findAllByTemplateIdOrderByDisplayOrderAsc(template.getId()).isEmpty())
                .toList();
    }

    // FR-8.3/8.5: read-only - shows a previously visited template exactly as buildTemplateResponse
    // would, but never touches the named subject's real position. Browsing never submits or
    // modifies anything. Rejects a template the named subject never actually visited, per FR-8.2.
    public CurrentTemplateResponse viewVisitedTemplate(UUID sessionId, UUID templateId, UUID familyMemberId) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        Subject subject = resolveSubject(session, familyMemberId);
        Template template = requireVisited(session, templateId, subject);
        return buildTemplateResponse(session, template, subject);
    }

    // FR-8.4/8.6: "go back to here" - the only path that moves the REAL position backward, which
    // is what makes editing (FR-9) possible via the normal /answer endpoint afterward. A session
    // that had already completed resumes IN_PROGRESS when the PROBAND resumes into it (the proband
    // is actively working on it again) - resuming into a family member's own template never
    // reopens the session, since a family member's pass was never what completed it. Scoped to the
    // named subject's own path - doesn't reach across subjects.
    @Transactional
    public CurrentTemplateResponse resumeAtTemplate(UUID sessionId, UUID templateId, UUID familyMemberId) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        Subject subject = resolveSubject(session, familyMemberId);
        Template template = requireVisited(session, templateId, subject);

        advancePosition(session, subject, template);
        if (familyMemberId == null && session.getStatus() == SessionStatus.COMPLETED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            session.setCompletedAt(null);
        }
        return buildTemplateResponse(session, template, subject);
    }

    private Template requireVisited(QuestionnaireSession session, UUID templateId, Subject subject) {
        boolean answered = questionResponseRepository.findVisitedTemplates(session.getId(), subject.familyMemberId()).stream()
                .anyMatch(row -> row.templateId().equals(templateId));
        boolean alwaysReachable = !answered && zeroQuestionTemplates(subject.familyMember() != null).stream()
                .anyMatch(template -> template.getId().equals(templateId));
        if (!answered && !alwaysReachable) {
            throw new ResourceNotFoundException(
                    "Template " + templateId + " was never visited by " + subjectLabel(subject)
                            + " in session " + session.getId());
        }
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template " + templateId + " not found"));
    }

    // FR-9: after `editedTemplate`'s answer changes, walk every later template applicable to this
    // subject in order, re-evaluating with the subject's live (just-mutated) facts at each step so
    // a nullification earlier in the cascade is visible to later steps in the same pass. A later
    // template whose templateRule no longer passes has every one of its answered fields nullified
    // (FR-9.3); a still-applicable template's individual questions whose visibilityRule no longer
    // passes have just that field nullified (FR-9.1/9.2). QUESTION_RESPONSE rows are never touched.
    private List<String> cascadeInvalidateDownstream(QuestionnaireSession session, Template editedTemplate, Subject subject) {
        boolean forFamilyMember = subject.familyMember() != null;
        List<Template> applicable = applicableTemplatesInOrder(forFamilyMember);
        int editedIndex = -1;
        for (int i = 0; i < applicable.size(); i++) {
            if (applicable.get(i).getId().equals(editedTemplate.getId())) {
                editedIndex = i;
                break;
            }
        }
        if (editedIndex < 0) {
            return List.of();
        }

        List<String> invalidated = new ArrayList<>();
        for (int i = editedIndex + 1; i < applicable.size(); i++) {
            Template downstream = applicable.get(i);
            Map<String, Object> facts = currentFacts(session, subject);
            boolean templateApplicable = downstream.getTemplateRule() == null
                    || ruleEngine.evaluate(downstream.getTemplateRule(), facts);

            List<Question> questions = questionRepository.findAllByTemplateIdOrderByDisplayOrderAsc(downstream.getId());
            if (!templateApplicable) {
                for (Question question : questions) {
                    if (nullifyIfAnswered(session, subject, question)) {
                        invalidated.add(question.getAnswerKey());
                    }
                }
                continue;
            }

            for (Question question : questions) {
                String visibilityRule = question.getVisibilityRule();
                boolean visible = visibilityRule == null || ruleEngine.evaluate(visibilityRule, currentFacts(session, subject));
                if (!visible && nullifyIfAnswered(session, subject, question)) {
                    invalidated.add(question.getAnswerKey());
                }
            }
        }
        return invalidated;
    }

    private Map<String, Object> currentFacts(QuestionnaireSession session, Subject subject) {
        return subject.familyMember() == null
                ? personFactsMapper.toFacts(session.getPerson())
                : familyMemberFactsMapper.toFacts(subject.familyMember());
    }

    // Resets the business-entity field to unset if (and only if) it's currently answered - the
    // historical QUESTION_RESPONSE row is left exactly as it was (FR-9.2). Returns whether anything
    // was actually nullified, so the cascade only reports fields that genuinely changed.
    private boolean nullifyIfAnswered(QuestionnaireSession session, Subject subject, Question question) {
        boolean answered = questionResponseRepository
                .findTopBySessionIdAndQuestionIdAndRelationshipInstanceIdOrderByCreatedAtDesc(
                        session.getId(), question.getId(), subject.familyMemberId())
                .map(r -> answerValidator.isAnswered(jsonColumnMapper.read(r.getAnswerValue())))
                .orElse(false);
        if (!answered) {
            return false;
        }

        if (subject.familyMember() != null) {
            applyAnswerToSubject(null, subject.familyMember(), question.getAnswerKey(), NullNode.getInstance());
            subject.familyMember().setUpdatedAt(LocalDateTime.now());
        } else {
            Person person = session.getPerson();
            applyAnswerToSubject(person, null, question.getAnswerKey(), NullNode.getInstance());
            person.setUpdatedAt(LocalDateTime.now());
        }
        return true;
    }

    // Dispatches to whichever writer actually owns this answer_key. DISEASE/MUTATION are
    // repeatable sub-records (FR-3.2: "a NEW DISEASE or MUTATION record"), not a single fixed
    // column, so they can't go through PersonAnswerWriter/FamilyMemberAnswerWriter's switch -
    // intercepted here, before either writer's default (additional_facts) case would otherwise
    // swallow them. Used by both the normal answer path and the FR-9 nullify cascade, so a
    // diagnosis/finding cleared by an upstream edit is correctly deleted here too, not stashed
    // as inert JSON. Exactly one of person/familyMember is non-null, same convention as elsewhere.
    private void applyAnswerToSubject(Person person, FamilyMember familyMember, String answerKey, JsonNode value) {
        switch (answerKey) {
            case DiseaseAnswerWriter.ANSWER_KEY -> {
                if (familyMember != null) {
                    diseaseAnswerWriter.applyToFamilyMember(familyMember, value);
                } else {
                    diseaseAnswerWriter.applyToPerson(person, value);
                }
            }
            case MutationAnswerWriter.ANSWER_KEY -> {
                if (familyMember != null) {
                    mutationAnswerWriter.applyToFamilyMember(familyMember, value);
                } else {
                    mutationAnswerWriter.applyToPerson(person, value);
                }
            }
            case GenderTransitionAnswerWriter.ANSWER_KEY -> {
                if (familyMember != null) {
                    genderTransitionAnswerWriter.applyToFamilyMember(familyMember, value);
                } else {
                    genderTransitionAnswerWriter.applyToPerson(person, value);
                }
            }
            case TwinshipAnswerWriter.ANSWER_KEY -> {
                if (familyMember != null) {
                    twinshipAnswerWriter.applyToFamilyMember(familyMember, value);
                } else {
                    twinshipAnswerWriter.applyToPerson(person, value);
                }
            }
            case AdoptionAnswerWriter.ANSWER_KEY -> {
                if (familyMember != null) {
                    adoptionAnswerWriter.applyToFamilyMember(familyMember, value);
                } else {
                    adoptionAnswerWriter.applyToPerson(person, value);
                }
            }
            case PregnancyOutcomeAnswerWriter.ANSWER_KEY -> {
                if (familyMember != null) {
                    pregnancyOutcomeAnswerWriter.applyToFamilyMember(familyMember, value);
                } else {
                    pregnancyOutcomeAnswerWriter.applyToPerson(person, value);
                }
            }
            case AssistedReproductionAnswerWriter.ANSWER_KEY -> {
                if (familyMember != null) {
                    assistedReproductionAnswerWriter.applyToFamilyMember(familyMember, value);
                } else {
                    assistedReproductionAnswerWriter.applyToPerson(person, value);
                }
            }
            default -> {
                if (familyMember != null) {
                    familyMemberAnswerWriter.apply(familyMember, answerKey, value);
                } else {
                    personAnswerWriter.apply(person, answerKey, value);
                }
            }
        }
    }

    // --- template resolution -------------------------------------------------------------

    // The named subject's current position, loading their entry template the first time they're
    // accessed - session.previousTemplate/previousWorkflow for the proband (as always, never
    // touched by a family member's pass), or this specific family member's own
    // currentTemplate/currentWorkflow (set the first time their pass is touched, i.e. the first
    // time this is called with their id). Each subject's position is independent - resolving or
    // advancing one never reads or writes another's.
    private Template resolvePosition(QuestionnaireSession session, Subject subject) {
        if (subject.familyMember() == null) {
            if (session.getPreviousTemplate() != null) {
                return session.getPreviousTemplate();
            }
            Template entry = resolveFirstApplicableTemplate(false)
                    .orElseThrow(() -> new ResourceNotFoundException("No workflow/template configuration exists yet"));
            session.setPreviousTemplate(entry);
            session.setPreviousWorkflow(entry.getWorkflow());
            return entry;
        }

        FamilyMember familyMember = subject.familyMember();
        if (familyMember.getCurrentTemplate() != null) {
            return familyMember.getCurrentTemplate();
        }
        Template entry = resolveFirstApplicableTemplate(true)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No FAMILY_MEMBER_ONLY/BOTH template configured yet"));
        familyMember.setCurrentTemplate(entry);
        familyMember.setCurrentWorkflow(entry.getWorkflow());
        return entry;
    }

    private void advancePosition(QuestionnaireSession session, Subject subject, Template template) {
        if (subject.familyMember() == null) {
            session.setPreviousTemplate(template);
            session.setPreviousWorkflow(template.getWorkflow());
        } else {
            subject.familyMember().setCurrentTemplate(template);
            subject.familyMember().setCurrentWorkflow(template.getWorkflow());
        }
    }

    // Every WORKFLOW's templates, in (workflow displayOrder, template displayOrder) order,
    // filtered to those applicable to the given subject - PROBAND_ONLY+BOTH for the proband,
    // FAMILY_MEMBER_ONLY+BOTH for a family member's pass. Same flattened order the two-level
    // workflow/template hierarchy always implied; this is the one place that order is resolved.
    private List<Template> applicableTemplatesInOrder(boolean forFamilyMember) {
        List<Template> result = new ArrayList<>();
        for (Workflow workflow : workflowRepository.findAllByOrderByDisplayOrderAsc()) {
            for (Template template : templateRepository.findAllByWorkflowIdOrderByDisplayOrderAsc(workflow.getId())) {
                if (appliesToSubject(template.getSubjectScope(), forFamilyMember)) {
                    result.add(template);
                }
            }
        }
        return result;
    }

    private boolean appliesToSubject(SubjectScope scope, boolean forFamilyMember) {
        return switch (scope) {
            case BOTH -> true;
            case PROBAND_ONLY -> !forFamilyMember;
            case FAMILY_MEMBER_ONLY -> forFamilyMember;
        };
    }

    // The first applicable template - loaded unconditionally, its templateRule (if any) is
    // never evaluated (SRS 8.2), same rule for the proband's entry template and the first
    // template of a family member's pass.
    private Optional<Template> resolveFirstApplicableTemplate(boolean forFamilyMember) {
        List<Template> applicable = applicableTemplatesInOrder(forFamilyMember);
        return applicable.isEmpty() ? Optional.empty() : Optional.of(applicable.get(0));
    }

    // Next candidate after `current` in the subject-filtered flattened order, gated by its
    // templateRule (FR-5). Empty means this subject's walk has no further applicable template -
    // the whole session if this is the proband, or just this family member's pass otherwise.
    private Optional<Template> resolveNextApplicableTemplate(Template current, boolean forFamilyMember, Map<String, Object> facts) {
        List<Template> applicable = applicableTemplatesInOrder(forFamilyMember);
        int currentIndex = -1;
        for (int i = 0; i < applicable.size(); i++) {
            if (applicable.get(i).getId().equals(current.getId())) {
                currentIndex = i;
                break;
            }
        }
        // currentIndex == -1 (current no longer matches this subject's scope, e.g. an admin
        // edited it mid-session) falls back to scanning from the start rather than erroring.
        for (int i = currentIndex + 1; i < applicable.size(); i++) {
            Template candidate = applicable.get(i);
            if (candidate.getTemplateRule() == null || ruleEngine.evaluate(candidate.getTemplateRule(), facts)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // FR-6: a template can't be completed (left via next-template) while any of its required,
    // currently-visible questions have no answer on record for the named subject. Hidden
    // questions are evaluated with the exact same visibility rule/facts used to render the
    // template, so a question hidden from the subject can never block them - and a question that
    // only becomes required for a *different* subject never blocks someone else's pass either.
    private void validateTemplateCompletion(QuestionnaireSession session, Template template, Subject subject) {
        List<String> missing = new ArrayList<>();

        for (Question question : questionRepository.findAllByTemplateIdOrderByDisplayOrderAsc(template.getId())) {
            String visibilityRule = question.getVisibilityRule();
            boolean visible = visibilityRule == null || ruleEngine.evaluate(visibilityRule, subject.facts());
            if (!visible) {
                continue;
            }

            JsonNode validation = jsonColumnMapper.read(question.getValidation());
            boolean required = validation != null && validation.path("required").asBoolean(false);
            if (!required) {
                continue;
            }

            boolean answered = questionResponseRepository
                    .findTopBySessionIdAndQuestionIdAndRelationshipInstanceIdOrderByCreatedAtDesc(
                            session.getId(), question.getId(), subject.familyMemberId())
                    .map(r -> answerValidator.isAnswered(jsonColumnMapper.read(r.getAnswerValue())))
                    .orElse(false);

            if (!answered) {
                missing.add(question.getAnswerKey());
            }
        }

        if (!missing.isEmpty()) {
            throw new TemplateIncompleteException(
                    "Template '" + template.getCode() + "' cannot be completed - required question(s) unanswered: "
                            + String.join(", ", missing));
        }
    }

    // --- response building -----------------------------------------------------------------

    // The named subject's identity (null familyMember = the proband) and facts, bundled together
    // so every response-building step evaluates rules and scopes prefill lookups against the same
    // subject. Carrying the actual FamilyMember entity (not just its id) is what lets
    // resolvePosition/advancePosition read and write that specific subject's own position.
    private record Subject(FamilyMember familyMember, Map<String, Object> facts) {
        UUID familyMemberId() {
            return familyMember == null ? null : familyMember.getId();
        }
    }

    // Resolving a non-null familyMemberId goes through FamilyMemberService.findFamilyMemberOrThrow,
    // which scopes the lookup to this session's own proband - a familyMemberId belonging to a
    // different session's proband (spoofed or stale) is rejected here, before any position is ever
    // read or written for it.
    private Subject resolveSubject(QuestionnaireSession session, UUID familyMemberId) {
        if (familyMemberId == null) {
            return new Subject(null, personFactsMapper.toFacts(session.getPerson()));
        }
        FamilyMember familyMember = familyMemberService.findFamilyMemberOrThrow(session.getId(), familyMemberId);
        return new Subject(familyMember, familyMemberFactsMapper.toFacts(familyMember));
    }

    private String subjectLabel(Subject subject) {
        return subject.familyMember() == null ? "the proband" : "family member " + subject.familyMemberId();
    }

    private CurrentTemplateResponse buildTemplateResponse(QuestionnaireSession session, Template template, Subject subject) {
        List<QuestionAnswerView> questions = questionRepository
                .findAllByTemplateIdOrderByDisplayOrderAsc(template.getId()).stream()
                .map(q -> toView(session, q, subject))
                .toList();

        return new CurrentTemplateResponse(
                session.getId(), template.getWorkflow().getId(), template.getId(),
                template.getCode(), template.getName(), subject.familyMemberId(), questions, false);
    }

    // familyMemberId distinguishes "this family member's pass just finished" from "the whole
    // session just finished" (null) - both shapes reuse this same completed=true response, per
    // docs/superpowers/specs/2026-10-29-auth-removal-and-independent-passes-design.md §4.
    private CurrentTemplateResponse completedResponse(QuestionnaireSession session, UUID familyMemberId) {
        return new CurrentTemplateResponse(session.getId(), null, null, null, null, familyMemberId, List.of(), true);
    }

    private QuestionAnswerView toView(QuestionnaireSession session, Question question, Subject subject) {
        String visibilityRule = question.getVisibilityRule();
        boolean visible = visibilityRule == null || ruleEngine.evaluate(visibilityRule, subject.facts());

        JsonNode value = questionResponseRepository
                .findTopBySessionIdAndQuestionIdAndRelationshipInstanceIdOrderByCreatedAtDesc(
                        session.getId(), question.getId(), subject.familyMemberId())
                .map(r -> jsonColumnMapper.read(r.getAnswerValue()))
                .orElse(null);

        return new QuestionAnswerView(
                question.getId(), question.getAnswerKey(), question.getText(), question.getType(),
                question.getDisplayOrder(), jsonColumnMapper.read(question.getOptions()),
                jsonColumnMapper.read(question.getValidation()), visible, value,
                jsonColumnMapper.read(question.getFieldConfig()));
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

    // Session/request integrity: a completed session is read-only everywhere except
    // resume-at-template (FR-8.6), which is the one, explicit, intentional escape hatch that
    // un-completes it - every other mutating entry point calls this first.
    private void requireInProgress(QuestionnaireSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new ResourceInUseException(
                    "Session " + session.getId() + " is already completed and cannot be modified");
        }
    }
}
