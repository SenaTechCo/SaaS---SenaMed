import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { Appointment, AppointmentPayload, AppointmentReschedulePayload } from '../types/appointment';
import type { Doctor } from '../types/doctor';
import './dashboard-shared.css';

function todayAsInputValue(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

interface CreateForm {
  doctorId: string;
  date: string;
  startTime: string;
  patientName: string;
  patientEmail: string;
  patientPhone: string;
  lgpdConsent: boolean;
}

const emptyCreateForm: CreateForm = {
  doctorId: '',
  date: '',
  startTime: '',
  patientName: '',
  patientEmail: '',
  patientPhone: '',
  lgpdConsent: false,
};

export function AppointmentsPage() {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSlow, setIsSlow] = useState(false);

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState<CreateForm>(emptyCreateForm);
  const [createError, setCreateError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  const [rowError, setRowError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<number | null>(null);

  const [reschedulingId, setReschedulingId] = useState<number | null>(null);
  const [rescheduleForm, setRescheduleForm] = useState<AppointmentReschedulePayload>({ date: '', startTime: '' });
  const [rescheduleError, setRescheduleError] = useState<string | null>(null);
  const [isSavingReschedule, setIsSavingReschedule] = useState(false);

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
        const [appointmentsList, doctorsList] = await Promise.all([
          apiFetch<Appointment[]>('/api/appointments'),
          apiFetch<Doctor[]>('/api/doctors'),
        ]);
        if (cancelled) return;
        setAppointments(appointmentsList);
        setDoctors(doctorsList);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar as consultas.');
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

  const activeDoctors = doctors.filter((doctor) => doctor.active);

  function openCreateForm() {
    setCreateForm(emptyCreateForm);
    setCreateError(null);
    setShowCreateForm(true);
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateError(null);
    setIsCreating(true);
    try {
      const payload: AppointmentPayload = {
        doctorId: Number(createForm.doctorId),
        date: createForm.date,
        startTime: createForm.startTime,
        patientName: createForm.patientName,
        patientEmail: createForm.patientEmail,
        patientPhone: createForm.patientPhone.trim() ? createForm.patientPhone.trim() : null,
        lgpdConsent: createForm.lgpdConsent,
      };
      const created = await apiFetch<Appointment>('/api/appointments', {
        method: 'POST',
        body: payload,
      });
      setAppointments((prev) => [...prev, created].sort((a, b) => (a.date + a.startTime).localeCompare(b.date + b.startTime)));
      setShowCreateForm(false);
      setCreateForm(emptyCreateForm);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setCreateError(err.message || 'Este médico já tem uma consulta marcada para este horário.');
      } else if (err instanceof ApiError) {
        setCreateError(err.message);
      } else {
        setCreateError('Não foi possível cadastrar a consulta. Tente novamente.');
      }
    } finally {
      setIsCreating(false);
    }
  }

  async function handleCancel(appointment: Appointment) {
    setRowError(null);
    const confirmed = window.confirm(`Cancelar a consulta de "${appointment.patientName}"?`);
    if (!confirmed) return;

    setCancellingId(appointment.id);
    try {
      const updated = await apiFetch<Appointment>(`/api/appointments/${appointment.id}/cancel`, { method: 'POST' });
      setAppointments((prev) => prev.map((a) => (a.id === appointment.id ? updated : a)));
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : 'Não foi possível cancelar a consulta.');
    } finally {
      setCancellingId(null);
    }
  }

  function startReschedule(appointment: Appointment) {
    setReschedulingId(appointment.id);
    setRescheduleForm({ date: appointment.date, startTime: appointment.startTime });
    setRescheduleError(null);
  }

  function cancelReschedule() {
    setReschedulingId(null);
    setRescheduleError(null);
  }

  async function handleSaveReschedule(event: FormEvent<HTMLFormElement>, appointmentId: number) {
    event.preventDefault();
    setRescheduleError(null);
    setIsSavingReschedule(true);
    try {
      const updated = await apiFetch<Appointment>(`/api/appointments/${appointmentId}`, {
        method: 'PATCH',
        body: rescheduleForm,
      });
      setAppointments((prev) => prev.map((a) => (a.id === appointmentId ? updated : a)));
      setReschedulingId(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setRescheduleError(err.message || 'Este médico já tem uma consulta marcada para este horário.');
      } else {
        setRescheduleError(err instanceof ApiError ? err.message : 'Não foi possível reagendar a consulta.');
      }
    } finally {
      setIsSavingReschedule(false);
    }
  }

  return (
    <DashboardLayout>
      <div className="page-header">
        <div>
          <h2>Consultas</h2>
          <p className="subtitle">Todas as consultas agendadas da clínica.</p>
        </div>
        <button type="button" className="btn-primary btn-small" style={{ width: 'auto' }} onClick={openCreateForm}>
          Nova consulta
        </button>
      </div>

      {showCreateForm && (
        <div className="card">
          <h3>Cadastrar consulta</h3>
          {createError && <div className="form-error">{createError}</div>}
          <form onSubmit={handleCreate} noValidate>
            <div className="form-inline-row">
              <div className="form-field">
                <label htmlFor="create-doctor">Médico</label>
                <select
                  id="create-doctor"
                  value={createForm.doctorId}
                  onChange={(e) => setCreateForm((f) => ({ ...f, doctorId: e.target.value }))}
                  required
                >
                  <option value="" disabled>Selecione um médico</option>
                  {activeDoctors.map((doctor) => (
                    <option key={doctor.id} value={doctor.id}>{doctor.name}</option>
                  ))}
                </select>
              </div>
              <div className="form-field">
                <label htmlFor="create-date">Data</label>
                <input
                  id="create-date"
                  type="date"
                  min={todayAsInputValue()}
                  value={createForm.date}
                  onChange={(e) => setCreateForm((f) => ({ ...f, date: e.target.value }))}
                  required
                />
              </div>
              <div className="form-field">
                <label htmlFor="create-time">Horário</label>
                <input
                  id="create-time"
                  type="time"
                  value={createForm.startTime}
                  onChange={(e) => setCreateForm((f) => ({ ...f, startTime: e.target.value }))}
                  required
                />
              </div>
            </div>
            <div className="form-inline-row">
              <div className="form-field">
                <label htmlFor="create-patient-name">Nome do paciente</label>
                <input
                  id="create-patient-name"
                  type="text"
                  value={createForm.patientName}
                  onChange={(e) => setCreateForm((f) => ({ ...f, patientName: e.target.value }))}
                  required
                />
              </div>
              <div className="form-field">
                <label htmlFor="create-patient-email">E-mail do paciente</label>
                <input
                  id="create-patient-email"
                  type="email"
                  value={createForm.patientEmail}
                  onChange={(e) => setCreateForm((f) => ({ ...f, patientEmail: e.target.value }))}
                  required
                />
              </div>
              <div className="form-field">
                <label htmlFor="create-patient-phone">Telefone (opcional)</label>
                <input
                  id="create-patient-phone"
                  type="tel"
                  value={createForm.patientPhone}
                  onChange={(e) => setCreateForm((f) => ({ ...f, patientPhone: e.target.value }))}
                />
              </div>
            </div>

            <label className="lgpd-consent">
              <input
                type="checkbox"
                checked={createForm.lgpdConsent}
                onChange={(e) => setCreateForm((f) => ({ ...f, lgpdConsent: e.target.checked }))}
              />
              Paciente autorizou o uso dos seus dados para fins de agendamento, conforme a LGPD.
            </label>

            <div className="inline-actions">
              <button
                type="submit"
                className="btn-primary btn-small"
                style={{ width: 'auto' }}
                disabled={isCreating || !createForm.lgpdConsent}
              >
                {isCreating ? 'Salvando...' : 'Salvar'}
              </button>
              <button type="button" className="btn-secondary" onClick={() => setShowCreateForm(false)}>
                Cancelar
              </button>
            </div>
          </form>
        </div>
      )}

      {isLoading && (
        <>
          <p className="loading-state">Carregando consultas...</p>
          {isSlow && (
            <p className="loading-state">
              Isso está demorando mais que o esperado — o servidor pode estar iniciando, aguarde alguns segundos.
            </p>
          )}
        </>
      )}
      {loadError && <div className="form-error">{loadError}</div>}
      {rowError && <div className="form-error">{rowError}</div>}

      {!isLoading && !loadError && (
        appointments.length === 0 ? (
          <p className="empty-state">Nenhuma consulta agendada ainda.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Paciente</th>
                <th>Médico</th>
                <th>Data</th>
                <th>Horário</th>
                <th>Status</th>
                <th>Confirmado</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {appointments.map((appointment) =>
                reschedulingId === appointment.id ? (
                  <tr key={appointment.id}>
                    <td colSpan={7}>
                      {rescheduleError && <div className="form-error">{rescheduleError}</div>}
                      <form onSubmit={(e) => handleSaveReschedule(e, appointment.id)} noValidate>
                        <div className="form-inline-row">
                          <div className="form-field">
                            <label htmlFor={`reschedule-date-${appointment.id}`}>Nova data</label>
                            <input
                              id={`reschedule-date-${appointment.id}`}
                              type="date"
                              min={todayAsInputValue()}
                              value={rescheduleForm.date}
                              onChange={(e) => setRescheduleForm((f) => ({ ...f, date: e.target.value }))}
                              required
                            />
                          </div>
                          <div className="form-field">
                            <label htmlFor={`reschedule-time-${appointment.id}`}>Novo horário</label>
                            <input
                              id={`reschedule-time-${appointment.id}`}
                              type="time"
                              value={rescheduleForm.startTime}
                              onChange={(e) => setRescheduleForm((f) => ({ ...f, startTime: e.target.value }))}
                              required
                            />
                          </div>
                        </div>
                        <div className="inline-actions">
                          <button
                            type="submit"
                            className="btn-primary btn-small"
                            style={{ width: 'auto' }}
                            disabled={isSavingReschedule}
                          >
                            {isSavingReschedule ? 'Salvando...' : 'Salvar'}
                          </button>
                          <button type="button" className="btn-secondary" onClick={cancelReschedule}>
                            Cancelar
                          </button>
                        </div>
                      </form>
                    </td>
                  </tr>
                ) : (
                  <tr key={appointment.id}>
                    <td>{appointment.patientName}</td>
                    <td>{appointment.doctorName}</td>
                    <td>{appointment.date}</td>
                    <td>{appointment.startTime} — {appointment.endTime}</td>
                    <td>
                      <span className={`badge ${appointment.status === 'CONFIRMED' ? 'badge-active' : 'badge-inactive'}`}>
                        {appointment.status === 'CONFIRMED' ? 'Confirmado' : 'Cancelado'}
                      </span>
                    </td>
                    <td>{appointment.confirmedAt ? 'Sim' : 'Não'}</td>
                    <td>
                      {appointment.status === 'CONFIRMED' && (
                        <div className="inline-actions">
                          <button type="button" className="btn-link" onClick={() => startReschedule(appointment)}>
                            Reagendar
                          </button>
                          <button
                            type="button"
                            className="btn-danger"
                            onClick={() => handleCancel(appointment)}
                            disabled={cancellingId === appointment.id}
                          >
                            {cancellingId === appointment.id ? 'Cancelando...' : 'Cancelar'}
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ),
              )}
            </tbody>
          </table>
        )
      )}
    </DashboardLayout>
  );
}
