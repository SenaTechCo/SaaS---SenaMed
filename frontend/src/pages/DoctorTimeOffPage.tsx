import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ArrowLeft, CalendarOff, Plus } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { Doctor, TimeOff, TimeOffPayload } from '../types/doctor';

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
      <Link
        className="inline-flex items-center gap-1 text-primary-600 hover:text-primary-700 text-sm font-medium mb-4"
        to="/dashboard/medicos"
      >
        <ArrowLeft className="w-4 h-4" /> Voltar para médicos
      </Link>

      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Folgas{doctor ? ` — ${doctor.name}` : ''}</h1>
          <p className="text-sm text-slate-500 mt-1">Cadastre períodos em que o médico não estará disponível.</p>
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-6 mb-6">
        <h2 className="text-base font-semibold text-slate-900 mb-4">Nova folga</h2>
        {createError && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">{createError}</div>
        )}
        <form onSubmit={handleCreate} noValidate className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="start-date" className="block text-sm font-medium text-slate-700 mb-1.5">
                Data de início
              </label>
              <input
                id="start-date"
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                required
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
              />
            </div>
            <div>
              <label htmlFor="end-date" className="block text-sm font-medium text-slate-700 mb-1.5">
                Data de fim (opcional)
              </label>
              <input
                id="end-date"
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
              />
            </div>
            <div className="col-span-1 sm:col-span-2">
              <label htmlFor="reason" className="block text-sm font-medium text-slate-700 mb-1.5">
                Motivo (opcional)
              </label>
              <input
                id="reason"
                type="text"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
              />
            </div>
          </div>
          <button
            type="submit"
            disabled={isCreating}
            className="flex items-center justify-center gap-2 bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm disabled:opacity-50 w-full sm:w-auto"
          >
            <Plus className="w-4 h-4" /> {isCreating ? 'Salvando...' : 'Adicionar folga'}
          </button>
        </form>
      </div>

      {isLoading && (
        <div className="flex items-center justify-center py-24">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
        </div>
      )}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">{loadError}</div>
      )}

      {!isLoading && !loadError && (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] overflow-hidden">
          {timeOffs.length === 0 ? (
            <div className="px-6 py-16 text-center">
              <div className="flex flex-col items-center gap-3">
                <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
                  <CalendarOff className="w-6 h-6 text-slate-400" />
                </div>
                <p className="text-slate-500 text-sm">Nenhuma folga cadastrada ainda.</p>
              </div>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[500px]">
                <thead>
                  <tr className="bg-slate-50/80 border-b border-slate-100">
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Início</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Fim</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Motivo</th>
                  </tr>
                </thead>
                <tbody>
                  {timeOffs.map((timeOff) => (
                    <tr key={timeOff.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/80 transition-colors">
                      <td className="px-5 py-3.5 text-sm text-slate-700">{timeOff.startDate}</td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">{timeOff.endDate ?? '—'}</td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">{timeOff.reason ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </DashboardLayout>
  );
}
