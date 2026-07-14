import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { apiFetch, ApiError } from '../lib/http';
import type { Appointment } from '../types/appointment';
import './auth-pages.css';
import './dashboard-shared.css';
import './PublicClinicPage.css';

export function ConfirmAppointmentPage() {
  const { token } = useParams<{ token: string }>();

  const [isConfirming, setIsConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [appointment, setAppointment] = useState<Appointment | null>(null);

  async function handleConfirm() {
    if (!token) return;
    setError(null);
    setIsConfirming(true);
    try {
      const confirmed = await apiFetch<Appointment>(`/api/public/appointments/confirm/${token}`, {
        method: 'POST',
      });
      setAppointment(confirmed);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Não foi possível confirmar sua presença.');
    } finally {
      setIsConfirming(false);
    }
  }

  return (
    <div className="public-page">
      <div className="public-container">
        <div className="card public-cancel-card">
          <h1>Confirmar presença</h1>

          {!appointment && (
            <>
              <p className="subtitle">
                Ao confirmar, você indica que comparecerá à consulta agendada.
              </p>
              {error && <div className="form-error">{error}</div>}
              <button type="button" className="btn-primary btn-small" style={{ width: 'auto' }} onClick={handleConfirm} disabled={isConfirming}>
                {isConfirming ? 'Confirmando...' : 'Confirmar presença'}
              </button>
            </>
          )}

          {appointment && (
            <>
              <p className="form-success">Presença confirmada com sucesso.</p>
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
                {appointment.confirmedAt && (
                  <>
                    <dt>Confirmado em</dt>
                    <dd>{appointment.confirmedAt}</dd>
                  </>
                )}
              </dl>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
