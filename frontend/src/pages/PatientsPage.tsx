import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Pencil, Plus, RotateCcw, Search, UserRound, X } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { PatientFields } from '../components/patients/PatientFields';
import { apiFetch, ApiError } from '../lib/http';
import type { Patient, PatientPayload } from '../types/patient';
import type { Appointment } from '../types/appointment';
import type { Receivable } from '../types/finance';
import type { Doctor } from '../types/doctor';

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

type PaymentStatusFilter = 'TODOS' | 'PENDENTE' | 'EM_DIA';
type PaymentStatus = 'Pendente' | 'Em dia' | '—';

function computeAge(birthDate: string): number {
  const [year, month, day] = birthDate.split('-').map(Number);
  const today = new Date();
  let age = today.getFullYear() - year;
  const hadBirthdayThisYear =
    today.getMonth() + 1 > month || (today.getMonth() + 1 === month && today.getDate() >= day);
  if (!hadBirthdayThisYear) age -= 1;
  return age;
}

function computeAgeGroup(birthDate: string | null): string {
  if (!birthDate) return 'Não informado';
  const age = computeAge(birthDate);
  if (age < 12) return 'Infantil';
  if (age < 18) return 'Adolescente';
  if (age < 60) return 'Adulto';
  return 'Idoso';
}

interface DerivedPatientData {
  sessionCount: number;
  ageGroup: string;
  paymentStatus: PaymentStatus;
  treatedByDoctorIds: Set<number>;
}

const PAYMENT_BADGE_CLASSES: Record<PaymentStatus, string> = {
  Pendente: 'bg-amber-50 text-amber-700',
  'Em dia': 'bg-green-50 text-green-700',
  '—': 'bg-slate-100 text-slate-500',
};

export function PatientsPage() {
  const navigate = useNavigate();

  const [patients, setPatients] = useState<Patient[]>([]);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [receivables, setReceivables] = useState<Receivable[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSlow, setIsSlow] = useState(false);
  const [search, setSearch] = useState('');

  const [paymentStatusFilter, setPaymentStatusFilter] = useState<PaymentStatusFilter>('TODOS');
  const [doctorFilter, setDoctorFilter] = useState<string>('');
  const [showArchived, setShowArchived] = useState(false);

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState<PatientPayload>(emptyForm);
  const [createError, setCreateError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  const [rowError, setRowError] = useState<string | null>(null);
  const [deactivatingId, setDeactivatingId] = useState<string | null>(null);
  const [restoringId, setRestoringId] = useState<string | null>(null);

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
        const query = search.trim() ? `?search=${encodeURIComponent(search.trim())}` : '';
        const [patientsList, appointmentsList, receivablesList, doctorsList] = await Promise.all([
          apiFetch<Patient[]>(`/api/patients${query}`),
          apiFetch<Appointment[]>('/api/appointments'),
          apiFetch<Receivable[]>('/api/finance/receivables'),
          apiFetch<Doctor[]>('/api/doctors'),
        ]);
        if (cancelled) return;
        setPatients(patientsList);
        setAppointments(appointmentsList);
        setReceivables(receivablesList);
        setDoctors(doctorsList);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar os pacientes.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    const debounce = setTimeout(load, search ? 300 : 0);
    return () => {
      cancelled = true;
      clearTimeout(slowTimer);
      clearTimeout(debounce);
    };
  }, [search]);

  const derivedByPatientId = useMemo(() => {
    const map = new Map<string, DerivedPatientData>();
    for (const patient of patients) {
      const patientIdNum = Number(patient.id);
      const patientAppointments = appointments.filter((a) => a.patientId === patientIdNum);
      const patientReceivables = receivables.filter((r) => r.patientId === patientIdNum);

      let paymentStatus: PaymentStatus = '—';
      if (patientReceivables.some((r) => r.status === 'PENDING')) {
        paymentStatus = 'Pendente';
      } else if (patientReceivables.some((r) => r.status === 'PAID')) {
        paymentStatus = 'Em dia';
      }

      map.set(patient.id, {
        sessionCount: patientAppointments.length,
        ageGroup: computeAgeGroup(patient.birthDate),
        paymentStatus,
        treatedByDoctorIds: new Set(patientAppointments.map((a) => a.doctorId)),
      });
    }
    return map;
  }, [patients, appointments, receivables]);

  const filteredPatients = useMemo(() => {
    return patients.filter((patient) => {
      if (!showArchived && !patient.active) return false;

      const derived = derivedByPatientId.get(patient.id);
      if (!derived) return true;

      if (paymentStatusFilter === 'PENDENTE' && derived.paymentStatus !== 'Pendente') return false;
      if (paymentStatusFilter === 'EM_DIA' && derived.paymentStatus !== 'Em dia') return false;

      if (doctorFilter && !derived.treatedByDoctorIds.has(Number(doctorFilter))) return false;

      return true;
    });
  }, [patients, derivedByPatientId, paymentStatusFilter, doctorFilter, showArchived]);

  function openCreateForm() {
    setCreateForm(emptyForm);
    setCreateError(null);
    setShowCreateForm(true);
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateError(null);
    setIsCreating(true);
    try {
      const created = await apiFetch<Patient>('/api/patients', {
        method: 'POST',
        body: toPayload(createForm),
      });
      setPatients((prev) => [...prev, created].sort((a, b) => a.name.localeCompare(b.name)));
      setShowCreateForm(false);
      setCreateForm(emptyForm);
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : 'Não foi possível cadastrar o paciente. Tente novamente.');
    } finally {
      setIsCreating(false);
    }
  }

  async function handleDeactivate(patient: Patient) {
    setRowError(null);
    const confirmed = window.confirm(`Inativar o paciente "${patient.name}"?`);
    if (!confirmed) return;

    setDeactivatingId(patient.id);
    try {
      await apiFetch<void>(`/api/patients/${patient.id}`, { method: 'DELETE' });
      setPatients((prev) => prev.map((p) => (p.id === patient.id ? { ...p, active: false } : p)));
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : 'Não foi possível inativar o paciente.');
    } finally {
      setDeactivatingId(null);
    }
  }

  async function handleRestore(patient: Patient) {
    setRowError(null);
    setRestoringId(patient.id);
    try {
      const updated = await apiFetch<Patient>(`/api/patients/${patient.id}/restaurar`, { method: 'PATCH' });
      setPatients((prev) => prev.map((p) => (p.id === patient.id ? updated : p)));
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : 'Não foi possível restaurar o paciente.');
    } finally {
      setRestoringId(null);
    }
  }

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Pacientes</h1>
          <p className="text-sm text-slate-500 mt-1">Cadastre e gerencie os pacientes atendidos pela clínica.</p>
        </div>
        <button
          type="button"
          onClick={openCreateForm}
          className="flex items-center justify-center gap-2 bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm w-full sm:w-auto"
        >
          <Plus className="w-4 h-4" /> Novo paciente
        </button>
      </div>

      <div className="relative mb-4 max-w-sm">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar por nome..."
          className="w-full pl-9 pr-4 py-2.5 border border-slate-200 rounded-xl text-sm bg-white shadow-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 transition-all"
        />
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center gap-3 mb-6">
        <select
          value={paymentStatusFilter}
          onChange={(e) => setPaymentStatusFilter(e.target.value as PaymentStatusFilter)}
          className="px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
        >
          <option value="TODOS">Status do pagamento: Todos</option>
          <option value="PENDENTE">Pendente</option>
          <option value="EM_DIA">Em dia</option>
        </select>

        <select
          value={doctorFilter}
          onChange={(e) => setDoctorFilter(e.target.value)}
          className="px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
        >
          <option value="">Atendido por: Todos os médicos</option>
          {doctors.map((doctor) => (
            <option key={doctor.id} value={doctor.id}>
              {doctor.name}
            </option>
          ))}
        </select>

        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input
            type="checkbox"
            checked={showArchived}
            onChange={(e) => setShowArchived(e.target.checked)}
            className="w-4 h-4 rounded border-slate-300 text-primary-600 focus:ring-primary-200"
          />
          Mostrar arquivados
        </label>
      </div>

      {showCreateForm && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-[2px] flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center gap-3">
              <div className="flex-1">
                <h2 className="text-lg font-semibold text-slate-900">Cadastrar paciente</h2>
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
              <PatientFields form={createForm} setForm={setCreateForm} idPrefix="create" />
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
                  disabled={isCreating}
                  className="px-5 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 shadow-sm disabled:opacity-50 transition-all"
                >
                  {isCreating ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

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
          {filteredPatients.length === 0 ? (
            <div className="px-6 py-16 text-center">
              <div className="flex flex-col items-center gap-3">
                <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
                  <UserRound className="w-6 h-6 text-slate-400" />
                </div>
                <p className="text-slate-500 text-sm">
                  {search ? 'Nenhum paciente encontrado para essa busca.' : 'Nenhum paciente cadastrado ainda.'}
                </p>
              </div>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[700px]">
                <thead>
                  <tr className="bg-slate-50/80 border-b border-slate-100">
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Ações</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Nome</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Telefone</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Sessões</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Grupo</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Status do pagamento</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredPatients.map((patient) => {
                    const derived = derivedByPatientId.get(patient.id);
                    return (
                      <tr key={patient.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/80 transition-colors">
                        <td className="px-5 py-3.5 text-sm">
                          <div className="flex items-center gap-3 flex-wrap">
                            <button
                              type="button"
                              onClick={() => navigate(`/dashboard/pacientes/${patient.id}`)}
                              className="p-1.5 rounded-lg hover:bg-primary-50 text-slate-400 hover:text-primary-600 transition-colors"
                              aria-label={`Editar ${patient.name}`}
                            >
                              <Pencil className="w-4 h-4" />
                            </button>
                            {patient.active ? (
                              <button
                                type="button"
                                onClick={() => handleDeactivate(patient)}
                                disabled={deactivatingId === patient.id}
                                className="px-4 py-2.5 bg-red-600 text-white rounded-xl text-sm font-semibold hover:bg-red-700 disabled:opacity-50 transition-colors"
                              >
                                {deactivatingId === patient.id ? 'Inativando...' : 'Inativar'}
                              </button>
                            ) : (
                              <button
                                type="button"
                                onClick={() => handleRestore(patient)}
                                disabled={restoringId === patient.id}
                                className="flex items-center gap-1 text-primary-600 hover:text-primary-700 text-sm font-medium"
                              >
                                <RotateCcw className="w-3.5 h-3.5" /> {restoringId === patient.id ? 'Restaurando...' : 'Restaurar'}
                              </button>
                            )}
                          </div>
                        </td>
                        <td className="px-5 py-3.5 text-sm text-slate-700 font-medium">
                          {patient.name}
                          {patient.socialName && <div className="text-slate-500 font-normal">{patient.socialName}</div>}
                        </td>
                        <td className="px-5 py-3.5 text-sm text-slate-700">{patient.phone ?? '—'}</td>
                        <td className="px-5 py-3.5 text-sm text-slate-700">{derived?.sessionCount ?? 0}</td>
                        <td className="px-5 py-3.5 text-sm text-slate-700">{derived?.ageGroup ?? 'Não informado'}</td>
                        <td className="px-5 py-3.5 text-sm">
                          <span
                            className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                              PAYMENT_BADGE_CLASSES[derived?.paymentStatus ?? '—']
                            }`}
                          >
                            {derived?.paymentStatus ?? '—'}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </DashboardLayout>
  );
}
