const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081/api/v1';

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json' };

  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });

  const data = await res.json();
  if (!res.ok) {
    throw new ApiError(res.status, data?.message ?? 'Request failed');
  }
  return data;
}

export const getSessions = () => request('/assessment/sessions');

export const startAssessment = ({ sessionId } = {}) =>
  request('/assessment/start', { method: 'POST', body: { sessionId: sessionId ?? null } });

const fmQuery = (familyMemberId) => (familyMemberId ? `?familyMemberId=${familyMemberId}` : '');

export const getCurrentTemplate = (sessionId, familyMemberId) =>
  request(`/assessment/${sessionId}/current-template${fmQuery(familyMemberId)}`);

export const nextTemplate = (sessionId, familyMemberId) =>
  request(`/assessment/${sessionId}/next-template${fmQuery(familyMemberId)}`, { method: 'POST' });

export const submitAnswer = (sessionId, { questionId, value, familyMemberId }) =>
  request(`/assessment/${sessionId}/answer`, {
    method: 'POST',
    body: { questionId, value, familyMemberId },
  });

export const getVisitedTemplates = (sessionId, familyMemberId) =>
  request(`/assessment/${sessionId}/visited-templates${fmQuery(familyMemberId)}`);

export const viewVisitedTemplate = (sessionId, templateId, familyMemberId) =>
  request(`/assessment/${sessionId}/templates/${templateId}${fmQuery(familyMemberId)}`);

export const resumeAtTemplate = (sessionId, templateId, familyMemberId) =>
  request(`/assessment/${sessionId}/templates/${templateId}/resume-here${fmQuery(familyMemberId)}`, { method: 'POST' });

export const addFamilyMember = (sessionId, { relationshipType, maternalPaternalSide, scopeId }) =>
  request(`/assessment/${sessionId}/family-members`, {
    method: 'POST',
    body: { relationshipType, maternalPaternalSide, scopeId },
  });

export const getFamilyMembers = (sessionId) =>
  request(`/assessment/${sessionId}/family-members`);

export const getPedigreeExport = (sessionId) =>
  request(`/assessment/${sessionId}/pedigree-export`);

// Not routed through request() - that helper always calls res.json(), which fails on the binary
// image bytes a successful render returns. Only the error path is JSON here.
export const getPedigreeDiagram = async (sessionId, format = 'svg') => {
  const res = await fetch(`${BASE_URL}/assessment/${sessionId}/pedigree-diagram?format=${format}`);
  if (!res.ok) {
    const data = await res.json().catch(() => null);
    throw new ApiError(res.status, data?.message ?? 'Request failed');
  }
  return res.blob();
};
