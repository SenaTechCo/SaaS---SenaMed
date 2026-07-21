import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, Eye, Plus, X } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { AppointmentCalendar } from '../components/AppointmentCalendar';
import { AppointmentDetailModal } from '../components/appointments/AppointmentDetailModal';
import { apiFetch, ApiError } from '../lib/http';
import type { Appointment, AppointmentReschedulePayload } from '../types/appointment';
import type { AvailabilitySlot, Doctor } from '../types/doctor';

function appointmentStatusClasses(status: Appointment['status']): string {
  if (status === 'CONFIRMED') return 'bg-green-50 text-green-700';
  if (status === 'ATTENDED') return 'bg-blue-50 text-blue-700';
  if (status === 'NO_SHOW') return 'bg-red-50 text-red-700';
  return 'bg-slate-100 text-slate-500';
}

function appointmentStatusLabel(status: Appointment['status']): string {
  if (status === 'CONFIRMED') return 'Confirmado';
  if (status === 'ATTENDED') return 'Atendido';
  if (status === 'NO_SHOW') return 'Faltou';
  return 'Cancelado';
}

function todayAsInputValue(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

const MONTH_NAMES = [
  'Janeiro',
  'Fevereiro',
  'Março',
  'Abril',
  'Maio',
  'Junho',
  'Julho',
  'Agosto',
  'Setembro',
  'Outubro',
  'Novembro',
  'Dezembro',
];

function startOfWeekLocal(date: Date): Date {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const jsDay = d.getDay();
  const isoDay = jsDay === 0 ? 7 : jsDay;
  d.setDate(d.getDate() + (1 - isoDay));
  return d;
}

type ViewMode = 'lista' | 'mes' | 'semana' | 'dia';

type DetailModalState =
  | { mode: 'create'; initialDoctorId: number | null; initialDate: string; initialStartTime: string | null }
  | { mode: 'view'; appointmentId: number };

export function AppointmentsPage() {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSlow, setIsSlow] = useState(false);

  const [detailModal, setDetailModal] = useState<DetailModalState | null>(null);

  const [attendingId, setAttendingId] = useState<number | null>(null);
  const [markingNoShowId, setMarkingNoShowId] = useState<number | null>(null);

  const [rowError, setRowError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<number | null>(null);

  const [reschedulingId, setReschedulingId] = useState<number | null>(null);
  const [rescheduleForm, setRescheduleForm] = useState<AppointmentReschedulePayload>({ date: '', startTime: '' });
  const [rescheduleError, setRescheduleError] = useState<string | null>(null);
  const [isSavingReschedule, setIsSavingReschedule] = useState(false);

  const [viewMode, setViewMode] = useState<ViewMode>('lista');
  const [referenceDate, setReferenceDate] = useState<Date>(new Date());
  const [filterDoctorId, setFilterDoctorId] = useState<number | null>(null);
  const [filterAvailability, setFilterAvailability] = useState<AvailabilitySlot[]>([]);

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
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar os agendamentos.');
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

  useEffect(() => {
    if (viewMode === 'lista' || filterDoctorId === null) {
      setFilterAvailability([]);
      return;
    }

    let cancelled = false;
    async function loadAvailability() {
      try {
        const slots = await apiFetch<AvailabilitySlot[]>(`/api/doctors/${filterDoctorId}/availability`);
        if (!cancelled) setFilterAvailability(slots);
      } catch {
        if (!cancelled) setFilterAvailability([]);
      }
    }

    loadAvailability();
    return () => {
      cancelled = true;
    };
  }, [filterDoctorId, viewMode]);

  const activeDoctors = doctors.filter((doctor) => doctor.active);

  function openCreateModal(prefill?: { doctorId?: number | null; date?: string; startTime?: string | null }) {
    setDetailModal({
      mode: 'create',
      initialDoctorId: prefill?.doctorId ?? null,
      initialDate: prefill?.date ?? '',
      initialStartTime: prefill?.startTime ?? null,
    });
  }

  function openViewModal(appointment: Appointment) {
    setDetailModal({ mode: 'view', appointmentId: appointment.id });
  }

  function handleSlotClick(params: { doctorId: number | null; date: string; time: string | null }) {
    openCreateModal({ doctorId: params.doctorId, date: params.date, startTime: params.time });
  }

  function handleDetailSaved(updated: Appointment) {
    setAppointments((prev) => {
      if (prev.some((a) => a.id === updated.id)) {
        return prev.map((a) => (a.id === updated.id ? updated : a));
      }
      return [...prev, updated].sort((a, b) => (a.date + a.startTime).localeCompare(b.date + b.startTime));
    });
  }

  async function handleCancel(appointment: Appointment) {
    setRowError(null);
    const confirmed = window.confirm(`Cancelar o agendamento de "${appointment.patientName}"?`);
    if (!confirmed) return;

    setCancellingId(appointment.id);
    try {
      const updated = await apiFetch<Appointment>(`/api/appointments/${appointment.id}/cancel`, { method: 'POST' });
      setAppointments((prev) => prev.map((a) => (a.id === appointment.id ? updated : a)));
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : 'Não foi possível cancelar o agendamento.');
    } finally {
      setCancellingId(null);
    }
  }

  async function handleMarkAttended(appointment: Appointment) {
    setRowError(null);
    setAttendingId(appointment.id);
    try {
      const updated = await apiFetch<Appointment>(`/api/appointments/${appointment.id}/atender`, { method: 'PATCH' });
      setAppointments((prev) => prev.map((a) => (a.id === appointment.id ? updated : a)));
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : 'Não foi possível marcar o agendamento como atendido.');
    } finally {
      setAttendingId(null);
    }
  }

  async function handleMarkNoShow(appointment: Appointment) {
    setRowError(null);
    setMarkingNoShowId(appointment.id);
    try {
      const updated = await apiFetch<Appointment>(`/api/appointments/${appointment.id}/faltou`, { method: 'PATCH' });
      setAppointments((prev) => prev.map((a) => (a.id === appointment.id ? updated : a)));
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : 'Não foi possível marcar falta neste agendamento.');
    } finally {
      setMarkingNoShowId(null);
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
        setRescheduleError(err.message || 'Este médico já tem um agendamento marcado para este horário.');
      } else {
        setRescheduleError(err instanceof ApiError ? err.message : 'Não foi possível reagendar o agendamento.');
      }
    } finally {
      setIsSavingReschedule(false);
    }
  }

  const reschedulingAppointment =
    reschedulingId !== null ? appointments.find((a) => a.id === reschedulingId) : undefined;

  function shiftReferenceDate(direction: 1 | -1) {
    setReferenceDate((prev) => {
      const next = new Date(prev);
      if (viewMode === 'mes') {
        next.setMonth(next.getMonth() + direction);
      } else if (viewMode === 'semana') {
        next.setDate(next.getDate() + direction * 7);
      } else {
        next.setDate(next.getDate() + direction);
      }
      return next;
    });
  }

  function formatPeriodLabel(): string {
    if (viewMode === 'mes') {
      return `${MONTH_NAMES[referenceDate.getMonth()]} ${referenceDate.getFullYear()}`;
    }
    if (viewMode === 'semana') {
      const start = startOfWeekLocal(referenceDate);
      const end = new Date(start);
      end.setDate(end.getDate() + 6);
      if (start.getMonth() === end.getMonth()) {
        return `${start.getDate()} a ${end.getDate()} de ${MONTH_NAMES[end.getMonth()]} de ${end.getFullYear()}`;
      }
      return `${start.getDate()} de ${MONTH_NAMES[start.getMonth()]} a ${end.getDate()} de ${MONTH_NAMES[end.getMonth()]} de ${end.getFullYear()}`;
    }
    return `${referenceDate.getDate()} de ${MONTH_NAMES[referenceDate.getMonth()]} de ${referenceDate.getFullYear()}`;
  }

  const calendarAppointments = appointments.filter((a) => filterDoctorId == null || a.doctorId === filterDoctorId);

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Agendamentos</h1>
          <p className="text-sm text-slate-500 mt-1">Todos os agendamentos da clínica.</p>
        </div>
        <button
          type="button"
          onClick={() => openCreateModal()}
          className="flex items-center justify-center gap-2 bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm w-full sm:w-auto"
        >
          <Plus className="w-4 h-4" /> Novo agendamento
        </button>
      </div>

      <div className="flex flex-col gap-4 mb-6">
        <div className="flex flex-wrap items-center gap-2">
          {(['lista', 'mes', 'semana', 'dia'] as const).map((mode) => (
            <button
              key={mode}
              type="button"
              onClick={() => setViewMode(mode)}
              className={`px-4 py-2 rounded-xl text-sm font-medium border transition-colors ${
                viewMode === mode
                  ? 'bg-primary-600 border-primary-600 text-white'
                  : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
            >
              {mode === 'lista' ? 'Lista' : mode === 'mes' ? 'Mês' : mode === 'semana' ? 'Semana' : 'Dia'}
            </button>
          ))}
        </div>

        {viewMode !== 'lista' && (
          <div className="flex flex-col sm:flex-row sm:items-center gap-3">
            <select
              value={filterDoctorId ?? ''}
              onChange={(e) => setFilterDoctorId(e.target.value ? Number(e.target.value) : null)}
              className="px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all w-full sm:w-auto"
            >
              <option value="">Todos os médicos</option>
              {activeDoctors.map((doctor) => (
                <option key={doctor.id} value={doctor.id}>
                  {doctor.name}
                </option>
              ))}
            </select>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => shiftReferenceDate(-1)}
                aria-label="Período anterior"
                className="p-2.5 rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={() => setReferenceDate(new Date())}
                className="px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm font-medium text-slate-600 hover:bg-slate-50 transition-colors"
              >
                Hoje
              </button>
              <button
                type="button"
                onClick={() => shiftReferenceDate(1)}
                aria-label="Próximo período"
                className="p-2.5 rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>

            <p className="text-sm font-semibold text-slate-700">{formatPeriodLabel()}</p>
          </div>
        )}
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

      {!isLoading && !loadError && viewMode !== 'lista' && (
        <AppointmentCalendar
          view={viewMode}
          appointments={calendarAppointments}
          availability={filterAvailability}
          referenceDate={referenceDate}
          filterDoctorId={filterDoctorId}
          onAppointmentClick={openViewModal}
          onSlotClick={handleSlotClick}
          doctorsForDayColumns={viewMode === 'dia' && filterDoctorId == null ? activeDoctors : undefined}
        />
      )}

      {!isLoading && !loadError && viewMode === 'lista' && (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] overflow-hidden">
          {appointments.length === 0 ? (
            <div className="px-6 py-16 text-center">
              <div className="flex flex-col items-center gap-3">
                <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
                  <CalendarDays className="w-6 h-6 text-slate-400" />
                </div>
                <p className="text-slate-500 text-sm">Nenhum agendamento ainda.</p>
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
                          className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${appointmentStatusClasses(
                            appointment.status,
                          )}`}
                        >
                          {appointmentStatusLabel(appointment.status)}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">{appointment.confirmedAt ? 'Sim' : 'Não'}</td>
                      <td className="px-5 py-3.5 text-sm">
                        <div className="flex items-center gap-3 flex-wrap">
                          <button
                            type="button"
                            onClick={() => openViewModal(appointment)}
                            aria-label="Ver detalhes"
                            title="Ver detalhes"
                            className="text-slate-500 hover:text-slate-700"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                          {appointment.status === 'CONFIRMED' && (
                            <>
                              <button
                                type="button"
                                onClick={() => handleMarkAttended(appointment)}
                                disabled={attendingId === appointment.id}
                                className="text-blue-600 hover:text-blue-700 text-sm font-medium disabled:opacity-50"
                              >
                                {attendingId === appointment.id ? 'Marcando...' : 'Marcar como atendido'}
                              </button>
                              <button
                                type="button"
                                onClick={() => handleMarkNoShow(appointment)}
                                disabled={markingNoShowId === appointment.id}
                                className="text-red-600 hover:text-red-700 text-sm font-medium disabled:opacity-50"
                              >
                                {markingNoShowId === appointment.id ? 'Marcando...' : 'Marcar falta'}
                              </button>
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
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {detailModal && (
        <AppointmentDetailModal
          mode={detailModal.mode}
          appointmentId={detailModal.mode === 'view' ? detailModal.appointmentId : undefined}
          doctors={activeDoctors}
          onClose={() => setDetailModal(null)}
          onSaved={handleDetailSaved}
          initialDoctorId={detailModal.mode === 'create' ? detailModal.initialDoctorId : undefined}
          initialDate={detailModal.mode === 'create' ? detailModal.initialDate : undefined}
          initialStartTime={detailModal.mode === 'create' ? detailModal.initialStartTime : undefined}
        />
      )}

      {reschedulingId !== null && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-[2px] flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center gap-3">
              <div className="flex-1">
                <h2 className="text-lg font-semibold text-slate-900">
                  Reagendar agendamento{reschedulingAppointment ? ` — ${reschedulingAppointment.patientName}` : ''}
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
