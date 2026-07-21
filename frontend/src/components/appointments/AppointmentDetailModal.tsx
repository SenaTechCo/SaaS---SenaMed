import { useEffect, useState } from 'react';
import { MessageCircle, Trash2, X } from 'lucide-react';
import { apiFetch, ApiError } from '../../lib/http';
import { PatientPicker } from './PatientPicker';
import type { Appointment, AppointmentPayload, ServiceLine } from '../../types/appointment';
import type { Doctor } from '../../types/doctor';
import type { Patient } from '../../types/patient';
import type { ServiceOffering } from '../../types/service';
import type { CommissionConfig, Receivable } from '../../types/finance';

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

function todayAsInputValue(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function computeEndTime(startTime: string): string {
  if (!startTime) return '';
  const [hours, minutes] = startTime.split(':').map(Number);
  if (Number.isNaN(hours)) return '';
  const total = hours * 60 + (minutes || 0) + 30;
  const hh = Math.floor(total / 60) % 24;
  const mm = total % 60;
  return `${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}`;
}

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

interface PendingServiceLine {
  serviceId: number;
  quantity: number;
  serviceName: string;
  unitPrice: number;
}

interface DisplayServiceLine {
  key: string;
  serviceName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  onRemove: (() => void) | null;
}

interface AppointmentDetailModalProps {
  mode: 'create' | 'view';
  appointmentId?: number;
  doctors: Doctor[];
  onClose: () => void;
  onSaved: (appointment: Appointment) => void;
  initialDoctorId?: number | null;
  initialDate?: string;
  initialStartTime?: string | null;
}

export function AppointmentDetailModal({
  mode,
  appointmentId,
  doctors,
  onClose,
  onSaved,
  initialDoctorId,
  initialDate,
  initialStartTime,
}: AppointmentDetailModalProps) {
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null);
  const [doctorId, setDoctorId] = useState(initialDoctorId != null ? String(initialDoctorId) : '');
  const [date, setDate] = useState(initialDate ?? '');
  const [startTime, setStartTime] = useState(initialStartTime ?? '');
  const [lgpdConsent, setLgpdConsent] = useState(false);

  const [initialDateTime, setInitialDateTime] = useState<{ date: string; startTime: string } | null>(null);

  const [appointment, setAppointment] = useState<Appointment | null>(null);
  const [isLoadingAppointment, setIsLoadingAppointment] = useState(mode === 'view');
  const [loadError, setLoadError] = useState<string | null>(null);

  const [services, setServices] = useState<ServiceOffering[]>([]);
  const [pendingServices, setPendingServices] = useState<PendingServiceLine[]>([]);
  const [addServiceId, setAddServiceId] = useState('');
  const [addServiceQty, setAddServiceQty] = useState(1);
  const [isMutatingService, setIsMutatingService] = useState(false);
  const [serviceError, setServiceError] = useState<string | null>(null);

  const [commissionPercentage, setCommissionPercentage] = useState<number | null>(null);

  const [receivable, setReceivable] = useState<Receivable | null>(null);
  const [isPaying, setIsPaying] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);

  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [pendingAction, setPendingAction] = useState<'atender' | 'faltou' | 'cancelar' | null>(null);
  const [transitionError, setTransitionError] = useState<string | null>(null);

  // Load the list of active services for the "Adicionar Serviço" dropdown.
  useEffect(() => {
    let cancelled = false;
    async function loadServices() {
      try {
        const list = await apiFetch<ServiceOffering[]>('/api/services');
        if (!cancelled) setServices(list.filter((service) => service.active));
      } catch {
        if (!cancelled) setServices([]);
      }
    }
    loadServices();
    return () => {
      cancelled = true;
    };
  }, []);

  // Load the appointment being viewed.
  useEffect(() => {
    if (mode !== 'view' || appointmentId == null) return;
    let cancelled = false;
    async function load() {
      setIsLoadingAppointment(true);
      setLoadError(null);
      try {
        const list = await apiFetch<Appointment[]>('/api/appointments');
        if (cancelled) return;
        const found = list.find((a) => a.id === appointmentId);
        if (!found) {
          setLoadError('Agendamento não encontrado.');
          return;
        }
        setAppointment(found);
        setDoctorId(String(found.doctorId));
        setDate(found.date);
        setStartTime(found.startTime);
        setInitialDateTime({ date: found.date, startTime: found.startTime });
        if (found.patientId != null) {
          try {
            const patient = await apiFetch<Patient>(`/api/patients/${found.patientId}`);
            if (!cancelled) setSelectedPatient(patient);
          } catch {
            // Patient details are a nice-to-have here; ignore failure.
          }
        }
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar o agendamento.');
      } finally {
        if (!cancelled) setIsLoadingAppointment(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [mode, appointmentId]);

  // Load the matching receivable, if any, once we know which appointment this is.
  useEffect(() => {
    if (mode !== 'view' || appointmentId == null) return;
    let cancelled = false;
    async function load() {
      try {
        const list = await apiFetch<Receivable[]>('/api/finance/receivables');
        if (cancelled) return;
        setReceivable(list.find((r) => r.appointmentId === appointmentId) ?? null);
      } catch {
        if (!cancelled) setReceivable(null);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [mode, appointmentId]);

  // Load the commission percentage once the doctor is known.
  useEffect(() => {
    if (!doctorId) {
      setCommissionPercentage(null);
      return;
    }
    let cancelled = false;
    async function load() {
      try {
        const config = await apiFetch<CommissionConfig>(`/api/finance/commissions/${Number(doctorId)}/config`);
        if (!cancelled) setCommissionPercentage(config.percentage);
      } catch {
        if (!cancelled) setCommissionPercentage(null);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [doctorId]);

  const canManageServices = mode === 'create' || (mode === 'view' && appointment?.status === 'CONFIRMED');

  const displayLines: DisplayServiceLine[] =
    mode === 'create'
      ? pendingServices.map((line, index) => ({
          key: `pending-${index}`,
          serviceName: line.serviceName,
          quantity: line.quantity,
          unitPrice: line.unitPrice,
          lineTotal: line.unitPrice * line.quantity,
          onRemove: () => handleRemovePendingService(index),
        }))
      : (appointment?.services ?? []).map((line: ServiceLine) => ({
          key: `line-${line.id}`,
          serviceName: line.serviceName,
          quantity: line.quantity,
          unitPrice: line.unitPrice,
          lineTotal: line.lineTotal,
          onRemove: canManageServices ? () => handleRemoveServiceLine(line.id) : null,
        }));

  const totalAmount = displayLines.reduce((sum, line) => sum + line.lineTotal, 0);

  function handleAddPendingService() {
    const service = services.find((s) => String(s.id) === addServiceId);
    if (!service) return;
    setPendingServices((prev) => [
      ...prev,
      { serviceId: Number(service.id), quantity: addServiceQty, serviceName: service.name, unitPrice: service.price },
    ]);
    setAddServiceId('');
    setAddServiceQty(1);
  }

  function handleRemovePendingService(index: number) {
    setPendingServices((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleAddServiceLine() {
    if (!appointment || !addServiceId) return;
    setServiceError(null);
    setIsMutatingService(true);
    try {
      const updated = await apiFetch<Appointment>(`/api/appointments/${appointment.id}/servicos`, {
        method: 'POST',
        body: { serviceId: Number(addServiceId), quantity: addServiceQty },
      });
      setAppointment(updated);
      onSaved(updated);
      setAddServiceId('');
      setAddServiceQty(1);
    } catch (err) {
      setServiceError(err instanceof ApiError ? err.message : 'Não foi possível adicionar o serviço.');
    } finally {
      setIsMutatingService(false);
    }
  }

  async function handleRemoveServiceLine(lineItemId: number) {
    if (!appointment) return;
    setServiceError(null);
    setIsMutatingService(true);
    try {
      const updated = await apiFetch<Appointment>(`/api/appointments/${appointment.id}/servicos/${lineItemId}`, {
        method: 'DELETE',
      });
      setAppointment(updated);
      onSaved(updated);
    } catch (err) {
      setServiceError(err instanceof ApiError ? err.message : 'Não foi possível remover o serviço.');
    } finally {
      setIsMutatingService(false);
    }
  }

  function handleAddServiceClick() {
    if (mode === 'create') {
      handleAddPendingService();
    } else {
      handleAddServiceLine();
    }
  }

  async function handleMarkAttended() {
    if (!appointment) return;
    setTransitionError(null);
    setPendingAction('atender');
    try {
      const updated = await apiFetch<Appointment>(`/api/appointments/${appointment.id}/atender`, { method: 'PATCH' });
      setAppointment(updated);
      onSaved(updated);
    } catch (err) {
      setTransitionError(err instanceof ApiError ? err.message : 'Não foi possível marcar o agendamento como atendido.');
    } finally {
      setPendingAction(null);
    }
  }

  async function handleMarkNoShow() {
    if (!appointment) return;
    setTransitionError(null);
    setPendingAction('faltou');
    try {
      const updated = await apiFetch<Appointment>(`/api/appointments/${appointment.id}/faltou`, { method: 'PATCH' });
      setAppointment(updated);
      onSaved(updated);
    } catch (err) {
      setTransitionError(err instanceof ApiError ? err.message : 'Não foi possível marcar falta neste agendamento.');
    } finally {
      setPendingAction(null);
    }
  }

  async function handleCancelAppointment() {
    if (!appointment) return;
    const confirmed = window.confirm(`Cancelar o agendamento de "${appointment.patientName}"?`);
    if (!confirmed) return;
    setTransitionError(null);
    setPendingAction('cancelar');
    try {
      const updated = await apiFetch<Appointment>(`/api/appointments/${appointment.id}/cancel`, { method: 'POST' });
      setAppointment(updated);
      onSaved(updated);
    } catch (err) {
      setTransitionError(err instanceof ApiError ? err.message : 'Não foi possível cancelar o agendamento.');
    } finally {
      setPendingAction(null);
    }
  }

  async function handlePayReceivable() {
    if (!receivable) return;
    setPayError(null);
    setIsPaying(true);
    try {
      const updated = await apiFetch<Receivable>(`/api/finance/receivables/${receivable.id}/pagar`, { method: 'PATCH' });
      setReceivable(updated);
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : 'Não foi possível dar baixa nesta conta.');
    } finally {
      setIsPaying(false);
    }
  }

  async function handleCreateSubmit() {
    setSaveError(null);
    setIsSaving(true);
    try {
      const payload: AppointmentPayload = {
        doctorId: Number(doctorId),
        date,
        startTime,
        patientId: selectedPatient ? Number(selectedPatient.id) : null,
        patientName: selectedPatient?.name ?? '',
        patientEmail: selectedPatient?.email ?? '',
        patientPhone: selectedPatient?.phone?.trim() ? selectedPatient.phone.trim() : null,
        lgpdConsent,
        services: pendingServices.map(({ serviceId, quantity }) => ({ serviceId, quantity })),
      };
      const created = await apiFetch<Appointment>('/api/appointments', { method: 'POST', body: payload });
      onSaved(created);
      onClose();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setSaveError(err.message || 'Este médico já tem um agendamento marcado para este horário.');
      } else if (err instanceof ApiError) {
        setSaveError(err.message);
      } else {
        setSaveError('Não foi possível cadastrar o agendamento. Tente novamente.');
      }
    } finally {
      setIsSaving(false);
    }
  }

  async function handleRescheduleSubmit() {
    if (!appointment) return;
    setSaveError(null);
    setIsSaving(true);
    try {
      const updated = await apiFetch<Appointment>(`/api/appointments/${appointment.id}`, {
        method: 'PATCH',
        body: { date, startTime },
      });
      setAppointment(updated);
      setInitialDateTime({ date: updated.date, startTime: updated.startTime });
      setDate(updated.date);
      setStartTime(updated.startTime);
      onSaved(updated);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setSaveError(err.message || 'Este médico já tem um agendamento marcado para este horário.');
      } else {
        setSaveError(err instanceof ApiError ? err.message : 'Não foi possível reagendar o agendamento.');
      }
    } finally {
      setIsSaving(false);
    }
  }

  function handleSave() {
    if (mode === 'create') {
      handleCreateSubmit();
    } else {
      handleRescheduleSubmit();
    }
  }

  const dateTimeChanged =
    mode === 'view' && initialDateTime != null && (date !== initialDateTime.date || startTime !== initialDateTime.startTime);

  const title =
    mode === 'create' ? 'Cadastrar agendamento' : `Detalhes do Agendamento${appointment ? ` — ${appointment.patientName}` : ''}`;

  const saveDisabled =
    mode === 'create' ? isSaving || !lgpdConsent || !selectedPatient || !doctorId || !date || !startTime : isSaving || !dateTimeChanged;

  return (
    <div className="fixed inset-0 bg-black/40 backdrop-blur-[2px] flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="px-6 py-5 border-b border-slate-100 flex items-center gap-3 sticky top-0 bg-white z-10">
          <div className="flex-1">
            <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {mode === 'view' && isLoadingAppointment && (
          <div className="flex flex-col items-center gap-3 py-16">
            <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
          </div>
        )}

        {mode === 'view' && loadError && (
          <div className="p-6">
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{loadError}</div>
          </div>
        )}

        {(mode === 'create' || (!isLoadingAppointment && !loadError && appointment)) && (
          <div className="p-6 space-y-6">
            {saveError && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{saveError}</div>
            )}

            <section>
              <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">Informações do Paciente</h3>
              <PatientPicker
                selectedPatient={selectedPatient}
                onSelectPatient={setSelectedPatient}
                onClearPatient={() => setSelectedPatient(null)}
                lgpdConsent={lgpdConsent}
                idPrefix="appointment-detail-patient"
              />
              {selectedPatient?.phone && (
                <a
                  href={`https://wa.me/${selectedPatient.phone.replace(/\D/g, '')}`}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-3 inline-flex items-center gap-1.5 px-3 py-1.5 border border-primary-200 text-primary-600 rounded-full text-xs font-medium hover:bg-primary-50 transition-colors"
                >
                  <MessageCircle className="w-3.5 h-3.5" /> Mensagem
                </a>
              )}
            </section>

            <section>
              <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">Detalhes do Evento</h3>

              {mode === 'view' && appointment && (
                <div className="mb-4 flex flex-wrap items-center gap-3">
                  <span
                    className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${appointmentStatusClasses(
                      appointment.status,
                    )}`}
                  >
                    {appointmentStatusLabel(appointment.status)}
                  </span>

                  {appointment.status === 'CONFIRMED' && (
                    <div className="flex items-center gap-3 flex-wrap">
                      <button
                        type="button"
                        onClick={handleMarkAttended}
                        disabled={pendingAction !== null}
                        className="text-blue-600 hover:text-blue-700 text-sm font-medium disabled:opacity-50"
                      >
                        {pendingAction === 'atender' ? 'Marcando...' : 'Marcar como atendido'}
                      </button>
                      <button
                        type="button"
                        onClick={handleMarkNoShow}
                        disabled={pendingAction !== null}
                        className="text-red-600 hover:text-red-700 text-sm font-medium disabled:opacity-50"
                      >
                        {pendingAction === 'faltou' ? 'Marcando...' : 'Marcar falta'}
                      </button>
                      <button
                        type="button"
                        onClick={handleCancelAppointment}
                        disabled={pendingAction !== null}
                        className="text-red-600 hover:text-red-700 text-sm font-medium disabled:opacity-50"
                      >
                        {pendingAction === 'cancelar' ? 'Cancelando...' : 'Cancelar'}
                      </button>
                    </div>
                  )}
                </div>
              )}

              {transitionError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">
                  {transitionError}
                </div>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label htmlFor="detail-doctor" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Médico
                  </label>
                  <select
                    id="detail-doctor"
                    value={doctorId}
                    onChange={(e) => setDoctorId(e.target.value)}
                    disabled={mode === 'view'}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all disabled:bg-slate-50 disabled:text-slate-500"
                  >
                    <option value="" disabled>
                      Selecione um médico
                    </option>
                    {doctors.map((doctor) => (
                      <option key={doctor.id} value={doctor.id}>
                        {doctor.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="detail-date" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Data
                  </label>
                  <input
                    id="detail-date"
                    type="date"
                    min={todayAsInputValue()}
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="detail-start-time" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Horário de início
                  </label>
                  <input
                    id="detail-start-time"
                    type="time"
                    value={startTime}
                    onChange={(e) => setStartTime(e.target.value)}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="detail-end-time" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Horário de fim
                  </label>
                  <input
                    id="detail-end-time"
                    type="time"
                    value={computeEndTime(startTime)}
                    readOnly
                    disabled
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm bg-slate-50 text-slate-500"
                  />
                </div>
              </div>

              {mode === 'create' && (
                <label className="mt-4 flex items-start gap-2 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    checked={lgpdConsent}
                    onChange={(e) => setLgpdConsent(e.target.checked)}
                    className="mt-0.5 w-4 h-4 rounded border-slate-300 text-primary-600 focus:ring-primary-200"
                  />
                  Paciente autorizou o uso dos seus dados para fins de agendamento, conforme a LGPD.
                </label>
              )}
            </section>

            <section>
              <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">Serviços Prestados</h3>

              {serviceError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-3">
                  {serviceError}
                </div>
              )}

              <div className="border border-slate-200 rounded-xl overflow-hidden">
                <table className="w-full">
                  <thead>
                    <tr className="bg-slate-50/80 border-b border-slate-100">
                      <th className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Nome</th>
                      <th className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Qtd</th>
                      <th className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Valor</th>
                      <th className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Comissão</th>
                      <th className="text-left px-4 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Ações</th>
                    </tr>
                  </thead>
                  <tbody>
                    {displayLines.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-4 text-sm text-slate-500 text-center">
                          Nenhum serviço adicionado.
                        </td>
                      </tr>
                    ) : (
                      displayLines.map((line) => (
                        <tr key={line.key} className="border-b border-slate-100 last:border-0">
                          <td className="px-4 py-2.5 text-sm text-slate-700">{line.serviceName}</td>
                          <td className="px-4 py-2.5 text-sm text-slate-700">{line.quantity}</td>
                          <td className="px-4 py-2.5 text-sm text-slate-700">{currencyFormatter.format(line.lineTotal)}</td>
                          <td className="px-4 py-2.5 text-sm text-slate-700">
                            {commissionPercentage != null
                              ? currencyFormatter.format((line.lineTotal * commissionPercentage) / 100)
                              : '—'}
                          </td>
                          <td className="px-4 py-2.5 text-sm">
                            {line.onRemove && (
                              <button
                                type="button"
                                onClick={line.onRemove}
                                disabled={isMutatingService}
                                aria-label="Remover serviço"
                                className="text-red-500 hover:text-red-700 disabled:opacity-50"
                              >
                                <Trash2 className="w-4 h-4" />
                              </button>
                            )}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              {canManageServices && (
                <div className="mt-3 flex flex-col sm:flex-row gap-2 sm:items-end">
                  <div className="flex-1">
                    <label htmlFor="add-service-select" className="block text-xs font-medium text-slate-600 mb-1">
                      Serviço
                    </label>
                    <select
                      id="add-service-select"
                      value={addServiceId}
                      onChange={(e) => setAddServiceId(e.target.value)}
                      className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                    >
                      <option value="">Selecione um serviço</option>
                      {services.map((service) => (
                        <option key={service.id} value={service.id}>
                          {service.name} — {currencyFormatter.format(service.price)}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="w-24">
                    <label htmlFor="add-service-qty" className="block text-xs font-medium text-slate-600 mb-1">
                      Qtd
                    </label>
                    <input
                      id="add-service-qty"
                      type="number"
                      min={1}
                      value={addServiceQty}
                      onChange={(e) => setAddServiceQty(Math.max(1, Number(e.target.value) || 1))}
                      className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                    />
                  </div>
                  <button
                    type="button"
                    onClick={handleAddServiceClick}
                    disabled={!addServiceId || isMutatingService}
                    className="px-4 py-2 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 disabled:opacity-50 transition-colors"
                  >
                    {isMutatingService ? 'Adicionando...' : '+ Adicionar Serviço'}
                  </button>
                </div>
              )}

              <p className="mt-3 text-sm font-semibold text-slate-800">
                Total: <span className="text-base">{currencyFormatter.format(totalAmount)}</span>
              </p>
            </section>

            {mode === 'view' && receivable && (
              <section>
                <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">Financeiro</h3>
                {payError && (
                  <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-3">{payError}</div>
                )}
                <div className="flex items-center justify-between gap-3 px-4 py-3 border border-slate-200 rounded-xl bg-slate-50">
                  <div>
                    <p className="text-sm font-semibold text-slate-800">{currencyFormatter.format(receivable.amount)}</p>
                    <span
                      className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium mt-1 ${
                        receivable.status === 'PAID' ? 'bg-green-50 text-green-700' : 'bg-amber-50 text-amber-700'
                      }`}
                    >
                      {receivable.status === 'PAID' ? 'Pago' : 'Pendente'}
                    </span>
                  </div>
                  {receivable.status === 'PENDING' && (
                    <button
                      type="button"
                      onClick={handlePayReceivable}
                      disabled={isPaying}
                      className="px-4 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 disabled:opacity-50 transition-colors"
                    >
                      {isPaying ? 'Processando...' : 'Dar baixa'}
                    </button>
                  )}
                </div>
              </section>
            )}
          </div>
        )}

        <div className="px-6 py-4 border-t border-slate-100 flex gap-3 justify-end sticky bottom-0 bg-white">
          {mode === 'view' && appointment?.status === 'CONFIRMED' && (
            <button
              type="button"
              onClick={handleCancelAppointment}
              disabled={pendingAction !== null}
              className="px-4 py-2.5 border border-red-200 text-red-600 rounded-xl text-sm font-medium hover:bg-red-50 disabled:opacity-50 transition-colors"
            >
              {pendingAction === 'cancelar' ? 'Cancelando...' : 'Cancelar'}
            </button>
          )}
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-sm font-medium hover:bg-slate-50 transition-colors"
          >
            Fechar
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={saveDisabled}
            className="px-5 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 shadow-sm disabled:opacity-50 transition-all"
          >
            {isSaving ? 'Salvando...' : 'Salvar'}
          </button>
        </div>
      </div>
    </div>
  );
}
