import { TextField, NumberField, DateField, DropdownField } from './SimpleFields';
import FamilyMemberSelect from './FamilyMemberSelect';
import GroupListField from './GroupListField';

// Dispatches one FieldDef from a STRUCTURED question's field_config (see
// docs/superpowers/specs/2026-11-01-structured-pedigree-questions-design.md §4.2) to the matching
// control. Passes itself into GroupListField as a closure rather than GroupListField importing
// this module directly, avoiding a circular import between the two files.
export default function FieldRenderer({ field, value, onChange, sessionId, excludeId }) {
  const fieldId = `structured-field-${field.name}`;

  switch (field.type) {
    case 'TEXT':
      return <TextField fieldId={fieldId} label={field.label} value={value} required={field.required} validation={null} onChange={onChange} />;
    case 'NUMBER':
      return <NumberField fieldId={fieldId} label={field.label} value={value} required={field.required} validation={null} onChange={onChange} />;
    case 'DATE':
      return <DateField fieldId={fieldId} label={field.label} value={value} required={field.required} onChange={onChange} />;
    case 'DROPDOWN':
      return <DropdownField fieldId={fieldId} label={field.label} value={value} required={field.required} options={field.options} onChange={onChange} />;
    case 'FAMILY_MEMBER_SELECT':
      return (
        <div className="mb-2">
          <FamilyMemberSelect sessionId={sessionId} excludeId={excludeId} multiple={false} value={value} label={field.label} onChange={onChange} />
        </div>
      );
    case 'FAMILY_MEMBER_MULTI_SELECT':
      return (
        <div className="mb-2">
          <FamilyMemberSelect sessionId={sessionId} excludeId={excludeId} multiple value={value} label={field.label} onChange={onChange} />
        </div>
      );
    case 'GROUP_LIST':
      return (
        <div className="mb-2">
          <div className="mb-1.5 text-sm font-medium text-secondary">{field.label}</div>
          <GroupListField
            fields={field.fields}
            value={value}
            onChange={onChange}
            renderField={(rowField, rowValue, onRowFieldChange) => (
              <FieldRenderer
                field={rowField}
                value={rowValue}
                onChange={(nextValue) => onRowFieldChange(rowField.name, nextValue)}
                sessionId={sessionId}
                excludeId={excludeId}
              />
            )}
          />
        </div>
      );
    default:
      return null;
  }
}
