package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.Disease;
import com.questionnaire.questionnaire_platform.entity.DiseaseCatalog;
import com.questionnaire.questionnaire_platform.entity.DiseaseCodeSystemType;
import com.questionnaire.questionnaire_platform.entity.DiseaseStatusType;
import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.repository.DiseaseCatalogRepository;
import com.questionnaire.questionnaire_platform.repository.DiseaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiseaseAnswerWriterTest {

    @Mock
    private DiseaseRepository diseaseRepository;

    @Mock
    private DiseaseCatalogRepository diseaseCatalogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DiseaseAnswerWriter writer() {
        return new DiseaseAnswerWriter(diseaseRepository, diseaseCatalogRepository);
    }

    @Test
    void reconcilesPersonDiseasesFullyReplacingExisting() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        when(diseaseCatalogRepository.findByName("Diabetes")).thenReturn(Optional.empty());
        when(diseaseCatalogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        writer().applyToPerson(person, objectMapper.readTree("[{\"diseaseType\":\"Diabetes\",\"ageAtDiagnosis\":45}]"));

        verify(diseaseRepository).deleteAllByPersonId(person.getId());
        ArgumentCaptor<Disease> captor = ArgumentCaptor.forClass(Disease.class);
        verify(diseaseRepository).save(captor.capture());
        assertThat(captor.getValue().getDiseaseCatalog().getName()).isEqualTo("Diabetes");
        assertThat(captor.getValue().getCodeSystem()).isEqualTo(DiseaseCodeSystemType.CUSTOM);
        assertThat(captor.getValue().getCodeId()).isEqualTo("Diabetes");
        assertThat(captor.getValue().getStatus()).isEqualTo(DiseaseStatusType.REPORTED);
        assertThat(captor.getValue().getAgeAtDiagnosis()).isEqualTo(45);
        assertThat(captor.getValue().getPerson()).isSameAs(person);
    }

    @Test
    void reusesExistingCatalogEntryByName() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        DiseaseCatalog existing = new DiseaseCatalog();
        existing.setId(UUID.randomUUID());
        existing.setName("Diabetes");
        when(diseaseCatalogRepository.findByName("Diabetes")).thenReturn(Optional.of(existing));

        writer().applyToPerson(person, objectMapper.readTree("[{\"diseaseType\":\"Diabetes\"}]"));

        ArgumentCaptor<Disease> captor = ArgumentCaptor.forClass(Disease.class);
        verify(diseaseRepository).save(captor.capture());
        assertThat(captor.getValue().getDiseaseCatalog()).isSameAs(existing);
        verify(diseaseCatalogRepository, never()).save(any());
    }

    @Test
    void reconcilesFamilyMemberDiseases() {
        FamilyMember familyMember = new FamilyMember();
        familyMember.setId(UUID.randomUUID());

        writer().applyToFamilyMember(familyMember, objectMapper.readTree("[{\"diseaseType\":\"Breast Cancer\"}]"));

        verify(diseaseRepository).deleteAllByFamilyMemberId(familyMember.getId());
        ArgumentCaptor<Disease> captor = ArgumentCaptor.forClass(Disease.class);
        verify(diseaseRepository).save(captor.capture());
        assertThat(captor.getValue().getFamilyMember()).isSameAs(familyMember);
        assertThat(captor.getValue().getPerson()).isNull();
    }

    @Test
    void emptyArrayClearsWithoutRecreating() {
        Person person = new Person();
        person.setId(UUID.randomUUID());

        writer().applyToPerson(person, objectMapper.readTree("[]"));

        verify(diseaseRepository).deleteAllByPersonId(person.getId());
        verify(diseaseRepository, never()).save(any());
    }

    @Test
    void nullValueIsTreatedAsEmptyClear() {
        Person person = new Person();
        person.setId(UUID.randomUUID());

        writer().applyToPerson(person, objectMapper.readTree("null"));

        verify(diseaseRepository).deleteAllByPersonId(person.getId());
        verify(diseaseRepository, never()).save(any());
    }

    // Transactional-consistency proof at the unit level: parsing/validating the whole array
    // happens before any repository write, so a malformed entry never touches the DB at all -
    // not even a partial delete+recreate of the valid entries that came before it.
    @Test
    void entryMissingDiseaseTypeIsRejectedBeforeAnyRepositoryWrite() {
        Person person = new Person();
        person.setId(UUID.randomUUID());

        assertThatThrownBy(() -> writer().applyToPerson(person,
                objectMapper.readTree("[{\"diseaseType\":\"Valid\"},{\"ageAtDiagnosis\":45}]")))
                .isInstanceOf(InvalidAnswerException.class);

        verifyNoInteractions(diseaseRepository);
    }
}
