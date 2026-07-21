import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Banknote, Percent, TrendingUp, Wallet, X } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { CommissionCalculation, CommissionConfig, Receivable } from '../types/finance';
import type { Doctor } from '../types/doctor';

type Tab = 'receber' | 'comissoes';
type ReceivableFilter = 'TODAS' | 'PENDING' | 'PAID';

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

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

function currentYear(): number {
  return new Date().getFullYear();
}

function currentMonth(): number {
  return new Date().getMonth() + 1;
}

export function FinanceiroPage() {
  const [tab, setTab] = useState<Tab>('receber');

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Financeiro</h1>
          <p className="text-sm text-slate-500 mt-1">Contas a receber e comissões dos médicos.</p>
        </div>
      </div>

      <div className="flex gap-2 mb-6 border-b border-slate-200">
        <button
          type="button"
          onClick={() => setTab('receber')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'receber'
              ? 'border-primary-600 text-primary-600'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          Contas a Receber
        </button>
        <button
          type="button"
          onClick={() => setTab('comissoes')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'comissoes'
              ? 'border-primary-600 text-primary-600'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          Comissões
        </button>
      </div>

      {tab === 'receber' && <ContasReceberTab />}
      {tab === 'comissoes' && <ComissoesTab />}
    </DashboardLayout>
  );
}

function ContasReceberTab() {
  const [filter, setFilter] = useState<ReceivableFilter>('TODAS');
  const [receivables, setReceivables] = useState<Receivable[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [rowError, setRowError] = useState<string | null>(null);
  const [payingId, setPayingId] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const query = filter !== 'TODAS' ? `?status=${filter}` : '';
        const list = await apiFetch<Receivable[]>(`/api/finance/receivables${query}`);
        if (cancelled) return;
        setReceivables(list);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar as contas a receber.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [filter]);

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

  return (
    <div>
      <div className="flex flex-wrap items-center gap-2 mb-6">
        {(['TODAS', 'PENDING', 'PAID'] as const).map((option) => (
          <button
            key={option}
            type="button"
            onClick={() => setFilter(option)}
            className={`px-4 py-2 rounded-xl text-sm font-medium border transition-colors ${
              filter === option
                ? 'bg-primary-600 border-primary-600 text-white'
                : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
            }`}
          >
            {option === 'TODAS' ? 'Todas' : option === 'PENDING' ? 'Pendentes' : 'Pagas'}
          </button>
        ))}
      </div>

      {isLoading && (
        <div className="flex flex-col items-center gap-3 py-24">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
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
          {receivables.length === 0 ? (
            <div className="px-6 py-16 text-center">
              <div className="flex flex-col items-center gap-3">
                <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
                  <Banknote className="w-6 h-6 text-slate-400" />
                </div>
                <p className="text-slate-500 text-sm">Nenhuma conta a receber ainda.</p>
              </div>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[780px]">
                <thead>
                  <tr className="bg-slate-50/80 border-b border-slate-100">
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Paciente</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Médico</th>
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
                      <td className="px-5 py-3.5 text-sm text-slate-700 font-medium">{receivable.patientName}</td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">{receivable.doctorName}</td>
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
      )}
    </div>
  );
}

function ComissoesTab() {
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [doctorsError, setDoctorsError] = useState<string | null>(null);
  const [selectedDoctorId, setSelectedDoctorId] = useState<string>('');
  const [year, setYear] = useState<number>(currentYear());
  const [month, setMonth] = useState<number>(currentMonth());

  const [calculation, setCalculation] = useState<CommissionCalculation | null>(null);
  const [isLoadingCalculation, setIsLoadingCalculation] = useState(false);
  const [calculationError, setCalculationError] = useState<string | null>(null);

  const [showConfigModal, setShowConfigModal] = useState(false);
  const [percentageForm, setPercentageForm] = useState('0');
  const [isSavingConfig, setIsSavingConfig] = useState(false);
  const [configError, setConfigError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function loadDoctors() {
      try {
        const list = await apiFetch<Doctor[]>('/api/doctors');
        if (cancelled) return;
        const active = list.filter((doctor) => doctor.active);
        setDoctors(active);
        if (active.length > 0) {
          setSelectedDoctorId((prev) => prev || active[0].id);
        }
      } catch (err) {
        if (cancelled) return;
        setDoctorsError(err instanceof ApiError ? err.message : 'Não foi possível carregar os médicos.');
      }
    }
    loadDoctors();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!selectedDoctorId) {
      setCalculation(null);
      return;
    }

    let cancelled = false;
    async function loadCalculation() {
      setIsLoadingCalculation(true);
      setCalculationError(null);
      try {
        const result = await apiFetch<CommissionCalculation>(
          `/api/finance/commissions/${selectedDoctorId}?year=${year}&month=${month}`,
        );
        if (cancelled) return;
        setCalculation(result);
      } catch (err) {
        if (cancelled) return;
        setCalculationError(err instanceof ApiError ? err.message : 'Não foi possível carregar a comissão.');
        setCalculation(null);
      } finally {
        if (!cancelled) setIsLoadingCalculation(false);
      }
    }

    loadCalculation();
    return () => {
      cancelled = true;
    };
  }, [selectedDoctorId, year, month]);

  async function openConfigModal() {
    if (!selectedDoctorId) return;
    setConfigError(null);
    try {
      const config = await apiFetch<CommissionConfig>(`/api/finance/commissions/${selectedDoctorId}/config`);
      setPercentageForm(String(config.percentage));
    } catch {
      setPercentageForm(calculation ? String(calculation.percentage) : '0');
    }
    setShowConfigModal(true);
  }

  async function handleSaveConfig(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedDoctorId) return;
    setConfigError(null);
    setIsSavingConfig(true);
    try {
      await apiFetch<CommissionConfig>(`/api/finance/commissions/${selectedDoctorId}`, {
        method: 'PUT',
        body: { percentage: Number(percentageForm) },
      });
      setShowConfigModal(false);
      const result = await apiFetch<CommissionCalculation>(
        `/api/finance/commissions/${selectedDoctorId}?year=${year}&month=${month}`,
      );
      setCalculation(result);
    } catch (err) {
      setConfigError(err instanceof ApiError ? err.message : 'Não foi possível salvar a configuração.');
    } finally {
      setIsSavingConfig(false);
    }
  }

  const selectedDoctor = doctors.find((doctor) => doctor.id === selectedDoctorId);

  return (
    <div>
      {doctorsError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">{doctorsError}</div>
      )}

      <div className="flex flex-col sm:flex-row sm:items-end gap-3 mb-6">
        <div className="flex-1 min-w-[180px]">
          <label htmlFor="commission-doctor" className="block text-sm font-medium text-slate-700 mb-1.5">
            Médico
          </label>
          <select
            id="commission-doctor"
            value={selectedDoctorId}
            onChange={(e) => setSelectedDoctorId(e.target.value)}
            className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
          >
            {doctors.length === 0 && <option value="">Nenhum médico ativo</option>}
            {doctors.map((doctor) => (
              <option key={doctor.id} value={doctor.id}>
                {doctor.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="commission-month" className="block text-sm font-medium text-slate-700 mb-1.5">
            Mês
          </label>
          <select
            id="commission-month"
            value={month}
            onChange={(e) => setMonth(Number(e.target.value))}
            className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
          >
            {MONTH_NAMES.map((name, index) => (
              <option key={name} value={index + 1}>
                {name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="commission-year" className="block text-sm font-medium text-slate-700 mb-1.5">
            Ano
          </label>
          <input
            id="commission-year"
            type="number"
            value={year}
            onChange={(e) => setYear(Number(e.target.value))}
            className="w-28 px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
          />
        </div>
        <button
          type="button"
          onClick={openConfigModal}
          disabled={!selectedDoctorId}
          className="flex items-center justify-center gap-2 bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm disabled:opacity-50"
        >
          <Percent className="w-4 h-4" /> Configurar %
        </button>
      </div>

      {calculationError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">{calculationError}</div>
      )}

      {isLoadingCalculation && (
        <div className="flex flex-col items-center gap-3 py-16">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
        </div>
      )}

      {!isLoadingCalculation && calculation && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
          <div className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 bg-purple-50 rounded-xl flex items-center justify-center flex-shrink-0">
                <Percent className="w-4.5 h-4.5 text-purple-600" />
              </div>
              <div>
                <p className="text-2xl font-bold text-slate-900">{calculation.percentage}%</p>
                <p className="text-xs text-slate-500 leading-tight">Percentual configurado</p>
              </div>
            </div>
          </div>
          <div className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 bg-blue-50 rounded-xl flex items-center justify-center flex-shrink-0">
                <TrendingUp className="w-4.5 h-4.5 text-primary-600" />
              </div>
              <div>
                <p className="text-2xl font-bold text-slate-900">{currencyFormatter.format(calculation.totalBilled)}</p>
                <p className="text-xs text-slate-500 leading-tight">Total faturado no mês</p>
              </div>
            </div>
          </div>
          <div className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 bg-green-50 rounded-xl flex items-center justify-center flex-shrink-0">
                <Wallet className="w-4.5 h-4.5 text-green-600" />
              </div>
              <div>
                <p className="text-2xl font-bold text-slate-900">{currencyFormatter.format(calculation.commissionAmount)}</p>
                <p className="text-xs text-slate-500 leading-tight">Valor da comissão</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {showConfigModal && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-[2px] flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center gap-3">
              <div className="flex-1">
                <h2 className="text-lg font-semibold text-slate-900">
                  Configurar comissão{selectedDoctor ? ` — ${selectedDoctor.name}` : ''}
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setShowConfigModal(false)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleSaveConfig} noValidate className="p-6 space-y-4">
              {configError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{configError}</div>
              )}
              <div>
                <label htmlFor="commission-percentage" className="block text-sm font-medium text-slate-700 mb-1.5">
                  Percentual (%)
                </label>
                <input
                  id="commission-percentage"
                  type="number"
                  step="0.01"
                  min="0"
                  max="100"
                  value={percentageForm}
                  onChange={(e) => setPercentageForm(e.target.value)}
                  required
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                />
              </div>
              <div className="flex gap-3 justify-end pt-2">
                <button
                  type="button"
                  onClick={() => setShowConfigModal(false)}
                  className="px-4 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-sm font-medium hover:bg-slate-50 transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSavingConfig}
                  className="px-5 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 shadow-sm disabled:opacity-50 transition-all"
                >
                  {isSavingConfig ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
