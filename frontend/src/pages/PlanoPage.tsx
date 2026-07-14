import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { CheckoutResponse, Plan, Preapproval, PreapprovalCheckoutResponse, Subscription } from '../types/billing';
import './dashboard-shared.css';

const PERIOD_OPTIONS = [1, 3, 12];

const STATUS_MESSAGES: Record<string, { text: string; className: string }> = {
  success: {
    text: 'Pagamento em processamento. Assim que o Mercado Pago confirmar, seu plano será ativado automaticamente.',
    className: 'form-success',
  },
  pending: {
    text: 'Pagamento pendente de confirmação pelo Mercado Pago.',
    className: 'form-success',
  },
  failure: {
    text: 'O pagamento não foi aprovado. Você pode tentar novamente.',
    className: 'form-error',
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
      <div className="page-header">
        <div>
          <h2>Plano</h2>
          <p className="subtitle">Escolha ou renove o plano da sua clínica.</p>
        </div>
      </div>

      {statusMessage && <div className={statusMessage.className}>{statusMessage.text}</div>}

      {isLoading && <p className="loading-state">Carregando planos...</p>}
      {loadError && <div className="form-error">{loadError}</div>}

      {!isLoading && !loadError && subscription && (
        <div className="card">
          <h3>Assinatura atual</h3>
          <dl className="public-summary">
            <dt>Plano</dt>
            <dd>{subscription.planName}</dd>
            <dt>Status</dt>
            <dd>{subscription.status}</dd>
            <dt>Período</dt>
            <dd>{subscription.periodMonths} {subscription.periodMonths === 1 ? 'mês' : 'meses'}</dd>
            {subscription.currentPeriodEnd && (
              <>
                <dt>Válido até</dt>
                <dd>{new Date(subscription.currentPeriodEnd).toLocaleDateString('pt-BR')}</dd>
              </>
            )}
          </dl>
        </div>
      )}

      {!isLoading && !loadError && preapproval && (
        <div className="card">
          <h3>Assinatura recorrente atual</h3>
          <dl className="public-summary">
            <dt>Plano</dt>
            <dd>{preapproval.planName}</dd>
            <dt>Status</dt>
            <dd>{preapproval.status}</dd>
            <dt>Período</dt>
            <dd>{preapproval.periodMonths} {preapproval.periodMonths === 1 ? 'mês' : 'meses'}</dd>
            {preapproval.currentPeriodEnd && (
              <>
                <dt>Próxima cobrança</dt>
                <dd>{new Date(preapproval.currentPeriodEnd).toLocaleDateString('pt-BR')}</dd>
              </>
            )}
          </dl>
          {preapproval.status !== 'CANCELLED' && (
            <button
              type="button"
              className="btn-secondary btn-small"
              style={{ width: 'auto' }}
              onClick={handleCancelPreapproval}
              disabled={isCancellingPreapproval}
            >
              {isCancellingPreapproval ? 'Cancelando...' : 'Cancelar assinatura recorrente'}
            </button>
          )}
        </div>
      )}

      {checkoutError && <div className="form-error">{checkoutError}</div>}
      {preapprovalError && <div className="form-error">{preapprovalError}</div>}

      {!isLoading && !loadError && (
        <div className="inline-actions" style={{ flexWrap: 'wrap', gap: '1rem' }}>
          {plans.map((plan) => (
            <div className="card" key={plan.id} style={{ minWidth: '240px' }}>
              <h3>{plan.name}</h3>
              <p>
                R$ {plan.priceAmount.toFixed(2)} /mês — até {plan.maxDoctors} médicos
              </p>
              <div className="form-field">
                <label htmlFor={`period-${plan.id}`}>Período</label>
                <select
                  id={`period-${plan.id}`}
                  value={selectedPeriod[plan.id] ?? PERIOD_OPTIONS[0]}
                  onChange={(e) =>
                    setSelectedPeriod((prev) => ({ ...prev, [plan.id]: Number(e.target.value) }))
                  }
                >
                  {PERIOD_OPTIONS.map((months) => (
                    <option key={months} value={months}>
                      {months} {months === 1 ? 'mês' : 'meses'}
                    </option>
                  ))}
                </select>
              </div>
              <div className="inline-actions" style={{ gap: '0.5rem' }}>
                <button
                  type="button"
                  className="btn-primary btn-small"
                  style={{ width: 'auto' }}
                  onClick={() => handleCheckout(plan.id)}
                  disabled={checkingOutPlanId === plan.id}
                >
                  {checkingOutPlanId === plan.id ? 'Redirecionando...' : 'Assinar'}
                </button>
                <button
                  type="button"
                  className="btn-secondary btn-small"
                  style={{ width: 'auto' }}
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
