package com.questionnaire.questionnaire_platform.engine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineTest {

    private final RuleEngine ruleEngine = new RuleEngine();

    @Test
    void equalityRuleEvaluatesTrueWhenFactMatches() {
        boolean result = ruleEngine.evaluate("{\"==\":[{\"var\":\"gender_at_birth\"},\"FEMALE\"]}",
                Map.of("gender_at_birth", "FEMALE"));
        assertThat(result).isTrue();
    }

    @Test
    void equalityRuleEvaluatesFalseWhenFactDiffers() {
        boolean result = ruleEngine.evaluate("{\"==\":[{\"var\":\"gender_at_birth\"},\"FEMALE\"]}",
                Map.of("gender_at_birth", "MALE"));
        assertThat(result).isFalse();
    }

    @Test
    void missingFactResolvesToNullNotAnError() {
        boolean result = ruleEngine.evaluate("{\"==\":[{\"var\":\"nonexistent_key\"},\"FEMALE\"]}", Map.of());
        assertThat(result).isFalse();
    }

    @Test
    void truthyCheckOperatorWorks() {
        assertThat(ruleEngine.evaluate("{\"!!\":[{\"var\":\"first_name\"}]}", Map.of("first_name", "Safiya"))).isTrue();
        assertThat(ruleEngine.evaluate("{\"!!\":[{\"var\":\"first_name\"}]}", Map.of())).isFalse();
    }

    @Test
    void andOrOperatorsCompose() {
        Map<String, Object> facts = Map.of("gender_at_birth", "FEMALE", "age", 30);
        assertThat(ruleEngine.evaluate(
                "{\"and\":[{\"==\":[{\"var\":\"gender_at_birth\"},\"FEMALE\"]},{\">\":[{\"var\":\"age\"},18]}]}", facts))
                .isTrue();
        assertThat(ruleEngine.evaluate(
                "{\"or\":[{\"==\":[{\"var\":\"gender_at_birth\"},\"MALE\"]},{\">\":[{\"var\":\"age\"},18]}]}", facts))
                .isTrue();
    }

    @Test
    void malformedRuleThrowsRuleEvaluationException() {
        assertThatThrownBy(() -> ruleEngine.evaluate("{\"notAnOperator\":[1,2]}", Map.of()))
                .isInstanceOf(RuleEvaluationException.class);
    }
}
