import { useEffect, useState } from 'react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { TimeOff } from '../types/doctor';
import './dashboard-shared.css';

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
      <div className="page-header">
        <div>
          <h2>Minhas Folgas</h2>
          <p className="subtitle">Períodos em que você não está disponível, cadastrados pela clínica.</p>
        </div>
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
