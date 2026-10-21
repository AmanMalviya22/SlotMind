import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const API = `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/api`;
const emptyData = { providers: [], appointments: [], slots: [], suggestions: [] };

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, { ...options, headers: { 'Content-Type': 'application/json', ...(options.headers || {}) } });
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try { const body = await response.json(); message = body.error || message; } catch { /* non-JSON error */ }
    throw new Error(message);
  }
  return response.json();
}

function App() {
  const [data, setData] = useState(emptyData);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const sequence = useRef(0);
  const load = useCallback(async () => {
    const current = ++sequence.current;
    try {
      const [providers, appointments, slots, suggestions] = await Promise.all([request('/providers'), request('/appointments'), request('/slots'), request('/suggestions')]);
      if (current === sequence.current) { setData({ providers, appointments, slots, suggestions }); setError(''); }
    } catch (e) { if (current === sequence.current) setError(e.message || 'Backend unavailable. Start Spring Boot on port 8080.'); }
  }, []);
  useEffect(() => { load(); const timer = setInterval(load, 5000); return () => clearInterval(timer); }, [load]);
  const mutate = async (operation) => { setBusy(true); try { await operation(); await load(); } catch (e) { setError(e.message || 'The operation could not be completed.'); } finally { setBusy(false); } };
  const status = (id, value) => mutate(() => request(`/providers/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status: value }) }));
  const decide = (id, action) => mutate(() => request(`/suggestions/${id}`, { method: 'PATCH', body: JSON.stringify({ action }) }));
  const pending = data.suggestions.filter((suggestion) => suggestion.status === 'PENDING');
  return <main>
    <header><div><span className="eyebrow">CAREBRIDGE / OPERATIONS</span><h1>Slot<span>Mind</span></h1><p>Rule-based appointment re-scheduling engine</p></div><button onClick={load} disabled={busy}>↻ Refresh</button></header>
    {error && <div className="error" role="alert">{error}</div>}
    <section className="stats"><Stat n={data.appointments.filter((a) => a.status === 'CONFIRMED').length} l="Confirmed"/><Stat n={data.appointments.filter((a) => a.status === 'RESCHEDULE_PENDING').length} l="Needs review"/><Stat n={pending.length} l="Suggestions"/><Stat n={data.slots.filter((s) => s.status === 'OPEN').length} l="Open slots"/></section>
    <div className="grid">
      <section className="card"><h2>Provider status</h2>{data.providers.map((provider) => <div className="provider" key={provider.id}><div><b>{provider.name}</b><small>{provider.specialty} · {provider.id}</small></div><label><span className="sr-only">Status for {provider.name}</span><select disabled={busy} value={provider.status} onChange={(e) => status(provider.id, e.target.value)}><option>AVAILABLE</option><option>BUSY</option><option>OFFLINE</option></select></label></div>)}</section>
      <section className="card wide"><h2>Rescheduling queue <small>auto-refreshes</small></h2>{pending.length === 0 ? <div className="empty">No pending recommendations. Set a provider to OFFLINE to trigger the workflow.</div> : pending.map((suggestion) => { const source = ['AI', 'RULE_BASED'].includes(suggestion.source) ? suggestion.source : 'UNKNOWN'; return <article className="suggestion" key={suggestion.id}><div className="suggestion-head"><b>{suggestion.patientName}</b><div className="badges"><span className="badge">{suggestion.triggerReason}</span><span className={`badge source-${source.toLowerCase()}`}>Source: {source}</span></div></div><p><strong>{suggestion.originalProviderName}</strong> → <strong>{suggestion.providerName}</strong> · {new Date(suggestion.recommendedTime).toLocaleString()}</p><div className="confidence">Confidence <b>{Math.round(suggestion.confidence * 100)}%</b></div><p className="reason">“{suggestion.reasoning}”</p><div><button className="accept" disabled={busy} onClick={() => decide(suggestion.id, 'ACCEPT')}>Accept recommendation</button><button className="reject" disabled={busy} onClick={() => decide(suggestion.id, 'REJECT')}>Reject</button></div></article>; })}</section>
    </div>
    <section className="card"><h2>Appointments & slots</h2><div className="table">{data.appointments.map((appointment) => <div className="row" key={appointment.id}><span><b>{appointment.patientName}</b><small>{appointment.type}</small></span><span>{appointment.providerName}<small>{new Date(appointment.startTime).toLocaleString()}</small></span><span className={`state ${appointment.status}`}>{appointment.status}</span></div>)}</div></section>
  </main>;
}
function Stat({ n, l }) { return <div className="stat"><b>{n}</b><span>{l}</span></div>; }
createRoot(document.getElementById('root')).render(<App/>);