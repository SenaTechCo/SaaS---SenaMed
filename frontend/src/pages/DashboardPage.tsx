import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarCheck, CalendarDays, CreditCard, TrendingUp, Users, Wallet } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { useAuth } from '../context/AuthContext';
import { apiFetch, ApiError } from '../lib/http';
import type { DashboardSummary } from '../types/dashboard';

const CLINIC_STATUS_LABELS: Record<string, string> = {
  TRIAL: 'Em teste',
  ACTIVE: 'Ativo',
  PAST_DUE: 'Pagamento atrasado',
  BLOCKED: 'Bloqueada',
};

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export function DashboardPage() {
  const { user, clinic } = useAuth();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSlow, setIsSlow] = useState(false);

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
        const data = await apiFetch<DashboardSummary>('/api/dashboard/summary');
        if (cancelled) return;
        setSummary(data);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar o resumo da clínica.');
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

  const isDoctor = user?.role === 'DOCTOR';

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Início</h1>
          <p className="text-sm text-slate-500 mt-1">
            {isDoctor ? 'Resumo da sua agenda.' : 'Resumo da clínica.'}
          </p>
        </div>
      </div>

      {isLoading && (
        <div className="flex flex-col items-center gap-3 py-16">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
          {isSlow && (
            <p className="text-sm text-slate-500">
              Isso está demorando mais que o esperado — o servidor pode estar iniciando, aguarde alguns segundos.
            </p>
          )}
        </div>
      )}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">
          {loadError}
        </div>
      )}

      {!isLoading && !loadError && summary && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
            <div className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 bg-blue-50 rounded-xl flex items-center justify-center flex-shrink-0">
                  <CalendarCheck className="w-4.5 h-4.5 text-primary-600" />
                </div>
                <div>
                  <p className="text-2xl font-bold text-slate-900">{summary.todayCount}</p>
                  <p className="text-xs text-slate-500 leading-tight">Agendamentos hoje</p>
                </div>
              </div>
            </div>

            {summary.activeDoctorCount !== null && (
              <div className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 bg-accent-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <Users className="w-4.5 h-4.5 text-accent-600" />
                  </div>
                  <div>
                    <p className="text-2xl font-bold text-slate-900">{summary.activeDoctorCount}</p>
                    <p className="text-xs text-slate-500 leading-tight">Médicos ativos</p>
                  </div>
                </div>
              </div>
            )}

            {summary.pendingReceivablesTotal !== null && (
              <Link
                to="/dashboard/financeiro"
                className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4 hover:shadow-md hover:border-primary-200 transition-all"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 bg-amber-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <Wallet className="w-4.5 h-4.5 text-amber-600" />
                  </div>
                  <div>
                    <p className="text-2xl font-bold text-slate-900">
                      {currencyFormatter.format(summary.pendingReceivablesTotal)}
                    </p>
                    <p className="text-xs text-slate-500 leading-tight">A receber</p>
                  </div>
                </div>
              </Link>
            )}

            {summary.paidThisMonthTotal !== null && (
              <Link
                to="/dashboard/financeiro"
                className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4 hover:shadow-md hover:border-primary-200 transition-all"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 bg-green-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <TrendingUp className="w-4.5 h-4.5 text-green-600" />
                  </div>
                  <div>
                    <p className="text-2xl font-bold text-slate-900">
                      {currencyFormatter.format(summary.paidThisMonthTotal)}
                    </p>
                    <p className="text-xs text-slate-500 leading-tight">Recebido este mês</p>
                  </div>
                </div>
              </Link>
            )}

            {clinic && (
              <Link
                to="/dashboard/plano"
                className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4 hover:shadow-md hover:border-primary-200 transition-all"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 bg-purple-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <CreditCard className="w-4.5 h-4.5 text-purple-600" />
                  </div>
                  <div>
                    <p className="text-2xl font-bold text-slate-900">
                      {CLINIC_STATUS_LABELS[clinic.status] ?? clinic.status}
                    </p>
                    <p className="text-xs text-slate-500 leading-tight">Status do plano — ver detalhes</p>
                  </div>
                </div>
              </Link>
            )}
          </div>

          <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] overflow-hidden">
            <div className="px-5 py-4 border-b border-slate-100 flex items-center gap-2">
              <CalendarDays className="w-4 h-4 text-slate-400" />
              <h2 className="text-base font-semibold text-slate-900">Próximos agendamentos</h2>
            </div>
            {summary.upcoming.length === 0 ? (
              <div className="px-6 py-16 text-center">
                <div className="flex flex-col items-center gap-3">
                  <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
                    <CalendarDays className="w-6 h-6 text-slate-400" />
                  </div>
                  <p className="text-slate-500 text-sm">Nenhum agendamento futuro.</p>
                </div>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[500px]">
                  <thead>
                    <tr className="bg-slate-50/80 border-b border-slate-100">
                      <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Paciente</th>
                      {!isDoctor && (
                        <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Médico</th>
                      )}
                      <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Data</th>
                      <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">Horário</th>
                    </tr>
                  </thead>
                  <tbody>
                    {summary.upcoming.map((appointment) => (
                      <tr key={appointment.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/80 transition-colors">
                        <td className="px-5 py-3.5 text-sm text-slate-700">{appointment.patientName}</td>
                        {!isDoctor && <td className="px-5 py-3.5 text-sm text-slate-700">{appointment.doctorName}</td>}
                        <td className="px-5 py-3.5 text-sm text-slate-700">{appointment.date}</td>
                        <td className="px-5 py-3.5 text-sm text-slate-700">{appointment.startTime} — {appointment.endTime}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </DashboardLayout>
  );
}
