import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { apiFetch, ApiError } from '../lib/http';
import type { Appointment } from '../types/appointment';
import './auth-pages.css';
import './dashboard-shared.css';
import './PublicClinicPage.css';

export function CancelAppointmentPage() {
  const { token } = useParams<{ token: string }>();

  const [isCancelling, setIsCancelling] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [appointment, setAppointment] = useState<Appointment | null>(null);

  async function handleCancel() {
    if (!token) return;
    setError(null);
    setIsCancelling(true);
    try {
      const cancelled = await apiFetch<Appointment>(`/api/public/appointments/cancel/${token}`, {
        method: 'POST',
      });
      setAppointment(cancelled);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Não foi possível cancelar o agendamento.');
    } finally {
      setIsCancelling(false);
    }
  }

  return (
    <div className="public-page">
      <div className="public-container">
        <div className="card public-cancel-card">
          <h1>Cancelar agendamento</h1>

          {!appointment && (
            <>
              <p className="subtitle">
                Ao confirmar, seu agendamento será cancelado. Esta ação não pode ser desfeita.
              </p>
              {error && <div className="form-error">{error}</div>}
              <button type="button" className="btn-primary btn-small" style={{ width: 'auto' }} onClick={handleCancel} disabled={isCancelling}>
                {isCancelling ? 'Cancelando...' : 'Cancelar agendamento'}
              </button>
            </>
          )}

          {appointment && (
            <>
              <p className="form-success">Agendamento cancelado com sucesso.</p>
              <dl className="public-summary">
                <dt>Médico</dt>
                <dd>{appointment.doctorName}</dd>
                <dt>Clínica</dt>
                <dd>{appointment.clinicName}</dd>
                <dt>Data</dt>
                <dd>{appointment.date}</dd>
                <dt>Horário</dt>
                <dd>{appointment.startTime} — {appointment.endTime}</dd>
                <dt>Paciente</dt>
                <dd>{appointment.patientName}</dd>
                <dt>Status</dt>
                <dd>{appointment.status === 'CANCELLED' ? 'Cancelado' : appointment.status}</dd>
              </dl>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
