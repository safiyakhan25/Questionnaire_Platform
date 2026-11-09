import { useEffect, useState } from 'react';
import { addFamilyMember, getFamilyMembers } from '../api/client';
import { familyMemberLabel } from './FamilyMemberLabel';
import { Button } from './base/buttons/button';
import { NativeSelect } from './base/select/select-native';

export const RELATIONSHIP_TYPES = [
  'MOTHER', 'FATHER', 'BROTHER', 'SISTER', 'SON', 'DAUGHTER', 'SPOUSE',
  'HALF_BROTHER', 'HALF_SISTER', 'ADOPTED_SON', 'ADOPTED_DAUGHTER',
  'GRANDFATHER', 'GRANDMOTHER', 'UNCLE', 'AUNT', 'COUSIN', 'NIECE', 'NEPHEW',
];

export const MATERNAL_PATERNAL_SIDES = ['MATERNAL', 'PATERNAL', 'UNKNOWN'];

// The FAMILY_MEMBER_HUB template's own content: who's been added so far (click "Continue" to pick
// up their pass, wherever it left off) and the add-a-relative form. Adding only adds to the list -
// it does not enter their pass, same uniform "click Continue" path for every row, new or old.
export default function FamilyMemberHub({ sessionId, onContinue, prevSlot, nextSlot }) {
  const [members, setMembers] = useState([]);
  const [relationshipType, setRelationshipType] = useState(RELATIONSHIP_TYPES[0]);
  const [maternalPaternalSide, setMaternalPaternalSide] = useState(MATERNAL_PATERNAL_SIDES[0]);
  // '' = the proband (the default anchor) - any other value is an already-added family member's id,
  // letting a relative be anchored to another relative (e.g. a grandparent scoped to the mother)
  // without needing to enter that relative's own pass first.
  const [scopeId, setScopeId] = useState('');

  const refresh = () => getFamilyMembers(sessionId).then(setMembers);

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  const handleAdd = async (e) => {
    e.preventDefault();
    await addFamilyMember(sessionId, { relationshipType, maternalPaternalSide, scopeId: scopeId || null });
    await refresh();
  };

  return (
    <div>
      <h2 className="mb-4 text-display-xs font-semibold text-primary">Family members</h2>

      {members.length > 0 && (
        <ul className="mb-4 divide-y divide-secondary rounded-xl border border-secondary bg-primary">
          {members.map((member) => (
            <li key={member.id} className="px-4 py-2">
              <Button type="button" color="link-gray" size="sm" onClick={() => onContinue(member.id, familyMemberLabel(member))}>
                Continue as {familyMemberLabel(member)}
              </Button>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={handleAdd} className="max-w-sm space-y-4">
        <NativeSelect
          label="Relationship"
          value={relationshipType}
          onChange={(e) => setRelationshipType(e.target.value)}
          options={RELATIONSHIP_TYPES.map((type) => ({ label: type, value: type }))}
        />

        <NativeSelect
          label="Side"
          value={maternalPaternalSide}
          onChange={(e) => setMaternalPaternalSide(e.target.value)}
          options={MATERNAL_PATERNAL_SIDES.map((side) => ({ label: side, value: side }))}
        />

        <NativeSelect
          label="Related to"
          value={scopeId}
          onChange={(e) => setScopeId(e.target.value)}
          options={[
            { label: 'Proband', value: '' },
            ...members.map((member) => ({ label: familyMemberLabel(member), value: member.id })),
          ]}
        />

        <div className="flex items-center gap-3">
          <Button type="submit" color="primary" size="md">
            Add family member
          </Button>
          {prevSlot}
          {nextSlot}
        </div>
      </form>
    </div>
  );
}
