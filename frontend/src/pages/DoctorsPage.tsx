import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { Doctor, DoctorPayload } from '../types/doctor';
import type { ClinicProfile } from '../types/clinic';
import './auth-pages.css';
import './dashboard-shared.css';

const emptyForm: DoctorPayload = { name: '', specialty: '', email: '', phone: '' };

export function DoctorsPage() {
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [maxDoctors, setMaxDoctors] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

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

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
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

  return (
    <DashboardLayout>
      <div className="page-header">
        <div>
          <h2>Médicos</h2>
          <p className="subtitle">Cadastre e gerencie os médicos da clínica.</p>
        </div>
        <div className="inline-actions" style={{ alignItems: 'center' }}>
          {maxDoctors !== null && (
            <span className={`limit-indicator ${limitReached ? 'limit-reached' : ''}`}>
              {activeCount}/{maxDoctors} médicos
            </span>
          )}
          <button
            type="button"
            className="btn-primary btn-small"
            style={{ width: 'auto' }}
            onClick={openCreateForm}
            disabled={limitReached}
          >
            Novo médico
          </button>
        </div>
      </div>

      {showCreateForm && (
        <div className="card">
          <h3>Cadastrar médico</h3>
          {createError && <div className="form-error">{createError}</div>}
          <form onSubmit={handleCreate} noValidate>
            <div className="form-inline-row">
              <div className="form-field">
                <label htmlFor="create-name">Nome</label>
                <input
                  id="create-name"
                  type="text"
                  value={createForm.name}
                  onChange={(e) => setCreateForm((f) => ({ ...f, name: e.target.value }))}
                  required
                />
              </div>
              <div className="form-field">
                <label htmlFor="create-specialty">Especialidade</label>
                <input
                  id="create-specialty"
                  type="text"
                  value={createForm.specialty}
                  onChange={(e) => setCreateForm((f) => ({ ...f, specialty: e.target.value }))}
                  required
                />
              </div>
            </div>
            <div className="form-inline-row">
              <div className="form-field">
                <label htmlFor="create-email">E-mail</label>
                <input
                  id="create-email"
                  type="email"
                  value={createForm.email}
                  onChange={(e) => setCreateForm((f) => ({ ...f, email: e.target.value }))}
                  required
                />
              </div>
              <div className="form-field">
                <label htmlFor="create-phone">Telefone</label>
                <input
                  id="create-phone"
                  type="tel"
                  value={createForm.phone}
                  onChange={(e) => setCreateForm((f) => ({ ...f, phone: e.target.value }))}
                  required
                />
              </div>
            </div>
            <div className="inline-actions">
              <button type="submit" className="btn-primary btn-small" style={{ width: 'auto' }} disabled={isCreating}>
                {isCreating ? 'Salvando...' : 'Salvar'}
              </button>
              <button type="button" className="btn-secondary" onClick={() => setShowCreateForm(false)}>
                Cancelar
              </button>
            </div>
          </form>
        </div>
      )}

      {isLoading && <p className="loading-state">Carregando médicos...</p>}
      {loadError && <div className="form-error">{loadError}</div>}
      {rowError && <div className="form-error">{rowError}</div>}

      {!isLoading && !loadError && (
        doctors.length === 0 ? (
          <p className="empty-state">Nenhum médico cadastrado ainda.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Nome</th>
                <th>Especialidade</th>
                <th>Contato</th>
                <th>Status</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {doctors.map((doctor) =>
                editingId === doctor.id ? (
                  <tr key={doctor.id}>
                    <td colSpan={5}>
                      {editError && <div className="form-error">{editError}</div>}
                      <form onSubmit={(e) => handleSaveEdit(e, doctor.id)} noValidate>
                        <div className="form-inline-row">
                          <div className="form-field">
                            <label htmlFor={`edit-name-${doctor.id}`}>Nome</label>
                            <input
                              id={`edit-name-${doctor.id}`}
                              type="text"
                              value={editForm.name}
                              onChange={(e) => setEditForm((f) => ({ ...f, name: e.target.value }))}
                              required
                            />
                          </div>
                          <div className="form-field">
                            <label htmlFor={`edit-specialty-${doctor.id}`}>Especialidade</label>
                            <input
                              id={`edit-specialty-${doctor.id}`}
                              type="text"
                              value={editForm.specialty}
                              onChange={(e) => setEditForm((f) => ({ ...f, specialty: e.target.value }))}
                              required
                            />
                          </div>
                          <div className="form-field">
                            <label htmlFor={`edit-email-${doctor.id}`}>E-mail</label>
                            <input
                              id={`edit-email-${doctor.id}`}
                              type="email"
                              value={editForm.email}
                              onChange={(e) => setEditForm((f) => ({ ...f, email: e.target.value }))}
                              required
                            />
                          </div>
                          <div className="form-field">
                            <label htmlFor={`edit-phone-${doctor.id}`}>Telefone</label>
                            <input
                              id={`edit-phone-${doctor.id}`}
                              type="tel"
                              value={editForm.phone}
                              onChange={(e) => setEditForm((f) => ({ ...f, phone: e.target.value }))}
                              required
                            />
                          </div>
                        </div>
                        <div className="inline-actions">
                          <button
                            type="submit"
                            className="btn-primary btn-small"
                            style={{ width: 'auto' }}
                            disabled={isSavingEdit}
                          >
                            {isSavingEdit ? 'Salvando...' : 'Salvar'}
                          </button>
                          <button type="button" className="btn-secondary" onClick={cancelEdit}>
                            Cancelar
                          </button>
                        </div>
                      </form>
                    </td>
                  </tr>
                ) : (
                  <tr key={doctor.id}>
                    <td>{doctor.name}</td>
                    <td>{doctor.specialty}</td>
                    <td>
                      {doctor.email}
                      <br />
                      {doctor.phone}
                    </td>
                    <td>
                      <span className={`badge ${doctor.active ? 'badge-active' : 'badge-inactive'}`}>
                        {doctor.active ? 'Ativo' : 'Inativo'}
                      </span>
                    </td>
                    <td>
                      <div className="inline-actions">
                        <Link className="btn-link" to={`/dashboard/medicos/${doctor.id}/horarios`}>
                          Horários
                        </Link>
                        <Link className="btn-link" to={`/dashboard/medicos/${doctor.id}/folgas`}>
                          Folgas
                        </Link>
                        <button type="button" className="btn-link" onClick={() => startEdit(doctor)}>
                          Editar
                        </button>
                        {doctor.active && (
                          <button
                            type="button"
                            className="btn-danger"
                            onClick={() => handleDeactivate(doctor)}
                            disabled={deletingId === doctor.id}
                          >
                            {deletingId === doctor.id ? 'Inativando...' : 'Inativar'}
                          </button>
                        )}
                      </div>
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
