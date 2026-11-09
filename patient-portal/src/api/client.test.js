import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getSessions, startAssessment, resumeAtTemplate, getPedigreeDiagram } from './client';

describe('client', () => {
  beforeEach(() => { global.fetch = vi.fn(); });

  it('getSessions fetches the session list', async () => {
    global.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ([{ sessionId: 's1', displayName: 'Safiya' }]) });
    const result = await getSessions();
    expect(result).toEqual([{ sessionId: 's1', displayName: 'Safiya' }]);
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/assessment/sessions'),
      expect.objectContaining({ method: 'GET' })
    );
  });

  it('startAssessment posts the given sessionId to resume, or null to start fresh', async () => {
    global.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ({ sessionId: 's1' }) });
    await startAssessment({ sessionId: 's1' });
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/assessment/start'),
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ sessionId: 's1' }) })
    );
  });

  it('throws ApiError carrying the backend message and status on a non-2xx response', async () => {
    global.fetch.mockResolvedValue({ ok: false, status: 404, json: async () => ({ message: 'not found' }) });
    await expect(getSessions()).rejects.toMatchObject({ status: 404, message: 'not found' });
  });

  it('sends an Accept: application/json header so an unmapped server error returns JSON, not an HTML error page', async () => {
    global.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ([]) });
    await getSessions();
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/assessment/sessions'),
      expect.objectContaining({ headers: expect.objectContaining({ Accept: 'application/json' }) })
    );
  });

  it('resumeAtTemplate posts to the backend\'s resume-here route', async () => {
    global.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ({ templateCode: 'DEMOGRAPHICS' }) });
    await resumeAtTemplate('s1', 't1');
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/assessment/s1/templates/t1/resume-here'),
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('getPedigreeDiagram resolves with the response blob on success, defaulting to svg', async () => {
    const blob = new Blob(['<svg/>'], { type: 'image/svg+xml' });
    global.fetch.mockResolvedValue({ ok: true, status: 200, blob: async () => blob });
    const result = await getPedigreeDiagram('s1');
    expect(result).toBe(blob);
    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('/assessment/s1/pedigree-diagram?format=svg'));
  });

  it('getPedigreeDiagram requests the given format', async () => {
    global.fetch.mockResolvedValue({ ok: true, status: 200, blob: async () => new Blob([]) });
    await getPedigreeDiagram('s1', 'png');
    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('/assessment/s1/pedigree-diagram?format=png'));
  });

  it('getPedigreeDiagram rejects with an ApiError built from the JSON error body on failure', async () => {
    global.fetch.mockResolvedValue({ ok: false, status: 503, json: async () => ({ message: 'Pedigree rendering service is not reachable' }) });
    await expect(getPedigreeDiagram('s1')).rejects.toMatchObject({ status: 503, message: 'Pedigree rendering service is not reachable' });
  });
});
