import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Link2, Unlink } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { ConnectUrlResponse, GoogleCalendarStatus } from '../types/googleCalendar';

const STATUS_MESSAGES: Record<string, { text: string; className: string }> = {
  connected: {
    text: 'Google Calendar conectado com sucesso. Suas consultas serão sincronizadas automaticamente.',
    className: 'bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-6',
  },
  error: {
    text: 'Não foi possível conectar ao Google Calendar. Tente novamente.',
    className: 'bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6',
  },
};

export function GoogleCalendarPage() {
  const [searchParams] = useSearchParams();
  const redirectStatus = searchParams.get('status');

  const [status, setStatus] = useState<GoogleCalendarStatus | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [isConnecting, setIsConnecting] = useState(false);
  const [isDisconnecting, setIsDisconnecting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const current = await apiFetch<GoogleCalendarStatus>('/api/doctors/me/google-calendar');
        if (cancelled) return;
        setStatus(current);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar o status da conexão.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleConnect() {
    setActionError(null);
    setIsConnecting(true);
    try {
      const response = await apiFetch<ConnectUrlResponse>('/api/doctors/me/google-calendar/connect-url');
      window.location.href = response.authorizationUrl;
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Não foi possível iniciar a conexão com o Google.');
      setIsConnecting(false);
    }
  }

  async function handleDisconnect() {
    setActionError(null);
    setIsDisconnecting(true);
    try {
      await apiFetch<void>('/api/doctors/me/google-calendar', { method: 'DELETE' });
      setStatus({ connected: false, googleEmail: null });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Não foi possível desconectar o Google Calendar.');
    } finally {
      setIsDisconnecting(false);
    }
  }

  const statusMessage = redirectStatus ? STATUS_MESSAGES[redirectStatus] : null;

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Google Calendar</h1>
          <p className="text-sm text-slate-500 mt-1">Sincronize suas consultas automaticamente com o seu Google Calendar.</p>
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
      {actionError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">
          {actionError}
        </div>
      )}

      {!isLoading && !loadError && status && (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] p-6">
          {status.connected ? (
            <>
              <div className="flex items-center gap-3 mb-3">
                <div className="w-9 h-9 bg-green-50 rounded-xl flex items-center justify-center flex-shrink-0">
                  <Link2 className="w-4.5 h-4.5 text-green-600" />
                </div>
                <div>
                  <h3 className="text-base font-semibold text-slate-900">Conectado</h3>
                  <span className="inline-block rounded-full px-2.5 py-0.5 text-xs font-medium bg-green-50 text-green-700">
                    Ativo
                  </span>
                </div>
              </div>
              <p className="text-sm text-slate-600 mb-4">Conta conectada: {status.googleEmail}</p>
              <button type="button" className="px-4 py-2.5 bg-red-600 text-white rounded-xl text-sm font-semibold hover:bg-red-700 disabled:opacity-50 transition-colors" onClick={handleDisconnect} disabled={isDisconnecting}>
                {isDisconnecting ? 'Desconectando...' : 'Desconectar'}
              </button>
            </>
          ) : (
            <>
              <div className="flex items-center gap-3 mb-3">
                <div className="w-9 h-9 bg-slate-100 rounded-xl flex items-center justify-center flex-shrink-0">
                  <Unlink className="w-4.5 h-4.5 text-slate-400" />
                </div>
                <div>
                  <h3 className="text-base font-semibold text-slate-900">Não conectado</h3>
                  <span className="inline-block rounded-full px-2.5 py-0.5 text-xs font-medium bg-slate-100 text-slate-500">
                    Inativo
                  </span>
                </div>
              </div>
              <p className="text-sm text-slate-600 mb-4">
                Conecte sua conta do Google para que suas consultas sejam criadas e canceladas automaticamente no seu Google Calendar.
              </p>
              <button
                type="button"
                className="bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm disabled:opacity-50"
                onClick={handleConnect}
                disabled={isConnecting}
              >
                {isConnecting ? 'Redirecionando...' : 'Conectar Google Calendar'}
              </button>
            </>
          )}
        </div>
      )}
    </DashboardLayout>
  );
}
