import { useState } from 'react';
import FieldRenderer from './FieldRenderer';
import GroupListField from './GroupListField';
import { isEntryComplete } from './structuredFieldCompleteness';
import { RadioButton, RadioGroup } from './base/radio-buttons/radio-buttons';

// Top-level renderer for a STRUCTURED question (see
// docs/superpowers/specs/2026-11-01-structured-pedigree-questions-design.md §4.2). shape "object"
// gets a "does this apply?" Yes/No gate (every object-shaped question in this platform is
// optional, and null is how "No"/"not applicable" is submitted - the answer-writers already treat
// null that way). shape "list" has no gate - diseases/genetic_findings express "none reported" as
// an empty array, not null, so it's always shown.
export default function StructuredQuestionField({ question, sessionId, excludeId, onChange }) {
  const { shape, fields } = question.fieldConfig;

  return (
    <div className="mb-4">
      <h3 className="mb-2 text-sm font-semibold text-primary">{question.text}</h3>
      {shape === 'list' ? (
        <GroupListField
          fields={fields}
          value={question.value ?? []}
          onChange={onChange}
          renderField={(field, rowValue, onRowFieldChange) => (
            <FieldRenderer
              field={field}
              value={rowValue}
              onChange={(nextValue) => onRowFieldChange(field.name, nextValue)}
              sessionId={sessionId}
              excludeId={excludeId}
            />
          )}
        />
      ) : (
        <ObjectShapeField question={question} fields={fields} sessionId={sessionId} excludeId={excludeId} onChange={onChange} />
      )}
    </div>
  );
}

function ObjectShapeField({ question, fields, sessionId, excludeId, onChange }) {
  const [applies, setApplies] = useState(question.value != null);
  const [current, setCurrent] = useState(question.value ?? {});

  const handleYes = () => setApplies(true);
  const handleNo = () => {
    setApplies(false);
    // Reset the local draft too - otherwise flipping back to Yes shows fields the server no
    // longer has an answer for (this "No" already deleted them via `onChange(null)`).
    setCurrent({});
    onChange(null);
  };

  const updateField = (fieldName, fieldValue) => {
    // An empty string from a cleared TEXT/DATE field is never a meaningful value to persist -
    // storing null here (not '') avoids e.g. PregnancyOutcomeAnswerWriter's LocalDate.parse('')
    // blowing up with an unchecked exception downstream.
    const sanitized = fieldValue === '' ? null : fieldValue;
    const next = { ...current, [fieldName]: sanitized };
    setCurrent(next);
    // Don't submit a partial object - every answer-writer rejects one missing a required field
    // outright, so submitting on every keystroke would surface that rejection on the very first
    // field the patient fills in, for every multi-field structured question.
    if (isEntryComplete(next, fields)) {
      onChange(next);
    }
  };

  return (
    <fieldset className="mb-4">
      <p className="mb-2 text-sm font-medium text-secondary">Does this apply to this person?</p>
      <RadioGroup
        aria-label="Does this apply to this person?"
        value={applies ? 'yes' : 'no'}
        onChange={(v) => (v === 'yes' ? handleYes() : handleNo())}
        className="mb-4 flex-row gap-6"
      >
        <RadioButton value="yes" label="Yes" />
        <RadioButton value="no" label="No" />
      </RadioGroup>
      {applies &&
        fields.map((field) => (
          <FieldRenderer
            key={field.name}
            field={field}
            value={current[field.name]}
            onChange={(value) => updateField(field.name, value)}
            sessionId={sessionId}
            excludeId={excludeId}
          />
        ))}
    </fieldset>
  );
}
