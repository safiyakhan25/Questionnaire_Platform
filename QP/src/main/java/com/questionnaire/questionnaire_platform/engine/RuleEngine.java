package com.questionnaire.questionnaire_platform.engine;

import io.github.jamsesso.jsonlogic.JsonLogic;
import org.springframework.stereotype.Component;

import java.util.Map;

// Evaluates a JSONLogic rule against the current business-entity facts and returns only a
// boolean - it does not know whether the result means "show this question" or "load this
// template"; the caller decides. Same engine/contract as QuestionnairePlatform-PoC's
// rule-engine-poc (io.github.jamsesso:json-logic-java, chosen there over SpEL because it
// matches the platform's JSONB rule-storage design with no translation layer).
@Component
public class RuleEngine {

    private final JsonLogic jsonLogic = new JsonLogic();

    // A null rule means "no rule configured" - callers should treat this as unconditionally
    // true (FR-2: a question with no visibility_rule is unconditionally shown) before calling
    // evaluate(), rather than relying on this method to special-case null.
    public boolean evaluate(String rule, Map<String, Object> facts) {
        try {
            return Boolean.TRUE.equals(jsonLogic.apply(rule, facts));
        } catch (Exception e) {
            throw new RuleEvaluationException("Failed to evaluate JsonLogic rule.", e);
        }
    }
}
