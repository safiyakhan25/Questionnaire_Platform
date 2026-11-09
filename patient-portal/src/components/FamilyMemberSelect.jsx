import { useEffect, useState } from 'react';
import { getFamilyMembers } from '../api/client';
import { familyMemberLabel } from './FamilyMemberLabel';
import { NativeSelect } from './base/select/select-native';
import { Checkbox } from './base/checkbox/checkbox';

// Backs the FAMILY_MEMBER_SELECT/FAMILY_MEMBER_MULTI_SELECT structured field kinds (see
// docs/superpowers/specs/2026-11-01-structured-pedigree-questions-design.md §4.2) - the subject's
// own id is excluded so a family member can never pick themselves as their own co-twin/adoptive
// parent/participant.
export default function FamilyMemberSelect({ sessionId, excludeId, multiple, value, label, onChange }) {
  const [members, setMembers] = useState([]);

  useEffect(() => {
    getFamilyMembers(sessionId).then(setMembers);
  }, [sessionId]);

  const selectable = members.filter((member) => member.id !== excludeId);

  if (!multiple) {
    return (
      <NativeSelect
        label={label}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || null)}
        options={[
          { label: 'Select...', value: '', disabled: true },
          ...selectable.map((member) => ({ label: familyMemberLabel(member), value: member.id })),
        ]}
      />
    );
  }

  const selected = Array.isArray(value) ? value : [];
  const toggle = (memberId) => {
    const next = selected.includes(memberId)
      ? selected.filter((id) => id !== memberId)
      : [...selected, memberId];
    onChange(next);
  };

  return (
    <fieldset className="space-y-2">
      {label && <legend className="mb-1.5 text-sm font-medium text-secondary">{label}</legend>}
      {selectable.map((member) => (
        <Checkbox
          key={member.id}
          label={familyMemberLabel(member)}
          isSelected={selected.includes(member.id)}
          onChange={() => toggle(member.id)}
        />
      ))}
    </fieldset>
  );
}
