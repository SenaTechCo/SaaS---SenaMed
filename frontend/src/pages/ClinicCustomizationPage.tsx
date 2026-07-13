import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { ClinicProfile, ClinicProfilePayload } from '../types/clinic';
import './auth-pages.css';
import './dashboard-shared.css';
import './ClinicCustomizationPage.css';

const DEFAULT_PRIMARY_COLOR = '#4f46e5';
const DEFAULT_SECONDARY_COLOR = '#111827';

interface FormState {
  name: string;
  description: string;
  phone: string;
  email: string;
  timezone: string;
  logoUrl: string;
  coverImageUrl: string;
  primaryColor: string;
  secondaryColor: string;
}

function toFormState(profile: ClinicProfile): FormState {
  return {
    name: profile.name ?? '',
    description: profile.description ?? '',
    phone: profile.phone ?? '',
    email: profile.email ?? '',
    timezone: profile.timezone ?? '',
    logoUrl: profile.logoUrl ?? '',
    coverImageUrl: profile.coverImageUrl ?? '',
    primaryColor: profile.primaryColor ?? DEFAULT_PRIMARY_COLOR,
    secondaryColor: profile.secondaryColor ?? DEFAULT_SECONDARY_COLOR,
  };
}

export function ClinicCustomizationPage() {
  const [form, setForm] = useState<FormState | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const profile = await apiFetch<ClinicProfile>('/api/clinics/me');
        if (cancelled) return;
        setForm(toFormState(profile));
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar os dados da clínica.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, []);

  function updateField<K extends keyof FormState>(field: K, value: FormState[K]) {
    setForm((prev) => (prev ? { ...prev, [field]: value } : prev));
    setSaveSuccess(false);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form) return;
    setSaveError(null);
    setSaveSuccess(false);

    const payload: ClinicProfilePayload = {
      name: form.name,
      description: form.description || null,
      phone: form.phone || null,
      email: form.email || null,
      timezone: form.timezone,
      logoUrl: form.logoUrl || null,
      coverImageUrl: form.coverImageUrl || null,
      primaryColor: form.primaryColor || null,
      secondaryColor: form.secondaryColor || null,
    };

    setIsSaving(true);
    try {
      const updated = await apiFetch<ClinicProfile>('/api/clinics/me', {
        method: 'PUT',
        body: payload,
      });
      setForm(toFormState(updated));
      setSaveSuccess(true);
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : 'Não foi possível salvar as alterações.');
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <DashboardLayout>
      <div className="page-header">
        <div>
          <h2>Personalização</h2>
          <p className="subtitle">Configure os dados públicos e a identidade visual da clínica.</p>
        </div>
      </div>

      {isLoading && <p className="loading-state">Carregando...</p>}
      {loadError && <div className="form-error">{loadError}</div>}

      {!isLoading && !loadError && form && (
        <div className="customization-layout">
          <div className="card">
            <h3>Dados da clínica</h3>
            {saveError && <div className="form-error">{saveError}</div>}
            {saveSuccess && <div className="form-success">Alterações salvas com sucesso.</div>}

            <form onSubmit={handleSubmit} noValidate>
              <div className="form-field">
                <label htmlFor="clinic-name">Nome</label>
                <input
                  id="clinic-name"
                  type="text"
                  value={form.name}
                  onChange={(e) => updateField('name', e.target.value)}
                  required
                />
              </div>

              <div className="form-field">
                <label htmlFor="clinic-description">Descrição</label>
                <textarea
                  id="clinic-description"
                  value={form.description}
                  onChange={(e) => updateField('description', e.target.value)}
                  rows={3}
                />
              </div>

              <div className="form-inline-row">
                <div className="form-field">
                  <label htmlFor="clinic-phone">Telefone</label>
                  <input
                    id="clinic-phone"
                    type="tel"
                    value={form.phone}
                    onChange={(e) => updateField('phone', e.target.value)}
                  />
                </div>
                <div className="form-field">
                  <label htmlFor="clinic-email">E-mail</label>
                  <input
                    id="clinic-email"
                    type="email"
                    value={form.email}
                    onChange={(e) => updateField('email', e.target.value)}
                  />
                </div>
              </div>

              <div className="form-field">
                <label htmlFor="clinic-timezone">Fuso horário</label>
                <input
                  id="clinic-timezone"
                  type="text"
                  value={form.timezone}
                  onChange={(e) => updateField('timezone', e.target.value)}
                  placeholder="America/Sao_Paulo"
                  required
                />
              </div>

              <h3>Identidade visual</h3>

              <div className="form-field">
                <label htmlFor="clinic-logo">URL do logo</label>
                <input
                  id="clinic-logo"
                  type="text"
                  value={form.logoUrl}
                  onChange={(e) => updateField('logoUrl', e.target.value)}
                  placeholder="https://..."
                />
              </div>

              <div className="form-field">
                <label htmlFor="clinic-cover">URL da foto de capa</label>
                <input
                  id="clinic-cover"
                  type="text"
                  value={form.coverImageUrl}
                  onChange={(e) => updateField('coverImageUrl', e.target.value)}
                  placeholder="https://..."
                />
              </div>

              <div className="form-inline-row">
                <div className="form-field">
                  <label htmlFor="clinic-primary-color">Cor primária</label>
                  <input
                    id="clinic-primary-color"
                    type="color"
                    value={form.primaryColor}
                    onChange={(e) => updateField('primaryColor', e.target.value)}
                  />
                </div>
                <div className="form-field">
                  <label htmlFor="clinic-secondary-color">Cor secundária</label>
                  <input
                    id="clinic-secondary-color"
                    type="color"
                    value={form.secondaryColor}
                    onChange={(e) => updateField('secondaryColor', e.target.value)}
                  />
                </div>
              </div>

              <button type="submit" className="btn-primary btn-small" style={{ width: 'auto' }} disabled={isSaving}>
                {isSaving ? 'Salvando...' : 'Salvar alterações'}
              </button>
            </form>
          </div>

          <div className="card">
            <h3>Pré-visualização</h3>
            <div
              className="color-preview-band"
              style={{ background: `linear-gradient(90deg, ${form.primaryColor} 50%, ${form.secondaryColor} 50%)` }}
            />
            <div className="preview-images">
              {form.logoUrl ? (
                <div className="preview-logo">
                  <p className="preview-label">Logo</p>
                  <img src={form.logoUrl} alt="Logo da clínica" />
                </div>
              ) : (
                <p className="empty-state">Sem logo cadastrado.</p>
              )}
              {form.coverImageUrl ? (
                <div className="preview-cover">
                  <p className="preview-label">Capa</p>
                  <img src={form.coverImageUrl} alt="Foto de capa da clínica" />
                </div>
              ) : (
                <p className="empty-state">Sem foto de capa cadastrada.</p>
              )}
            </div>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
}
