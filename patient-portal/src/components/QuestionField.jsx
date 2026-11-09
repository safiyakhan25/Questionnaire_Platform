import { RequiredMark, TextField, NumberField, DateField, DropdownField } from './SimpleFields';
import StructuredQuestionField from './StructuredQuestionField';
import CountryOfOriginField from './CountryOfOriginField';
import { Checkbox } from './base/checkbox/checkbox';
import { calculateAge } from './calculateAge';

export default function QuestionField({ question, onChange, sessionId, familyMemberId }) {
  const { id, answerKey, text, type, options, validation, visible, value } = question;

  if (!visible) return null;

  const fieldId = `question-${id ?? answerKey}`;
  const required = !!validation?.required;

  if (type === 'TEXT') {
    return <TextField fieldId={fieldId} label={text} value={value} required={required} validation={validation} onChange={onChange} />;
  }

  if (type === 'NUMBER') {
    return <NumberField fieldId={fieldId} label={text} value={value} required={required} validation={validation} onChange={onChange} />;
  }

  if (type === 'DATE') {
    const age = answerKey === 'birth_date' ? calculateAge(value) : null;
    return (
      <>
        <DateField fieldId={fieldId} label={text} value={value} required={required} onChange={onChange} />
        {age != null && <p className="text-sm text-tertiary -mt-3 mb-4">Current age: {age} years</p>}
      </>
    );
  }

  if (type === 'STRUCTURED') {
    return <StructuredQuestionField question={question} sessionId={sessionId} excludeId={familyMemberId} onChange={onChange} />;
  }

  // RADIO and DROPDOWN are both single-select from a closed options list - rendered identically as
  // a <select> rather than a list of individual radio buttons, which gets unwieldy past 2-3 options.
  if (type === 'RADIO' || type === 'DROPDOWN') {
    return <DropdownField fieldId={fieldId} label={text} value={value} required={required} options={options} onChange={onChange} />;
  }

  if (type === 'CHECKBOX' && answerKey === 'country_of_origin') {
    return <CountryOfOriginField fieldId={fieldId} label={text} value={value} required={required} options={options} onChange={onChange} />;
  }

  if (type === 'CHECKBOX') {
    const selected = Array.isArray(value) ? value : [];
    const toggle = (optionValue) => {
      const next = selected.includes(optionValue)
        ? selected.filter((v) => v !== optionValue)
        : [...selected, optionValue];
      onChange(next);
    };

    return (
      <fieldset className="mb-4">
        <div className="mb-2 flex items-center gap-1">
          <legend className="text-sm font-medium text-secondary">{text}</legend>
          <RequiredMark required={required} />
        </div>
        <div className="space-y-2">
          {(options ?? []).map((option, index) => (
            <Checkbox
              key={`${fieldId}-${index}`}
              label={option.label}
              isSelected={selected.includes(option.value)}
              onChange={() => toggle(option.value)}
            />
          ))}
        </div>
      </fieldset>
    );
  }

  return null;
}
