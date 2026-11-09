package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.QuestionCreateRequest;
import com.questionnaire.questionnaire_platform.dto.QuestionDto;
import com.questionnaire.questionnaire_platform.dto.QuestionUpdateRequest;
import com.questionnaire.questionnaire_platform.engine.ConfigurationValidator;
import com.questionnaire.questionnaire_platform.entity.Question;
import com.questionnaire.questionnaire_platform.entity.Template;
import com.questionnaire.questionnaire_platform.exception.DuplicateResourceException;
import com.questionnaire.questionnaire_platform.exception.ResourceInUseException;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import com.questionnaire.questionnaire_platform.repository.QuestionRepository;
import com.questionnaire.questionnaire_platform.repository.TemplateRepository;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final TemplateRepository templateRepository;
    private final JsonColumnMapper jsonColumnMapper;
    private final ConfigurationValidator configurationValidator;

    @Transactional
    public QuestionDto create(UUID templateId, QuestionCreateRequest request) {
        Template template = findTemplateOrThrow(templateId);
        configurationValidator.validateRule(request.visibilityRule(), "visibilityRule");
        configurationValidator.validateForType(request.type(), request.validation(), request.options(), request.fieldConfig());

        if (questionRepository.existsByAnswerKey(request.answerKey())) {
            throw new DuplicateResourceException(
                    "A question with answerKey '" + request.answerKey() + "' already exists");
        }
        if (questionRepository.existsByTemplateIdAndDisplayOrder(templateId, request.displayOrder())) {
            throw new DuplicateResourceException(
                    "A question with displayOrder " + request.displayOrder()
                            + " already exists in this template");
        }

        Question question = new Question();
        question.setTemplate(template);
        question.setAnswerKey(request.answerKey());
        question.setText(request.text());
        question.setType(request.type());
        question.setDisplayOrder(request.displayOrder());
        question.setOptions(jsonColumnMapper.write(request.options()));
        question.setValidation(jsonColumnMapper.write(request.validation()));
        question.setVisibilityRule(jsonColumnMapper.write(request.visibilityRule()));
        question.setFieldConfig(jsonColumnMapper.write(request.fieldConfig()));

        return toDto(questionRepository.save(question));
    }

    public List<QuestionDto> getAllByTemplate(UUID templateId) {
        findTemplateOrThrow(templateId);
        return questionRepository.findAllByTemplateIdOrderByDisplayOrderAsc(templateId).stream()
                .map(this::toDto)
                .toList();
    }

    public QuestionDto getById(UUID id) {
        return toDto(findQuestionOrThrow(id));
    }

    @Transactional
    public QuestionDto update(UUID id, QuestionUpdateRequest request) {
        Question question = findQuestionOrThrow(id);
        configurationValidator.validateRule(request.visibilityRule(), "visibilityRule");
        configurationValidator.validateForType(request.type(), request.validation(), request.options(), request.fieldConfig());
        if (questionRepository.existsByTemplateIdAndDisplayOrderAndIdNot(
                question.getTemplate().getId(), request.displayOrder(), id)) {
            throw new DuplicateResourceException(
                    "A question with displayOrder " + request.displayOrder()
                            + " already exists in this template");
        }
        question.setText(request.text());
        question.setType(request.type());
        question.setDisplayOrder(request.displayOrder());
        question.setOptions(jsonColumnMapper.write(request.options()));
        question.setValidation(jsonColumnMapper.write(request.validation()));
        question.setVisibilityRule(jsonColumnMapper.write(request.visibilityRule()));
        question.setFieldConfig(jsonColumnMapper.write(request.fieldConfig()));

        return toDto(question);
    }

    @Transactional
    public void delete(UUID id) {
        Question question = findQuestionOrThrow(id);
        if (questionRepository.hasResponses(id)) {
            throw new ResourceInUseException(
                    "Question '" + question.getAnswerKey() + "' already has responses and cannot be deleted");
        }
        questionRepository.delete(question);
    }

    private Question findQuestionOrThrow(UUID id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question " + id + " not found"));
    }

    private Template findTemplateOrThrow(UUID templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template " + templateId + " not found"));
    }

    private QuestionDto toDto(Question question) {
        return QuestionDto.from(question, jsonColumnMapper);
    }
}
