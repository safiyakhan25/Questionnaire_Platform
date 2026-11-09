// Shared by GroupListField and StructuredQuestionField: a structured row/object must not be
// submitted to the backend until every field it marks `required` actually has a value - the
// answer-writers (DiseaseAnswerWriter etc.) reject a partial entry outright, so submitting one
// mid-edit (e.g. the moment a GROUP_LIST row is added, before any of its fields are filled in)
// always fails. See docs/superpowers/specs/2026-11-01-structured-pedigree-questions-design.md §4.2.
export function isFieldFilled(value) {
  if (value === null || value === undefined) return false;
  if (typeof value === 'string') return value.trim() !== '';
  if (Array.isArray(value)) return value.length > 0;
  return true;
}

export function isEntryComplete(entry, fields) {
  return fields.every((field) => !field.required || isFieldFilled(entry[field.name]));
}
