import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { CheckoutResponse, Plan, Preapproval, PreapprovalCheckoutResponse, Subscription } from '../types/billing';

const PERIOD_OPTIONS = [1, 3, 12];

const STATUS_MESSAGES: Record<string, { text: string; className: string }> = {
  success: {
    text: 'Pagamento em processamento. Assim que o Mercado Pago confirmar, seu plano será ativado automaticamente.',
    className: 'bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-6',
  },
  pending: {
    text: 'Pagamento pendente de confirmação pelo Mercado Pago.',
    className: 'bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-6',
  },
  failure: {
    text: 'O pagamento não foi aprovado. Você pode tentar novamente.',
    className: 'bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6',
  },
};

export function PlanoPage() {
  const [searchParams] = useSearchParams();
  const redirectStatus = searchParams.get('status');

  const [plans, setPlans] = useState<Plan[]>([]);
  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [preapproval, setPreapproval] = useState<Preapproval | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [selectedPeriod, setSelectedPeriod] = useState<Record<number, number>>({});
  const [checkingOutPlanId, setCheckingOutPlanId] = useState<number | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [preapprovalCheckingOutPlanId, setPreapprovalCheckingOutPlanId] = useState<number | null>(null);
  const [preapprovalError, setPreapprovalError] = useState<string | null>(null);
  const [isCancellingPreapproval, setIsCancellingPreapproval] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const [plansList, currentSubscription, currentPreapproval] = await Promise.all([
          apiFetch<Plan[]>('/api/plans'),
          apiFetch<Subscription | undefined>('/api/subscriptions/me'),
          apiFetch<Preapproval | undefined>('/api/subscriptions/preapproval/me'),
        ]);
        if (cancelled) return;
        setPlans(plansList);
        setSubscription(currentSubscription ?? null);
        setPreapproval(currentPreapproval ?? null);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar os planos.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleCheckout(planId: number) {
    setCheckoutError(null);
    setCheckingOutPlanId(planId);
    try {
      const periodMonths = selectedPeriod[planId] ?? PERIOD_OPTIONS[0];
      const response = await apiFetch<CheckoutResponse>('/api/subscriptions/checkout', {
        method: 'POST',
        body: { planId, periodMonths },
      });
      window.location.href = response.checkoutUrl;
    } catch (err) {
      setCheckoutError(err instanceof ApiError ? err.message : 'Não foi possível iniciar o checkout.');
      setCheckingOutPlanId(null);
    }
  }

  async function handlePreapprovalCheckout(planId: number) {
    setPreapprovalError(null);
    setPreapprovalCheckingOutPlanId(planId);
    try {
      const periodMonths = selectedPeriod[planId] ?? PERIOD_OPTIONS[0];
      const response = await apiFetch<PreapprovalCheckoutResponse>('/api/subscriptions/preapproval', {
        method: 'POST',
        body: { planId, periodMonths },
      });
      window.location.href = response.checkoutUrl;
    } catch (err) {
      setPreapprovalError(err instanceof ApiError ? err.message : 'Não foi possível iniciar a assinatura recorrente.');
      setPreapprovalCheckingOutPlanId(null);
    }
  }

  async function handleCancelPreapproval() {
    setPreapprovalError(null);
    setIsCancellingPreapproval(true);
    try {
      const updated = await apiFetch<Preapproval | undefined>('/api/subscriptions/preapproval/me/cancel', {
        method: 'POST',
      });
      setPreapproval(updated ?? null);
    } catch (err) {
      setPreapprovalError(err instanceof ApiError ? err.message : 'Não foi possível cancelar a assinatura recorrente.');
    } finally {
      setIsCancellingPreapproval(false);
    }
  }

  const statusMessage = redirectStatus ? STATUS_MESSAGES[redirectStatus] : null;

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Plano</h1>
          <p className="text-sm text-slate-500 mt-1">Escolha ou renove o plano da sua clínica.</p>
        </div>
      </div>

      {statusMessage && <div className={statusMessage.className}>{statusMessage.text}</div>}

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

      {!isLoading && !loadError && subscription && (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] p-6 mb-6">
          <h3 className="text-base font-semibold text-slate-900 mb-4">Assinatura atual</h3>
          <dl>
            <dt className="text-xs font-medium text-slate-500 uppercase tracking-wide">Plano</dt>
            <dd className="text-sm text-slate-900 mb-3">{subscription.planName}</dd>
            <dt className="text-xs font-medium text-slate-500 uppercase tracking-wide">Status</dt>
            <dd className="text-sm text-slate-900 mb-3">{subscription.status}</dd>
            <dt className="text-xs font-medium text-slate-500 uppercase tracking-wide">Período</dt>
            <dd className="text-sm text-slate-900 mb-3">
              {subscription.periodMonths} {subscription.periodMonths === 1 ? 'mês' : 'meses'}
            </dd>
            {subscription.currentPeriodEnd && (
              <>
                <dt className="text-xs font-medium text-slate-500 uppercase tracking-wide">Válido até</dt>
                <dd className="text-sm text-slate-900">{new Date(subscription.currentPeriodEnd).toLocaleDateString('pt-BR')}</dd>
              </>
            )}
          </dl>
        </div>
      )}

      {!isLoading && !loadError && preapproval && (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] p-6 mb-6">
          <h3 className="text-base font-semibold text-slate-900 mb-4">Assinatura recorrente atual</h3>
          <dl>
            <dt className="text-xs font-medium text-slate-500 uppercase tracking-wide">Plano</dt>
            <dd className="text-sm text-slate-900 mb-3">{preapproval.planName}</dd>
            <dt className="text-xs font-medium text-slate-500 uppercase tracking-wide">Status</dt>
            <dd className="text-sm text-slate-900 mb-3">{preapproval.status}</dd>
            <dt className="text-xs font-medium text-slate-500 uppercase tracking-wide">Período</dt>
            <dd className="text-sm text-slate-900 mb-3">
              {preapproval.periodMonths} {preapproval.periodMonths === 1 ? 'mês' : 'meses'}
            </dd>
            {preapproval.currentPeriodEnd && (
              <>
                <dt className="text-xs font-medium text-slate-500 uppercase tracking-wide">Próxima cobrança</dt>
                <dd className="text-sm text-slate-900 mb-3">{new Date(preapproval.currentPeriodEnd).toLocaleDateString('pt-BR')}</dd>
              </>
            )}
          </dl>
          {preapproval.status !== 'CANCELLED' && (
            <button
              type="button"
              className="px-4 py-2.5 bg-red-600 text-white rounded-xl text-sm font-semibold hover:bg-red-700 disabled:opacity-50 transition-colors"
              onClick={handleCancelPreapproval}
              disabled={isCancellingPreapproval}
            >
              {isCancellingPreapproval ? 'Cancelando...' : 'Cancelar assinatura recorrente'}
            </button>
          )}
        </div>
      )}

      {checkoutError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">
          {checkoutError}
        </div>
      )}
      {preapprovalError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">
          {preapprovalError}
        </div>
      )}

      {!isLoading && !loadError && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {plans.map((plan) => (
            <div className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-5 flex flex-col gap-3" key={plan.id}>
              <h3 className="text-lg font-semibold text-slate-900">{plan.name}</h3>
              <p className="text-sm text-slate-500">
                R$ {plan.priceAmount.toFixed(2)} /mês — até {plan.maxDoctors} médicos
              </p>
              <div>
                <label htmlFor={`period-${plan.id}`} className="block text-sm font-medium text-slate-700 mb-1.5">
                  Período
                </label>
                <select
                  id={`period-${plan.id}`}
                  value={selectedPeriod[plan.id] ?? PERIOD_OPTIONS[0]}
                  onChange={(e) =>
                    setSelectedPeriod((prev) => ({ ...prev, [plan.id]: Number(e.target.value) }))
                  }
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                >
                  {PERIOD_OPTIONS.map((months) => (
                    <option key={months} value={months}>
                      {months} {months === 1 ? 'mês' : 'meses'}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex flex-wrap gap-2 mt-1">
                <button
                  type="button"
                  className="bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm disabled:opacity-50"
                  onClick={() => handleCheckout(plan.id)}
                  disabled={checkingOutPlanId === plan.id}
                >
                  {checkingOutPlanId === plan.id ? 'Redirecionando...' : 'Assinar'}
                </button>
                <button
                  type="button"
                  className="px-4 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-sm font-medium hover:bg-slate-50 transition-colors disabled:opacity-50"
                  onClick={() => handlePreapprovalCheckout(plan.id)}
                  disabled={preapprovalCheckingOutPlanId === plan.id}
                  title="Cobrança automática recorrente, sem precisar renovar manualmente"
                >
                  {preapprovalCheckingOutPlanId === plan.id ? 'Redirecionando...' : 'Assinatura recorrente'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </DashboardLayout>
  );
}
