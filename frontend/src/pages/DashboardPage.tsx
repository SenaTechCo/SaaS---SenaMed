import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { DashboardLayout } from '../components/DashboardLayout';
import { useAuth } from '../context/AuthContext';
import { apiFetch, ApiError } from '../lib/http';
import type { DashboardSummary } from '../types/dashboard';
import './dashboard-shared.css';

const CLINIC_STATUS_LABELS: Record<string, string> = {
  TRIAL: 'Em teste',
  ACTIVE: 'Ativo',
  PAST_DUE: 'Pagamento atrasado',
  BLOCKED: 'Bloqueada',
};

export function DashboardPage() {
  const { user, clinic } = useAuth();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSlow, setIsSlow] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const slowTimer = setTimeout(() => {
      if (!cancelled) setIsSlow(true);
    }, 8000);

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      setIsSlow(false);
      try {
        const data = await apiFetch<DashboardSummary>('/api/dashboard/summary');
        if (cancelled) return;
        setSummary(data);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar o resumo da clínica.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
      clearTimeout(slowTimer);
    };
  }, []);

  const isDoctor = user?.role === 'DOCTOR';

  return (
    <DashboardLayout>
      <div className="page-header">
        <div>
          <h2>Início</h2>
          <p className="subtitle">
            {isDoctor ? 'Resumo da sua agenda.' : 'Resumo da clínica.'}
          </p>
        </div>
      </div>

      {isLoading && (
        <>
          <p className="loading-state">Carregando resumo...</p>
          {isSlow && (
            <p className="loading-state">
              Isso está demorando mais que o esperado — o servidor pode estar iniciando, aguarde alguns segundos.
            </p>
          )}
        </>
      )}
      {loadError && <div className="form-error">{loadError}</div>}

      {!isLoading && !loadError && summary && (
        <>
          <div className="stat-grid">
            <div className="stat-tile">
              <p className="stat-value">{summary.todayCount}</p>
              <p className="stat-label">Consultas hoje</p>
            </div>
            {summary.activeDoctorCount !== null && (
              <div className="stat-tile">
                <p className="stat-value">{summary.activeDoctorCount}</p>
                <p className="stat-label">Médicos ativos</p>
              </div>
            )}
            {clinic && (
              <div className="stat-tile">
                <p className="stat-value">{CLINIC_STATUS_LABELS[clinic.status] ?? clinic.status}</p>
                <p className="stat-label">
                  Status do plano — <Link to="/dashboard/plano">ver detalhes</Link>
                </p>
              </div>
            )}
          </div>

          <div className="card">
            <h3>Próximos agendamentos</h3>
            {summary.upcoming.length === 0 ? (
              <p className="empty-state">Nenhuma consulta futura agendada.</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Paciente</th>
                    {!isDoctor && <th>Médico</th>}
                    <th>Data</th>
                    <th>Horário</th>
                  </tr>
                </thead>
                <tbody>
                  {summary.upcoming.map((appointment) => (
                    <tr key={appointment.id}>
                      <td>{appointment.patientName}</td>
                      {!isDoctor && <td>{appointment.doctorName}</td>}
                      <td>{appointment.date}</td>
                      <td>{appointment.startTime} — {appointment.endTime}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </DashboardLayout>
  );
}
