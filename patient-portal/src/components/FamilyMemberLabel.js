export function familyMemberLabel(member) {
  return member.roles?.[0]?.relationshipType ?? 'Family member';
}
