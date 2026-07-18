import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Pencil, Plus, RotateCcw, Search, UserRound, X } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { Patient, PatientPayload } from '../types/patient';

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

export function PatientsPage() {
  const [patients, setPatients] = useState<Patient[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSlow, setIsSlow] = useState(false);
  const [search, setSearch] = useState('');

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState<PatientPayload>(emptyForm);
  const [createError, setCreateError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<PatientPayload>(emptyForm);
  const [editError, setEditError] = useState<string | null>(null);
  const [isSavingEdit, setIsSavingEdit] = useState(false);

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
        const list = await apiFetch<Patient[]>(`/api/patients${query}`);
        if (cancelled) return;
        setPatients(list);
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

  function startEdit(patient: Patient) {
    setEditingId(patient.id);
    setEditForm({
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
    });
    setEditError(null);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditError(null);
  }

  async function handleSaveEdit(event: FormEvent<HTMLFormElement>, patientId: string) {
    event.preventDefault();
    setEditError(null);
    setIsSavingEdit(true);
    try {
      const updated = await apiFetch<Patient>(`/api/patients/${patientId}`, {
        method: 'PUT',
        body: toPayload(editForm),
      });
      setPatients((prev) => prev.map((p) => (p.id === patientId ? updated : p)));
      setEditingId(null);
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : 'Não foi possível salvar as alterações.');
    } finally {
      setIsSavingEdit(false);
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

  const editingPatient = editingId !== null ? patients.find((p) => p.id === editingId) : undefined;

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

      <div className="relative mb-6 max-w-sm">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar por nome..."
          className="w-full pl-9 pr-4 py-2.5 border border-slate-200 rounded-xl text-sm bg-white shadow-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 transition-all"
        />
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

      {editingId !== null && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-[2px] flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center gap-3">
              <div className="flex-1">
                <h2 className="text-lg font-semibold text-slate-900">
                  Editar paciente{editingPatient ? ` — ${editingPatient.name}` : ''}
                </h2>
              </div>
              <button
                type="button"
                onClick={cancelEdit}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={(e) => handleSaveEdit(e, editingId)} noValidate className="p-6 space-y-4">
              {editError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{editError}</div>
              )}
              <PatientFields form={editForm} setForm={setEditForm} idPrefix="edit" />
              <div className="flex gap-3 justify-end pt-2">
                <button
                  type="button"
                  onClick={cancelEdit}
                  className="px-4 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-sm font-medium hover:bg-slate-50 transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSavingEdit}
                  className="px-5 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 shadow-sm disabled:opacity-50 transition-all"
                >
                  {isSavingEdit ? 'Salvando...' : 'Salvar'}
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
          {patients.length === 0 ? (
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
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Nome</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Contato</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Cidade/UF</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {patients.map((patient) => (
                    <tr key={patient.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/80 transition-colors">
                      <td className="px-5 py-3.5 text-sm text-slate-700 font-medium">
                        {patient.name}
                        {patient.socialName && <div className="text-slate-500 font-normal">{patient.socialName}</div>}
                      </td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">
                        <div>{patient.email ?? '—'}</div>
                        <div className="text-slate-500">{patient.phone ?? '—'}</div>
                      </td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">
                        {patient.city ? `${patient.city}${patient.state ? `/${patient.state}` : ''}` : '—'}
                      </td>
                      <td className="px-5 py-3.5 text-sm">
                        <span
                          className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                            patient.active ? 'bg-green-50 text-green-700' : 'bg-slate-100 text-slate-500'
                          }`}
                        >
                          {patient.active ? 'Ativo' : 'Inativo'}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-sm">
                        <div className="flex items-center gap-3 flex-wrap">
                          <button
                            type="button"
                            onClick={() => startEdit(patient)}
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
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </DashboardLayout>
  );
}

const fieldClassName =
  'w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all';
const labelClassName = 'block text-sm font-medium text-slate-700 mb-1.5';

function PatientFields({
  form,
  setForm,
  idPrefix,
}: {
  form: PatientPayload;
  setForm: (updater: (f: PatientPayload) => PatientPayload) => void;
  idPrefix: string;
}) {
  return (
    <>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label htmlFor={`${idPrefix}-name`} className={labelClassName}>Nome</label>
          <input
            id={`${idPrefix}-name`}
            type="text"
            value={form.name}
            onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            required
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-social-name`} className={labelClassName}>Nome social (opcional)</label>
          <input
            id={`${idPrefix}-social-name`}
            type="text"
            value={form.socialName ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, socialName: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-birth-date`} className={labelClassName}>Data de nascimento</label>
          <input
            id={`${idPrefix}-birth-date`}
            type="date"
            value={form.birthDate ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, birthDate: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-sex`} className={labelClassName}>Sexo</label>
          <select
            id={`${idPrefix}-sex`}
            value={form.sex ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, sex: e.target.value || null }))}
            className={fieldClassName}
          >
            <option value="">Não informado</option>
            <option value="F">Feminino</option>
            <option value="M">Masculino</option>
            <option value="O">Outro</option>
          </select>
        </div>
        <div>
          <label htmlFor={`${idPrefix}-cpf`} className={labelClassName}>CPF</label>
          <input
            id={`${idPrefix}-cpf`}
            type="text"
            value={form.cpf ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, cpf: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-email`} className={labelClassName}>E-mail</label>
          <input
            id={`${idPrefix}-email`}
            type="email"
            value={form.email ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-phone`} className={labelClassName}>Telefone</label>
          <input
            id={`${idPrefix}-phone`}
            type="tel"
            value={form.phone ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-referral`} className={labelClassName}>Como conheceu a clínica</label>
          <input
            id={`${idPrefix}-referral`}
            type="text"
            value={form.referralSource ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, referralSource: e.target.value }))}
            className={fieldClassName}
          />
        </div>
      </div>

      <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider pt-2">Endereço</p>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <label htmlFor={`${idPrefix}-zip`} className={labelClassName}>CEP</label>
          <input
            id={`${idPrefix}-zip`}
            type="text"
            value={form.zipCode ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, zipCode: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div className="sm:col-span-2">
          <label htmlFor={`${idPrefix}-street`} className={labelClassName}>Logradouro</label>
          <input
            id={`${idPrefix}-street`}
            type="text"
            value={form.street ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, street: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-number`} className={labelClassName}>Número</label>
          <input
            id={`${idPrefix}-number`}
            type="text"
            value={form.number ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, number: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-complement`} className={labelClassName}>Complemento</label>
          <input
            id={`${idPrefix}-complement`}
            type="text"
            value={form.complement ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, complement: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-neighborhood`} className={labelClassName}>Bairro</label>
          <input
            id={`${idPrefix}-neighborhood`}
            type="text"
            value={form.neighborhood ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, neighborhood: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-city`} className={labelClassName}>Cidade</label>
          <input
            id={`${idPrefix}-city`}
            type="text"
            value={form.city ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, city: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-state`} className={labelClassName}>UF</label>
          <input
            id={`${idPrefix}-state`}
            type="text"
            maxLength={2}
            value={form.state ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, state: e.target.value.toUpperCase() }))}
            className={fieldClassName}
          />
        </div>
      </div>

      <div>
        <label htmlFor={`${idPrefix}-notes`} className={labelClassName}>Observações</label>
        <textarea
          id={`${idPrefix}-notes`}
          value={form.notes ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
          rows={3}
          className={`${fieldClassName} resize-vertical`}
        />
      </div>

      <label className="flex items-start gap-2 text-sm text-slate-600">
        <input
          type="checkbox"
          checked={form.lgpdConsent}
          onChange={(e) => setForm((f) => ({ ...f, lgpdConsent: e.target.checked }))}
          className="mt-0.5 w-4 h-4 rounded border-slate-300 text-primary-600 focus:ring-primary-200"
        />
        Paciente autorizou o uso dos seus dados, conforme a LGPD.
      </label>
    </>
  );
}
