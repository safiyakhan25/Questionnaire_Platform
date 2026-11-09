import { useState } from 'react';
import { isEntryComplete } from './structuredFieldCompleteness';
import { Button } from './base/buttons/button';

// Repeatable rows of sub-fields (the GROUP_LIST structured field kind - see
// docs/superpowers/specs/2026-11-01-structured-pedigree-questions-design.md §4.2). Rows are kept
// as local draft state, seeded once from `value` and never resynced from later prop changes (same
// uncontrolled-after-mount convention this codebase already uses for RADIO/CHECKBOX/DROPDOWN) -
// this is what lets an in-progress, still-incomplete row stay on screen across a refetch. Only the
// *complete* rows (every field it marks `required` has a value) are ever passed to `onChange` -
// the answer-writers (DiseaseAnswerWriter etc.) reject a partial entry outright, so a row that
// isn't finished yet is never submitted. `renderField` is supplied by the caller (FieldRenderer) so
// this component has no dependency on field-type dispatch itself.
export default function GroupListField({ fields, value, onChange, renderField }) {
  const [rows, setRows] = useState(() => (Array.isArray(value) ? value : []));

  const commit = (nextRows) => {
    setRows(nextRows);
    onChange(nextRows.filter((row) => isEntryComplete(row, fields)));
  };

  const addRow = () => commit([...rows, {}]);

  const removeRow = (index) => commit(rows.filter((_, i) => i !== index));

  const updateRowField = (index, fieldName, fieldValue) => {
    // An empty string from a cleared TEXT/DATE field is never meaningful to persist - see the
    // matching note in StructuredQuestionField.jsx's updateField.
    const sanitized = fieldValue === '' ? null : fieldValue;
    commit(rows.map((row, i) => (i === index ? { ...row, [fieldName]: sanitized } : row)));
  };

  return (
    <div className="mb-4">
      {rows.map((row, index) => (
        <div key={index} className="mb-2 rounded-xl border border-secondary p-3">
          {fields.map((field) => (
            <div key={field.name}>
              {renderField(field, row[field.name], (fieldName, fieldValue) => updateRowField(index, fieldName, fieldValue))}
            </div>
          ))}
          <Button type="button" color="link-destructive" size="sm" onClick={() => removeRow(index)}>
            Remove
          </Button>
        </div>
      ))}
      <Button type="button" color="link-color" size="sm" onClick={addRow}>
        Add
      </Button>
    </div>
  );
}
