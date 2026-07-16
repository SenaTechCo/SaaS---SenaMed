import { useEffect, useState } from 'react';
import { CalendarOff } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { TimeOff } from '../types/doctor';

export function MyTimeOffPage() {
  const [timeOffs, setTimeOffs] = useState<TimeOff[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const list = await apiFetch<TimeOff[]>('/api/doctors/me/time-off');
        if (cancelled) return;
        setTimeOffs(list);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar suas folgas.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Minhas Folgas</h1>
          <p className="text-sm text-slate-500 mt-1">Períodos em que você não está disponível, cadastrados pela clínica.</p>
        </div>
      </div>

      {isLoading && (
        <div className="flex items-center justify-center py-24">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
        </div>
      )}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">
          {loadError}
        </div>
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
