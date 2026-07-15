import { useEffect, useState } from 'react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { Appointment } from '../types/appointment';
import './dashboard-shared.css';

export function MyAgendaPage() {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const list = await apiFetch<Appointment[]>('/api/doctors/me/appointments');
        if (cancelled) return;
        setAppointments(list);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar sua agenda.');
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
          <h2>Minha Agenda</h2>
          <p className="subtitle">Suas consultas agendadas.</p>
        </div>
      </div>

      {isLoading && <p className="loading-state">Carregando consultas...</p>}
      {loadError && <div className="form-error">{loadError}</div>}

      {!isLoading && !loadError && (
        appointments.length === 0 ? (
          <p className="empty-state">Nenhuma consulta agendada ainda.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Paciente</th>
                <th>Data</th>
                <th>Horário</th>
                <th>Status</th>
                <th>Confirmado</th>
              </tr>
            </thead>
            <tbody>
              {appointments.map((appointment) => (
                <tr key={appointment.id}>
                  <td>{appointment.patientName}</td>
                  <td>{appointment.date}</td>
                  <td>{appointment.startTime} — {appointment.endTime}</td>
                  <td>
                    <span className={`badge ${appointment.status === 'CONFIRMED' ? 'badge-active' : 'badge-inactive'}`}>
                      {appointment.status === 'CONFIRMED' ? 'Confirmado' : 'Cancelado'}
                    </span>
                  </td>
                  <td>{appointment.confirmedAt ? 'Sim' : 'Não'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      )}
    </DashboardLayout>
  );
}
