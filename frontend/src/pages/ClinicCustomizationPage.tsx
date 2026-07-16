import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Image as ImageIcon } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { ClinicProfile, ClinicProfilePayload } from '../types/clinic';

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
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Personalização</h1>
          <p className="text-sm text-slate-500 mt-1">Configure os dados públicos e a identidade visual da clínica.</p>
        </div>
      </div>

      {isLoading && (
        <div className="flex items-center justify-center py-24">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
        </div>
      )}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">
          {loadError}
        </div>
      )}

      {!isLoading && !loadError && form && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
          <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] p-6">
            <h3 className="text-base font-semibold text-slate-900 mb-4">Dados da clínica</h3>
            {saveError && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">
                {saveError}
              </div>
            )}
            {saveSuccess && (
              <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-4">
                Alterações salvas com sucesso.
              </div>
            )}

            <form onSubmit={handleSubmit} noValidate className="space-y-4">
              <div>
                <label htmlFor="clinic-name" className="block text-sm font-medium text-slate-700 mb-1.5">
                  Nome
                </label>
                <input
                  id="clinic-name"
                  type="text"
                  value={form.name}
                  onChange={(e) => updateField('name', e.target.value)}
                  required
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                />
              </div>

              <div>
                <label htmlFor="clinic-description" className="block text-sm font-medium text-slate-700 mb-1.5">
                  Descrição
                </label>
                <textarea
                  id="clinic-description"
                  value={form.description}
                  onChange={(e) => updateField('description', e.target.value)}
                  rows={3}
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all resize-vertical"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="clinic-phone" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Telefone
                  </label>
                  <input
                    id="clinic-phone"
                    type="tel"
                    value={form.phone}
                    onChange={(e) => updateField('phone', e.target.value)}
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="clinic-email" className="block text-sm font-medium text-slate-700 mb-1.5">
                    E-mail
                  </label>
                  <input
                    id="clinic-email"
                    type="email"
                    value={form.email}
                    onChange={(e) => updateField('email', e.target.value)}
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                  />
                </div>
              </div>

              <div>
                <label htmlFor="clinic-timezone" className="block text-sm font-medium text-slate-700 mb-1.5">
                  Fuso horário
                </label>
                <input
                  id="clinic-timezone"
                  type="text"
                  value={form.timezone}
                  onChange={(e) => updateField('timezone', e.target.value)}
                  placeholder="America/Sao_Paulo"
                  required
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                />
              </div>

              <h3 className="text-base font-semibold text-slate-900 pt-2">Identidade visual</h3>

              <div>
                <label htmlFor="clinic-logo" className="block text-sm font-medium text-slate-700 mb-1.5">
                  URL do logo
                </label>
                <input
                  id="clinic-logo"
                  type="text"
                  value={form.logoUrl}
                  onChange={(e) => updateField('logoUrl', e.target.value)}
                  placeholder="https://..."
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                />
              </div>

              <div>
                <label htmlFor="clinic-cover" className="block text-sm font-medium text-slate-700 mb-1.5">
                  URL da foto de capa
                </label>
                <input
                  id="clinic-cover"
                  type="text"
                  value={form.coverImageUrl}
                  onChange={(e) => updateField('coverImageUrl', e.target.value)}
                  placeholder="https://..."
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="clinic-primary-color" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Cor primária
                  </label>
                  <input
                    id="clinic-primary-color"
                    type="color"
                    value={form.primaryColor}
                    onChange={(e) => updateField('primaryColor', e.target.value)}
                    className="w-full h-10 px-1 py-1 border border-slate-200 rounded-xl cursor-pointer bg-white transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="clinic-secondary-color" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Cor secundária
                  </label>
                  <input
                    id="clinic-secondary-color"
                    type="color"
                    value={form.secondaryColor}
                    onChange={(e) => updateField('secondaryColor', e.target.value)}
                    className="w-full h-10 px-1 py-1 border border-slate-200 rounded-xl cursor-pointer bg-white transition-all"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={isSaving}
                className="bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm disabled:opacity-50"
              >
                {isSaving ? 'Salvando...' : 'Salvar alterações'}
              </button>
            </form>
          </div>

          <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] p-6">
            <h3 className="text-base font-semibold text-slate-900 mb-4">Pré-visualização</h3>
            <div
              className="h-12 rounded-xl border border-slate-200 mb-4"
              style={{ background: `linear-gradient(90deg, ${form.primaryColor} 50%, ${form.secondaryColor} 50%)` }}
            />
            <div className="flex flex-col gap-4">
              {form.logoUrl ? (
                <div>
                  <p className="text-xs font-semibold text-slate-600 mb-1.5">Logo</p>
                  <img
                    src={form.logoUrl}
                    alt="Logo da clínica"
                    className="max-w-[120px] max-h-[120px] object-contain border border-slate-200 rounded-xl p-2 bg-white"
                  />
                </div>
              ) : (
                <div className="px-6 py-8 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <div className="w-10 h-10 rounded-2xl bg-slate-100 flex items-center justify-center">
                      <ImageIcon className="w-5 h-5 text-slate-400" />
                    </div>
                    <p className="text-slate-500 text-sm">Sem logo cadastrado.</p>
                  </div>
                </div>
              )}
              {form.coverImageUrl ? (
                <div>
                  <p className="text-xs font-semibold text-slate-600 mb-1.5">Capa</p>
                  <img
                    src={form.coverImageUrl}
                    alt="Foto de capa da clínica"
                    className="w-full max-h-[200px] object-cover border border-slate-200 rounded-xl"
                  />
                </div>
              ) : (
                <div className="px-6 py-8 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <div className="w-10 h-10 rounded-2xl bg-slate-100 flex items-center justify-center">
                      <ImageIcon className="w-5 h-5 text-slate-400" />
                    </div>
                    <p className="text-slate-500 text-sm">Sem foto de capa cadastrada.</p>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
}
