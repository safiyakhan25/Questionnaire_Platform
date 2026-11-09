package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.Mutation;
import com.questionnaire.questionnaire_platform.entity.MutationResultType;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.repository.MutationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MutationAnswerWriterTest {

    @Mock
    private MutationRepository mutationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MutationAnswerWriter writer() {
        return new MutationAnswerWriter(mutationRepository);
    }

    @Test
    void reconcilesPersonMutationsFullyReplacingExisting() {
        Person person = new Person();
        person.setId(UUID.randomUUID());

        writer().applyToPerson(person, objectMapper.readTree("[{\"gene\":\"BRCA1\",\"mutation\":\"c.68_69delAG\"}]"));

        verify(mutationRepository).deleteAllByPersonId(person.getId());
        ArgumentCaptor<Mutation> captor = ArgumentCaptor.forClass(Mutation.class);
        verify(mutationRepository).save(captor.capture());
        assertThat(captor.getValue().getGene()).isEqualTo("BRCA1");
        assertThat(captor.getValue().getVariant()).isEqualTo("c.68_69delAG");
        assertThat(captor.getValue().getResult()).isEqualTo(MutationResultType.PENDING);
        assertThat(captor.getValue().getPerson()).isSameAs(person);
    }

    @Test
    void reconcilesFamilyMemberMutations() {
        FamilyMember familyMember = new FamilyMember();
        familyMember.setId(UUID.randomUUID());

        writer().applyToFamilyMember(familyMember, objectMapper.readTree("[{\"gene\":\"BRCA2\"}]"));

        verify(mutationRepository).deleteAllByFamilyMemberId(familyMember.getId());
        ArgumentCaptor<Mutation> captor = ArgumentCaptor.forClass(Mutation.class);
        verify(mutationRepository).save(captor.capture());
        assertThat(captor.getValue().getFamilyMember()).isSameAs(familyMember);
        assertThat(captor.getValue().getPerson()).isNull();
    }

    @Test
    void emptyArrayClearsWithoutRecreating() {
        Person person = new Person();
        person.setId(UUID.randomUUID());

        writer().applyToPerson(person, objectMapper.readTree("[]"));

        verify(mutationRepository).deleteAllByPersonId(person.getId());
        verify(mutationRepository, never()).save(any());
    }

    @Test
    void entryMissingGeneIsRejectedBeforeAnyRepositoryWrite() {
        Person person = new Person();
        person.setId(UUID.randomUUID());

        assertThatThrownBy(() -> writer().applyToPerson(person,
                objectMapper.readTree("[{\"gene\":\"BRCA1\"},{\"mutation\":\"missing gene\"}]")))
                .isInstanceOf(InvalidAnswerException.class);

        verifyNoInteractions(mutationRepository);
    }
}
