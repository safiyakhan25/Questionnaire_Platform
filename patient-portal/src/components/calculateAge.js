// Whole years between a date-of-birth string (yyyy-mm-dd, as produced by a <input type="date">)
// and today, accounting for whether this year's birthday has happened yet. Returns null for an
// absent/unparseable/future date rather than throwing - callers treat "no age to show" as the
// normal case (most DATE questions aren't birth_date, and a half-typed date is unparseable).
export function calculateAge(dobString) {
  if (!dobString) return null;
  const dob = new Date(dobString);
  if (Number.isNaN(dob.getTime())) return null;

  const today = new Date();
  let age = today.getFullYear() - dob.getFullYear();
  const hasHadBirthdayThisYear =
    today.getMonth() > dob.getMonth() || (today.getMonth() === dob.getMonth() && today.getDate() >= dob.getDate());
  if (!hasHadBirthdayThisYear) age--;

  return age >= 0 ? age : null;
}
