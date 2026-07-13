import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { Doctor, TimeOff, TimeOffPayload } from '../types/doctor';
import './auth-pages.css';
import './dashboard-shared.css';

export function DoctorTimeOffPage() {
  const { id } = useParams<{ id: string }>();

  const [doctor, setDoctor] = useState<Doctor | null>(null);
  const [timeOffs, setTimeOffs] = useState<TimeOff[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [reason, setReason] = useState('');
  const [createError, setCreateError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const [doctorData, timeOffList] = await Promise.all([
          apiFetch<Doctor>(`/api/doctors/${id}`),
          apiFetch<TimeOff[]>(`/api/doctors/${id}/time-off`),
        ]);
        if (cancelled) return;
        setDoctor(doctorData);
        setTimeOffs(timeOffList);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar as folgas.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [id]);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!id) return;
    setCreateError(null);

    if (!startDate) {
      setCreateError('A data de início é obrigatória.');
      return;
    }

    const payload: TimeOffPayload = { startDate };
    if (endDate) payload.endDate = endDate;
    if (reason.trim()) payload.reason = reason.trim();

    setIsCreating(true);
    try {
      const created = await apiFetch<TimeOff>(`/api/doctors/${id}/time-off`, {
        method: 'POST',
        body: payload,
      });
      setTimeOffs((prev) => [...prev, created]);
      setStartDate('');
      setEndDate('');
      setReason('');
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : 'Não foi possível cadastrar a folga.');
    } finally {
      setIsCreating(false);
    }
  }

  return (
    <DashboardLayout>
      <Link className="back-link" to="/dashboard/medicos">
        ← Voltar para médicos
      </Link>

      <div className="page-header">
        <div>
          <h2>Folgas{doctor ? ` — ${doctor.name}` : ''}</h2>
          <p className="subtitle">Cadastre períodos em que o médico não estará disponível.</p>
        </div>
      </div>

      <div className="card">
        <h3>Nova folga</h3>
        {createError && <div className="form-error">{createError}</div>}
        <form onSubmit={handleCreate} noValidate>
          <div className="form-inline-row">
            <div className="form-field">
              <label htmlFor="start-date">Data de início</label>
              <input
                id="start-date"
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                required
              />
            </div>
            <div className="form-field">
              <label htmlFor="end-date">Data de fim (opcional)</label>
              <input id="end-date" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
            </div>
            <div className="form-field" style={{ flex: 2 }}>
              <label htmlFor="reason">Motivo (opcional)</label>
              <input id="reason" type="text" value={reason} onChange={(e) => setReason(e.target.value)} />
            </div>
          </div>
          <button type="submit" className="btn-primary btn-small" style={{ width: 'auto' }} disabled={isCreating}>
            {isCreating ? 'Salvando...' : 'Adicionar folga'}
          </button>
        </form>
      </div>

      {isLoading && <p className="loading-state">Carregando folgas...</p>}
      {loadError && <div className="form-error">{loadError}</div>}

      {!isLoading && !loadError && (
        timeOffs.length === 0 ? (
          <p className="empty-state">Nenhuma folga cadastrada ainda.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Início</th>
                <th>Fim</th>
                <th>Motivo</th>
              </tr>
            </thead>
            <tbody>
              {timeOffs.map((timeOff) => (
                <tr key={timeOff.id}>
                  <td>{timeOff.startDate}</td>
                  <td>{timeOff.endDate ?? '—'}</td>
                  <td>{timeOff.reason ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      )}
    </DashboardLayout>
  );
}
