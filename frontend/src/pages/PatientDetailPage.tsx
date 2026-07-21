import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ArrowLeft, Banknote, CalendarDays } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { DashboardLayout } from '../components/DashboardLayout';
import { PatientFields } from '../components/patients/PatientFields';
import { apiFetch, ApiError } from '../lib/http';
import type { Patient, PatientPayload } from '../types/patient';
import type { Appointment, AppointmentStatus } from '../types/appointment';
import type { Receivable } from '../types/finance';

type Tab = 'dados' | 'financeiro' | 'linha-do-tempo';

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

const emptyForm: PatientPayload = {
  name: '',
  socialName: null,
  birthDate: null,
  sex: null,
  cpf: null,
  email: null,
  phone: null,
  zipCode: null,
  street: null,
  number: null,
  complement: null,
  neighborhood: null,
  city: null,
  state: null,
  referralSource: null,
  notes: null,
  lgpdConsent: false,
};

function toPayload(form: PatientPayload): PatientPayload {
  return {
    ...form,
    socialName: form.socialName?.trim() || null,
    birthDate: form.birthDate || null,
    sex: form.sex || null,
    cpf: form.cpf?.trim() || null,
    email: form.email?.trim() || null,
    phone: form.phone?.trim() || null,
    zipCode: form.zipCode?.trim() || null,
    street: form.street?.trim() || null,
    number: form.number?.trim() || null,
    complement: form.complement?.trim() || null,
    neighborhood: form.neighborhood?.trim() || null,
    city: form.city?.trim() || null,
    state: form.state?.trim() || null,
    referralSource: form.referralSource?.trim() || null,
    notes: form.notes?.trim() || null,
  };
}

function patientToForm(patient: Patient): PatientPayload {
  return {
    name: patient.name,
    socialName: patient.socialName,
    birthDate: patient.birthDate,
    sex: patient.sex,
    cpf: patient.cpf,
    email: patient.email,
    phone: patient.phone,
    zipCode: patient.zipCode,
    street: patient.street,
    number: patient.number,
    complement: patient.complement,
    neighborhood: patient.neighborhood,
    city: patient.city,
    state: patient.state,
    referralSource: patient.referralSource,
    notes: patient.notes,
    lgpdConsent: patient.lgpdConsent,
  };
}

function appointmentStatusClasses(status: AppointmentStatus): string {
  if (status === 'CONFIRMED') return 'bg-green-50 text-green-700';
  if (status === 'ATTENDED') return 'bg-blue-50 text-blue-700';
  if (status === 'NO_SHOW') return 'bg-red-50 text-red-700';
  return 'bg-slate-100 text-slate-500';
}

function appointmentStatusLabel(status: AppointmentStatus): string {
  if (status === 'CONFIRMED') return 'Confirmado';
  if (status === 'ATTENDED') return 'Atendido';
  if (status === 'NO_SHOW') return 'Faltou';
  return 'Cancelado';
}

export function PatientDetailPage() {
  const { id } = useParams<{ id: string }>();

  const [patient, setPatient] = useState<Patient | null>(null);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [receivables, setReceivables] = useState<Receivable[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [tab, setTab] = useState<Tab>('dados');

  const [form, setForm] = useState<PatientPayload>(emptyForm);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  const [rowError, setRowError] = useState<string | null>(null);
  const [payingId, setPayingId] = useState<number | null>(null);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const [patientData, appointmentsList, receivablesList] = await Promise.all([
          apiFetch<Patient>(`/api/patients/${id}`),
          apiFetch<Appointment[]>('/api/appointments'),
          apiFetch<Receivable[]>('/api/finance/receivables'),
        ]);
        if (cancelled) return;
        setPatient(patientData);
        setForm(patientToForm(patientData));
        const patientIdNum = Number(id);
        setAppointments(appointmentsList.filter((a) => a.patientId === patientIdNum));
        setReceivables(receivablesList.filter((r) => r.patientId === patientIdNum));
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar o paciente.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [id]);

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!id) return;
    setSaveError(null);
    setSaveSuccess(false);
    setIsSaving(true);
    try {
      const updated = await apiFetch<Patient>(`/api/patients/${id}`, {
        method: 'PUT',
        body: toPayload(form),
      });
      setPatient(updated);
      setForm(patientToForm(updated));
      setSaveSuccess(true);
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : 'Não foi possível salvar as alterações.');
    } finally {
      setIsSaving(false);
    }
  }

  async function handlePay(receivable: Receivable) {
    setRowError(null);
    setPayingId(receivable.id);
    try {
      const updated = await apiFetch<Receivable>(`/api/finance/receivables/${receivable.id}/pagar`, {
        method: 'PATCH',
      });
      setReceivables((prev) => prev.map((r) => (r.id === receivable.id ? updated : r)));
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : 'Não foi possível dar baixa nesta conta.');
    } finally {
      setPayingId(null);
    }
  }

  const sortedAppointments = [...appointments].sort((a, b) =>
    (b.date + b.startTime).localeCompare(a.date + a.startTime),
  );

  return (
    <DashboardLayout>
      <Link
        className="inline-flex items-center gap-1 text-primary-600 hover:text-primary-700 text-sm font-medium mb-4"
        to="/dashboard/pacientes"
      >
        <ArrowLeft className="w-4 h-4" /> Voltar para pacientes
      </Link>

      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            {patient ? patient.name : 'Paciente'}
          </h1>
          <p className="text-sm text-slate-500 mt-1">Dados cadastrais, financeiro e histórico de atendimentos.</p>
        </div>
      </div>

      {isLoading && (
        <div className="flex items-center justify-center py-24">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
        </div>
      )}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">{loadError}</div>
      )}

      {!isLoading && !loadError && patient && (
        <>
          <div className="flex gap-2 mb-6 border-b border-slate-200">
            <button
              type="button"
              onClick={() => setTab('dados')}
              className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                tab === 'dados'
                  ? 'border-primary-600 text-primary-600'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              Dados
            </button>
            <button
              type="button"
              onClick={() => setTab('financeiro')}
              className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                tab === 'financeiro'
                  ? 'border-primary-600 text-primary-600'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              Financeiro
            </button>
            <button
              type="button"
              onClick={() => setTab('linha-do-tempo')}
              className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                tab === 'linha-do-tempo'
                  ? 'border-primary-600 text-primary-600'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              Linha do Tempo
            </button>
          </div>

          {tab === 'dados' && (
            <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] p-6 max-w-3xl">
              {saveError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">{saveError}</div>
              )}
              {saveSuccess && (
                <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-4">
                  Alterações salvas com sucesso.
                </div>
              )}
              <form onSubmit={handleSave} noValidate className="space-y-4">
                <PatientFields form={form} setForm={setForm} idPrefix="detail" />
                <div className="flex justify-end pt-2">
                  <button
                    type="submit"
                    disabled={isSaving}
                    className="px-5 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 shadow-sm disabled:opacity-50 transition-all"
                  >
                    {isSaving ? 'Salvando...' : 'Salvar'}
                  </button>
                </div>
              </form>
            </div>
          )}

          {tab === 'financeiro' && (
            <div>
              {rowError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">{rowError}</div>
              )}
              <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] overflow-hidden">
                {receivables.length === 0 ? (
                  <div className="px-6 py-16 text-center">
                    <div className="flex flex-col items-center gap-3">
                      <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
                        <Banknote className="w-6 h-6 text-slate-400" />
                      </div>
                      <p className="text-slate-500 text-sm">Nenhuma conta a receber para este paciente.</p>
                    </div>
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[620px]">
                      <thead>
                        <tr className="bg-slate-50/80 border-b border-slate-100">
                          <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Descrição</th>
                          <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Valor</th>
                          <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                          <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Data</th>
                          <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Ações</th>
                        </tr>
                      </thead>
                      <tbody>
                        {receivables.map((receivable) => (
                          <tr key={receivable.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/80 transition-colors">
                            <td className="px-5 py-3.5 text-sm text-slate-700">{receivable.description}</td>
                            <td className="px-5 py-3.5 text-sm text-slate-700">{currencyFormatter.format(receivable.amount)}</td>
                            <td className="px-5 py-3.5 text-sm">
                              <span
                                className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                                  receivable.status === 'PAID' ? 'bg-green-50 text-green-700' : 'bg-amber-50 text-amber-700'
                                }`}
                              >
                                {receivable.status === 'PAID' ? 'Pago' : 'Pendente'}
                              </span>
                            </td>
                            <td className="px-5 py-3.5 text-sm text-slate-700">
                              {new Date(receivable.createdAt).toLocaleDateString('pt-BR')}
                            </td>
                            <td className="px-5 py-3.5 text-sm">
                              {receivable.status === 'PENDING' && (
                                <button
                                  type="button"
                                  onClick={() => handlePay(receivable)}
                                  disabled={payingId === receivable.id}
                                  className="px-4 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 disabled:opacity-50 transition-colors"
                                >
                                  {payingId === receivable.id ? 'Processando...' : 'Dar baixa'}
                                </button>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}

          {tab === 'linha-do-tempo' && (
            <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] overflow-hidden">
              {sortedAppointments.length === 0 ? (
                <div className="px-6 py-16 text-center">
                  <div className="flex flex-col items-center gap-3">
                    <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
                      <CalendarDays className="w-6 h-6 text-slate-400" />
                    </div>
                    <p className="text-slate-500 text-sm">Nenhum agendamento para este paciente.</p>
                  </div>
                </div>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {sortedAppointments.map((appointment) => (
                    <li key={appointment.id} className="px-5 py-3.5 flex items-center gap-4 flex-wrap">
                      <div className="min-w-[100px] text-sm text-slate-700">{appointment.date}</div>
                      <div className="min-w-[130px] text-sm text-slate-700">
                        {appointment.startTime} — {appointment.endTime}
                      </div>
                      <div className="flex-1 min-w-[160px] text-sm text-slate-700">{appointment.doctorName}</div>
                      <div className="flex-1 min-w-[140px] text-sm text-slate-700">
                        {appointment.services.length > 0
                          ? appointment.services.map((s) => s.serviceName).join(', ')
                          : '—'}
                      </div>
                      <span
                        className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium shrink-0 ${appointmentStatusClasses(
                          appointment.status,
                        )}`}
                      >
                        {appointmentStatusLabel(appointment.status)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </>
      )}
    </DashboardLayout>
  );
}
