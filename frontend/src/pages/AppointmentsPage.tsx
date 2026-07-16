import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { CalendarDays, Plus, X } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { Appointment, AppointmentPayload, AppointmentReschedulePayload } from '../types/appointment';
import type { Doctor } from '../types/doctor';

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

  const reschedulingAppointment =
    reschedulingId !== null ? appointments.find((a) => a.id === reschedulingId) : undefined;

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Consultas</h1>
          <p className="text-sm text-slate-500 mt-1">Todas as consultas agendadas da clínica.</p>
        </div>
        <button
          type="button"
          onClick={openCreateForm}
          className="flex items-center justify-center gap-2 bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm w-full sm:w-auto"
        >
          <Plus className="w-4 h-4" /> Nova consulta
        </button>
      </div>

      {isLoading && (
        <div className="flex flex-col items-center gap-3 py-24">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
          {isSlow && (
            <p className="text-sm text-slate-500 text-center">
              Isso está demorando mais que o esperado — o servidor pode estar iniciando, aguarde alguns segundos.
            </p>
          )}
        </div>
      )}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">{loadError}</div>
      )}
      {rowError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">{rowError}</div>
      )}

      {!isLoading && !loadError && (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] overflow-hidden">
          {appointments.length === 0 ? (
            <div className="px-6 py-16 text-center">
              <div className="flex flex-col items-center gap-3">
                <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
                  <CalendarDays className="w-6 h-6 text-slate-400" />
                </div>
                <p className="text-slate-500 text-sm">Nenhuma consulta agendada ainda.</p>
              </div>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[820px]">
                <thead>
                  <tr className="bg-slate-50/80 border-b border-slate-100">
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Paciente</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Médico</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Data</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Horário</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Confirmado</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {appointments.map((appointment) => (
                    <tr key={appointment.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/80 transition-colors">
                      <td className="px-5 py-3.5 text-sm text-slate-700 font-medium">{appointment.patientName}</td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">{appointment.doctorName}</td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">{appointment.date}</td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">
                        {appointment.startTime} — {appointment.endTime}
                      </td>
                      <td className="px-5 py-3.5 text-sm">
                        <span
                          className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                            appointment.status === 'CONFIRMED' ? 'bg-green-50 text-green-700' : 'bg-slate-100 text-slate-500'
                          }`}
                        >
                          {appointment.status === 'CONFIRMED' ? 'Confirmado' : 'Cancelado'}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">{appointment.confirmedAt ? 'Sim' : 'Não'}</td>
                      <td className="px-5 py-3.5 text-sm">
                        {appointment.status === 'CONFIRMED' && (
                          <div className="flex items-center gap-3 flex-wrap">
                            <button
                              type="button"
                              onClick={() => startReschedule(appointment)}
                              className="text-primary-600 hover:text-primary-700 text-sm font-medium"
                            >
                              Reagendar
                            </button>
                            <button
                              type="button"
                              onClick={() => handleCancel(appointment)}
                              disabled={cancellingId === appointment.id}
                              className="px-4 py-2.5 bg-red-600 text-white rounded-xl text-sm font-semibold hover:bg-red-700 disabled:opacity-50 transition-colors"
                            >
                              {cancellingId === appointment.id ? 'Cancelando...' : 'Cancelar'}
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {showCreateForm && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-[2px] flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center gap-3">
              <div className="flex-1">
                <h2 className="text-lg font-semibold text-slate-900">Cadastrar consulta</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowCreateForm(false)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleCreate} noValidate className="p-6 space-y-4">
              {createError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{createError}</div>
              )}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="create-doctor" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Médico
                  </label>
                  <select
                    id="create-doctor"
                    value={createForm.doctorId}
                    onChange={(e) => setCreateForm((f) => ({ ...f, doctorId: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  >
                    <option value="" disabled>
                      Selecione um médico
                    </option>
                    {activeDoctors.map((doctor) => (
                      <option key={doctor.id} value={doctor.id}>
                        {doctor.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="create-date" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Data
                  </label>
                  <input
                    id="create-date"
                    type="date"
                    min={todayAsInputValue()}
                    value={createForm.date}
                    onChange={(e) => setCreateForm((f) => ({ ...f, date: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="create-time" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Horário
                  </label>
                  <input
                    id="create-time"
                    type="time"
                    value={createForm.startTime}
                    onChange={(e) => setCreateForm((f) => ({ ...f, startTime: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="create-patient-name" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Nome do paciente
                  </label>
                  <input
                    id="create-patient-name"
                    type="text"
                    value={createForm.patientName}
                    onChange={(e) => setCreateForm((f) => ({ ...f, patientName: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="create-patient-email" className="block text-sm font-medium text-slate-700 mb-1.5">
                    E-mail do paciente
                  </label>
                  <input
                    id="create-patient-email"
                    type="email"
                    value={createForm.patientEmail}
                    onChange={(e) => setCreateForm((f) => ({ ...f, patientEmail: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="create-patient-phone" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Telefone (opcional)
                  </label>
                  <input
                    id="create-patient-phone"
                    type="tel"
                    value={createForm.patientPhone}
                    onChange={(e) => setCreateForm((f) => ({ ...f, patientPhone: e.target.value }))}
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>

                <label className="col-span-1 sm:col-span-2 flex items-start gap-2 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    checked={createForm.lgpdConsent}
                    onChange={(e) => setCreateForm((f) => ({ ...f, lgpdConsent: e.target.checked }))}
                    className="mt-0.5 w-4 h-4 rounded border-slate-300 text-primary-600 focus:ring-primary-200"
                  />
                  Paciente autorizou o uso dos seus dados para fins de agendamento, conforme a LGPD.
                </label>
              </div>
              <div className="flex gap-3 justify-end pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreateForm(false)}
                  className="px-4 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-sm font-medium hover:bg-slate-50 transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isCreating || !createForm.lgpdConsent}
                  className="px-5 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 shadow-sm disabled:opacity-50 transition-all"
                >
                  {isCreating ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {reschedulingId !== null && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-[2px] flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center gap-3">
              <div className="flex-1">
                <h2 className="text-lg font-semibold text-slate-900">
                  Reagendar consulta{reschedulingAppointment ? ` — ${reschedulingAppointment.patientName}` : ''}
                </h2>
              </div>
              <button
                type="button"
                onClick={cancelReschedule}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={(e) => handleSaveReschedule(e, reschedulingId)} noValidate className="p-6 space-y-4">
              {rescheduleError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{rescheduleError}</div>
              )}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="reschedule-date" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Nova data
                  </label>
                  <input
                    id="reschedule-date"
                    type="date"
                    min={todayAsInputValue()}
                    value={rescheduleForm.date}
                    onChange={(e) => setRescheduleForm((f) => ({ ...f, date: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="reschedule-time" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Novo horário
                  </label>
                  <input
                    id="reschedule-time"
                    type="time"
                    value={rescheduleForm.startTime}
                    onChange={(e) => setRescheduleForm((f) => ({ ...f, startTime: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
              </div>
              <div className="flex gap-3 justify-end pt-2">
                <button
                  type="button"
                  onClick={cancelReschedule}
                  className="px-4 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-sm font-medium hover:bg-slate-50 transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSavingReschedule}
                  className="px-5 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 shadow-sm disabled:opacity-50 transition-all"
                >
                  {isSavingReschedule ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
}
