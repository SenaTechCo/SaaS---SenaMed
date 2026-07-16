import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { CalendarOff, Clock, KeyRound, Pencil, Plus, Unlock, Users, X } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { Doctor, DoctorAccessResponse, DoctorPayload, GrantDoctorAccessPayload } from '../types/doctor';
import type { ClinicProfile } from '../types/clinic';

const emptyForm: DoctorPayload = { name: '', specialty: '', email: '', phone: '' };
const emptyAccessForm: GrantDoctorAccessPayload = { email: '', password: '' };

export function DoctorsPage() {
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [maxDoctors, setMaxDoctors] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSlow, setIsSlow] = useState(false);

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState<DoctorPayload>(emptyForm);
  const [createError, setCreateError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<DoctorPayload>(emptyForm);
  const [editError, setEditError] = useState<string | null>(null);
  const [isSavingEdit, setIsSavingEdit] = useState(false);

  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [rowError, setRowError] = useState<string | null>(null);

  const [grantingAccessId, setGrantingAccessId] = useState<string | null>(null);
  const [accessForm, setAccessForm] = useState<GrantDoctorAccessPayload>(emptyAccessForm);
  const [accessError, setAccessError] = useState<string | null>(null);
  const [isSavingAccess, setIsSavingAccess] = useState(false);
  const [revokingAccessId, setRevokingAccessId] = useState<string | null>(null);

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
        const [doctorsList, clinicProfile] = await Promise.all([
          apiFetch<Doctor[]>('/api/doctors'),
          apiFetch<ClinicProfile>('/api/clinics/me'),
        ]);
        if (cancelled) return;
        setDoctors(doctorsList);
        setMaxDoctors(clinicProfile.maxDoctors);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar os médicos.');
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

  const activeCount = doctors.filter((doctor) => doctor.active).length;
  const limitReached = maxDoctors !== null && activeCount >= maxDoctors;

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
      const created = await apiFetch<Doctor>('/api/doctors', {
        method: 'POST',
        body: createForm,
      });
      setDoctors((prev) => [...prev, created]);
      setShowCreateForm(false);
      setCreateForm(emptyForm);
    } catch (err) {
      if (err instanceof ApiError && (err.status === 422 || err.status === 409)) {
        setCreateError(err.message || 'Limite de médicos atingido para o plano da clínica.');
      } else if (err instanceof ApiError) {
        setCreateError(err.message);
      } else {
        setCreateError('Não foi possível cadastrar o médico. Tente novamente.');
      }
    } finally {
      setIsCreating(false);
    }
  }

  function startEdit(doctor: Doctor) {
    setEditingId(doctor.id);
    setEditForm({
      name: doctor.name,
      specialty: doctor.specialty,
      email: doctor.email,
      phone: doctor.phone,
    });
    setEditError(null);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditError(null);
  }

  async function handleSaveEdit(event: FormEvent<HTMLFormElement>, doctorId: string) {
    event.preventDefault();
    setEditError(null);
    setIsSavingEdit(true);
    try {
      const updated = await apiFetch<Doctor>(`/api/doctors/${doctorId}`, {
        method: 'PUT',
        body: editForm,
      });
      setDoctors((prev) => prev.map((doctor) => (doctor.id === doctorId ? updated : doctor)));
      setEditingId(null);
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : 'Não foi possível salvar as alterações.');
    } finally {
      setIsSavingEdit(false);
    }
  }

  function openGrantAccessForm(doctorId: string) {
    setAccessForm(emptyAccessForm);
    setAccessError(null);
    setGrantingAccessId(doctorId);
  }

  function cancelGrantAccess() {
    setGrantingAccessId(null);
    setAccessError(null);
  }

  async function handleGrantAccess(event: FormEvent<HTMLFormElement>, doctorId: string) {
    event.preventDefault();
    setAccessError(null);
    setIsSavingAccess(true);
    try {
      await apiFetch<DoctorAccessResponse>(`/api/doctors/${doctorId}/access`, {
        method: 'POST',
        body: accessForm,
      });
      setDoctors((prev) => prev.map((d) => (d.id === doctorId ? { ...d, hasLoginAccess: true } : d)));
      setGrantingAccessId(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setAccessError(err.message || 'Este médico já possui acesso ou o e-mail já está em uso.');
      } else {
        setAccessError(err instanceof ApiError ? err.message : 'Não foi possível conceder acesso.');
      }
    } finally {
      setIsSavingAccess(false);
    }
  }

  async function handleRevokeAccess(doctor: Doctor) {
    setRowError(null);
    const confirmed = window.confirm(`Revogar o acesso de login do médico "${doctor.name}"?`);
    if (!confirmed) return;

    setRevokingAccessId(doctor.id);
    try {
      await apiFetch<void>(`/api/doctors/${doctor.id}/access`, { method: 'DELETE' });
      setDoctors((prev) => prev.map((d) => (d.id === doctor.id ? { ...d, hasLoginAccess: false } : d)));
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : 'Não foi possível revogar o acesso.');
    } finally {
      setRevokingAccessId(null);
    }
  }

  async function handleDeactivate(doctor: Doctor) {
    setRowError(null);
    const confirmed = window.confirm(`Inativar o médico "${doctor.name}"? Esta ação não pode ser desfeita por aqui.`);
    if (!confirmed) return;

    setDeletingId(doctor.id);
    try {
      await apiFetch<void>(`/api/doctors/${doctor.id}`, { method: 'DELETE' });
      setDoctors((prev) => prev.map((d) => (d.id === doctor.id ? { ...d, active: false } : d)));
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : 'Não foi possível inativar o médico.');
    } finally {
      setDeletingId(null);
    }
  }

  const editingDoctor = editingId !== null ? doctors.find((d) => d.id === editingId) : undefined;
  const grantingAccessDoctor = grantingAccessId !== null ? doctors.find((d) => d.id === grantingAccessId) : undefined;

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Médicos</h1>
          <p className="text-sm text-slate-500 mt-1">Cadastre e gerencie os médicos da clínica.</p>
        </div>
        <div className="flex items-center gap-3">
          {maxDoctors !== null && (
            <span
              className={`inline-flex items-center rounded-full px-3 py-1.5 text-xs font-semibold ${
                limitReached ? 'bg-red-50 text-red-700' : 'bg-slate-100 text-slate-600'
              }`}
            >
              {activeCount}/{maxDoctors} médicos
            </span>
          )}
          <button
            type="button"
            onClick={openCreateForm}
            disabled={limitReached}
            className="flex items-center justify-center gap-2 bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm disabled:opacity-50 disabled:cursor-not-allowed w-full sm:w-auto"
          >
            <Plus className="w-4 h-4" /> Novo médico
          </button>
        </div>
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
          {doctors.length === 0 ? (
            <div className="px-6 py-16 text-center">
              <div className="flex flex-col items-center gap-3">
                <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
                  <Users className="w-6 h-6 text-slate-400" />
                </div>
                <p className="text-slate-500 text-sm">Nenhum médico cadastrado ainda.</p>
              </div>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px]">
                <thead>
                  <tr className="bg-slate-50/80 border-b border-slate-100">
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Nome</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Especialidade</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Contato</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Acesso</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {doctors.map((doctor) => (
                    <tr key={doctor.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/80 transition-colors">
                      <td className="px-5 py-3.5 text-sm text-slate-700 font-medium">{doctor.name}</td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">{doctor.specialty}</td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">
                        <div>{doctor.email}</div>
                        <div className="text-slate-500">{doctor.phone}</div>
                      </td>
                      <td className="px-5 py-3.5 text-sm">
                        <span
                          className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                            doctor.active ? 'bg-green-50 text-green-700' : 'bg-slate-100 text-slate-500'
                          }`}
                        >
                          {doctor.active ? 'Ativo' : 'Inativo'}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-sm">
                        <span
                          className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                            doctor.hasLoginAccess ? 'bg-green-50 text-green-700' : 'bg-slate-100 text-slate-500'
                          }`}
                        >
                          {doctor.hasLoginAccess ? 'Concedido' : 'Não concedido'}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-sm">
                        <div className="flex items-center gap-3 flex-wrap">
                          <Link
                            className="flex items-center gap-1 text-primary-600 hover:text-primary-700 text-sm font-medium"
                            to={`/dashboard/medicos/${doctor.id}/horarios`}
                          >
                            <Clock className="w-3.5 h-3.5" /> Horários
                          </Link>
                          <Link
                            className="flex items-center gap-1 text-primary-600 hover:text-primary-700 text-sm font-medium"
                            to={`/dashboard/medicos/${doctor.id}/folgas`}
                          >
                            <CalendarOff className="w-3.5 h-3.5" /> Folgas
                          </Link>
                          <button
                            type="button"
                            onClick={() => startEdit(doctor)}
                            className="p-1.5 rounded-lg hover:bg-primary-50 text-slate-400 hover:text-primary-600 transition-colors"
                            aria-label={`Editar ${doctor.name}`}
                          >
                            <Pencil className="w-4 h-4" />
                          </button>
                          {doctor.hasLoginAccess ? (
                            <button
                              type="button"
                              onClick={() => handleRevokeAccess(doctor)}
                              disabled={revokingAccessId === doctor.id}
                              className="px-4 py-2.5 bg-red-600 text-white rounded-xl text-sm font-semibold hover:bg-red-700 disabled:opacity-50 transition-colors"
                            >
                              {revokingAccessId === doctor.id ? 'Revogando...' : 'Revogar acesso'}
                            </button>
                          ) : (
                            <button
                              type="button"
                              onClick={() => openGrantAccessForm(doctor.id)}
                              className="flex items-center gap-1 text-primary-600 hover:text-primary-700 text-sm font-medium"
                            >
                              <KeyRound className="w-3.5 h-3.5" /> Conceder acesso
                            </button>
                          )}
                          {doctor.active && (
                            <button
                              type="button"
                              onClick={() => handleDeactivate(doctor)}
                              disabled={deletingId === doctor.id}
                              className="px-4 py-2.5 bg-red-600 text-white rounded-xl text-sm font-semibold hover:bg-red-700 disabled:opacity-50 transition-colors"
                            >
                              {deletingId === doctor.id ? 'Inativando...' : 'Inativar'}
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

      {showCreateForm && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-[2px] flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center gap-3">
              <div className="flex-1">
                <h2 className="text-lg font-semibold text-slate-900">Cadastrar médico</h2>
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
                  <label htmlFor="create-name" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Nome
                  </label>
                  <input
                    id="create-name"
                    type="text"
                    value={createForm.name}
                    onChange={(e) => setCreateForm((f) => ({ ...f, name: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="create-specialty" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Especialidade
                  </label>
                  <input
                    id="create-specialty"
                    type="text"
                    value={createForm.specialty}
                    onChange={(e) => setCreateForm((f) => ({ ...f, specialty: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="create-email" className="block text-sm font-medium text-slate-700 mb-1.5">
                    E-mail
                  </label>
                  <input
                    id="create-email"
                    type="email"
                    value={createForm.email}
                    onChange={(e) => setCreateForm((f) => ({ ...f, email: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="create-phone" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Telefone
                  </label>
                  <input
                    id="create-phone"
                    type="tel"
                    value={createForm.phone}
                    onChange={(e) => setCreateForm((f) => ({ ...f, phone: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
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
                  Editar médico{editingDoctor ? ` — ${editingDoctor.name}` : ''}
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
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="edit-name" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Nome
                  </label>
                  <input
                    id="edit-name"
                    type="text"
                    value={editForm.name}
                    onChange={(e) => setEditForm((f) => ({ ...f, name: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="edit-specialty" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Especialidade
                  </label>
                  <input
                    id="edit-specialty"
                    type="text"
                    value={editForm.specialty}
                    onChange={(e) => setEditForm((f) => ({ ...f, specialty: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="edit-email" className="block text-sm font-medium text-slate-700 mb-1.5">
                    E-mail
                  </label>
                  <input
                    id="edit-email"
                    type="email"
                    value={editForm.email}
                    onChange={(e) => setEditForm((f) => ({ ...f, email: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="edit-phone" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Telefone
                  </label>
                  <input
                    id="edit-phone"
                    type="tel"
                    value={editForm.phone}
                    onChange={(e) => setEditForm((f) => ({ ...f, phone: e.target.value }))}
                    required
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
              </div>
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

      {grantingAccessId !== null && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-[2px] flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center gap-3">
              <div className="flex-1">
                <h2 className="text-lg font-semibold text-slate-900">
                  Conceder acesso{grantingAccessDoctor ? ` — ${grantingAccessDoctor.name}` : ''}
                </h2>
              </div>
              <button
                type="button"
                onClick={cancelGrantAccess}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={(e) => handleGrantAccess(e, grantingAccessId)} noValidate className="p-6 space-y-4">
              {accessError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{accessError}</div>
              )}
              <div>
                <label htmlFor="access-email" className="block text-sm font-medium text-slate-700 mb-1.5">
                  E-mail de login
                </label>
                <input
                  id="access-email"
                  type="email"
                  value={accessForm.email}
                  onChange={(e) => setAccessForm((f) => ({ ...f, email: e.target.value }))}
                  required
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                />
              </div>
              <div>
                <label htmlFor="access-password" className="block text-sm font-medium text-slate-700 mb-1.5">
                  Senha inicial
                </label>
                <input
                  id="access-password"
                  type="password"
                  minLength={8}
                  value={accessForm.password}
                  onChange={(e) => setAccessForm((f) => ({ ...f, password: e.target.value }))}
                  required
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                />
              </div>
              <div className="flex gap-3 justify-end pt-2">
                <button
                  type="button"
                  onClick={cancelGrantAccess}
                  className="px-4 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-sm font-medium hover:bg-slate-50 transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSavingAccess}
                  className="px-5 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 shadow-sm disabled:opacity-50 transition-all flex items-center gap-2"
                >
                  <Unlock className="w-4 h-4" /> {isSavingAccess ? 'Salvando...' : 'Conceder acesso'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
}
