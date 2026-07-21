import { useEffect, useState } from 'react';
import { Search, X } from 'lucide-react';
import { apiFetch, ApiError } from '../../lib/http';
import type { Patient } from '../../types/patient';

interface QuickAddForm {
  name: string;
  email: string;
  phone: string;
}

const emptyQuickAddForm: QuickAddForm = {
  name: '',
  email: '',
  phone: '',
};

interface PatientPickerProps {
  selectedPatient: Patient | null;
  onSelectPatient: (patient: Patient) => void;
  onClearPatient: () => void;
  lgpdConsent: boolean;
  idPrefix?: string;
  label?: string;
}

export function PatientPicker({
  selectedPatient,
  onSelectPatient,
  onClearPatient,
  lgpdConsent,
  idPrefix = 'patient-picker',
  label = 'Paciente',
}: PatientPickerProps) {
  const [patientQuery, setPatientQuery] = useState('');
  const [patientResults, setPatientResults] = useState<Patient[]>([]);
  const [isSearchingPatient, setIsSearchingPatient] = useState(false);
  const [showQuickAddPatient, setShowQuickAddPatient] = useState(false);
  const [quickAddForm, setQuickAddForm] = useState<QuickAddForm>(emptyQuickAddForm);
  const [isCreatingPatient, setIsCreatingPatient] = useState(false);
  const [quickAddError, setQuickAddError] = useState<string | null>(null);

  useEffect(() => {
    if (patientQuery.trim().length < 2) {
      setPatientResults([]);
      setIsSearchingPatient(false);
      return;
    }

    let cancelled = false;
    const timer = setTimeout(async () => {
      setIsSearchingPatient(true);
      try {
        const results = await apiFetch<Patient[]>(`/api/patients?search=${encodeURIComponent(patientQuery.trim())}`);
        if (cancelled) return;
        setPatientResults(results);
      } catch {
        if (cancelled) return;
        setPatientResults([]);
      } finally {
        if (!cancelled) setIsSearchingPatient(false);
      }
    }, 300);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [patientQuery]);

  function handleSelectPatient(patient: Patient) {
    onSelectPatient(patient);
    setPatientQuery('');
    setPatientResults([]);
    setShowQuickAddPatient(false);
    setQuickAddForm(emptyQuickAddForm);
    setQuickAddError(null);
  }

  function handleClearPatient() {
    onClearPatient();
    setPatientQuery('');
    setPatientResults([]);
    setShowQuickAddPatient(false);
    setQuickAddForm(emptyQuickAddForm);
    setQuickAddError(null);
  }

  function openQuickAddPatient() {
    setShowQuickAddPatient(true);
    setQuickAddForm({ name: patientQuery, email: '', phone: '' });
    setQuickAddError(null);
  }

  async function handleQuickAddPatient() {
    setQuickAddError(null);
    setIsCreatingPatient(true);
    try {
      const created = await apiFetch<Patient>('/api/patients', {
        method: 'POST',
        body: {
          name: quickAddForm.name,
          socialName: null,
          birthDate: null,
          sex: null,
          cpf: null,
          email: quickAddForm.email || null,
          phone: quickAddForm.phone || null,
          zipCode: null,
          street: null,
          number: null,
          complement: null,
          neighborhood: null,
          city: null,
          state: null,
          referralSource: null,
          notes: null,
          lgpdConsent,
        },
      });
      handleSelectPatient(created);
    } catch (err) {
      setQuickAddError(err instanceof ApiError ? err.message : 'Não foi possível cadastrar o paciente. Tente novamente.');
    } finally {
      setIsCreatingPatient(false);
    }
  }

  return (
    <div>
      <label className="block text-sm font-medium text-slate-700 mb-1.5">{label}</label>

      {selectedPatient ? (
        <div className="flex items-center justify-between gap-3 px-4 py-2.5 border border-slate-200 rounded-xl bg-slate-50">
          <div>
            <p className="text-sm font-semibold text-slate-800">{selectedPatient.name}</p>
            <p className="text-xs text-slate-500">
              {selectedPatient.email ?? '—'} · {selectedPatient.phone ?? '—'}
            </p>
          </div>
          <button
            type="button"
            onClick={handleClearPatient}
            className="flex items-center gap-1 text-xs font-medium text-slate-500 hover:text-slate-700 shrink-0"
          >
            <X className="w-3.5 h-3.5" /> Trocar
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              id={`${idPrefix}-search`}
              type="text"
              value={patientQuery}
              onChange={(e) => {
                setPatientQuery(e.target.value);
                setShowQuickAddPatient(false);
              }}
              placeholder="Buscar paciente por nome..."
              className="w-full pl-9 pr-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
            />
          </div>

          {!showQuickAddPatient && patientQuery.trim().length >= 2 && (
            <div>
              {isSearchingPatient ? (
                <p className="text-xs text-slate-500">Buscando...</p>
              ) : patientResults.length > 0 ? (
                <ul className="border border-slate-200 rounded-xl divide-y divide-slate-100 overflow-hidden max-h-48 overflow-y-auto">
                  {patientResults.map((patient) => (
                    <li key={patient.id}>
                      <button
                        type="button"
                        onClick={() => handleSelectPatient(patient)}
                        className="w-full text-left px-4 py-2.5 hover:bg-slate-50 transition-colors"
                      >
                        <p className="text-sm font-semibold text-slate-800">{patient.name}</p>
                        <p className="text-xs text-slate-500">
                          {patient.email ?? '—'} · {patient.phone ?? '—'}
                        </p>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="text-sm text-slate-500 space-y-2">
                  <p>Nenhum paciente encontrado.</p>
                  <button
                    type="button"
                    onClick={openQuickAddPatient}
                    className="text-primary-600 hover:text-primary-700 text-sm font-medium"
                  >
                    + Cadastrar novo paciente
                  </button>
                </div>
              )}
            </div>
          )}

          {showQuickAddPatient && (
            <div className="border border-slate-200 rounded-xl p-4 space-y-3 bg-slate-50/60">
              {quickAddError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">
                  {quickAddError}
                </div>
              )}
              <div>
                <label htmlFor={`${idPrefix}-quick-add-name`} className="block text-sm font-medium text-slate-700 mb-1.5">
                  Nome
                </label>
                <input
                  id={`${idPrefix}-quick-add-name`}
                  type="text"
                  value={quickAddForm.name}
                  onChange={(e) => setQuickAddForm((f) => ({ ...f, name: e.target.value }))}
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                />
              </div>
              <div>
                <label htmlFor={`${idPrefix}-quick-add-email`} className="block text-sm font-medium text-slate-700 mb-1.5">
                  E-mail
                </label>
                <input
                  id={`${idPrefix}-quick-add-email`}
                  type="email"
                  value={quickAddForm.email}
                  onChange={(e) => setQuickAddForm((f) => ({ ...f, email: e.target.value }))}
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                />
              </div>
              <div>
                <label htmlFor={`${idPrefix}-quick-add-phone`} className="block text-sm font-medium text-slate-700 mb-1.5">
                  Telefone
                </label>
                <input
                  id={`${idPrefix}-quick-add-phone`}
                  type="tel"
                  value={quickAddForm.phone}
                  onChange={(e) => setQuickAddForm((f) => ({ ...f, phone: e.target.value }))}
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                />
              </div>
              <div className="flex gap-3 justify-end">
                <button
                  type="button"
                  onClick={() => setShowQuickAddPatient(false)}
                  className="px-4 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-sm font-medium hover:bg-slate-50 transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="button"
                  onClick={handleQuickAddPatient}
                  disabled={isCreatingPatient || !quickAddForm.name.trim()}
                  className="px-5 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 shadow-sm disabled:opacity-50 transition-all"
                >
                  {isCreatingPatient ? 'Salvando...' : 'Salvar paciente'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
