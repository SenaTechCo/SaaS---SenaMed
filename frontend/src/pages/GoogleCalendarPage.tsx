import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { ConnectUrlResponse, GoogleCalendarStatus } from '../types/googleCalendar';
import './dashboard-shared.css';

const STATUS_MESSAGES: Record<string, { text: string; className: string }> = {
  connected: {
    text: 'Google Calendar conectado com sucesso. Suas consultas serão sincronizadas automaticamente.',
    className: 'form-success',
  },
  error: {
    text: 'Não foi possível conectar ao Google Calendar. Tente novamente.',
    className: 'form-error',
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
      <div className="page-header">
        <div>
          <h2>Google Calendar</h2>
          <p className="subtitle">Sincronize suas consultas automaticamente com o seu Google Calendar.</p>
        </div>
      </div>

      {statusMessage && <div className={statusMessage.className}>{statusMessage.text}</div>}

      {isLoading && <p className="loading-state">Carregando...</p>}
      {loadError && <div className="form-error">{loadError}</div>}
      {actionError && <div className="form-error">{actionError}</div>}

      {!isLoading && !loadError && status && (
        <div className="card">
          {status.connected ? (
            <>
              <h3>Conectado</h3>
              <p>Conta conectada: {status.googleEmail}</p>
              <button
                type="button"
                className="btn-danger"
                onClick={handleDisconnect}
                disabled={isDisconnecting}
              >
                {isDisconnecting ? 'Desconectando...' : 'Desconectar'}
              </button>
            </>
          ) : (
            <>
              <h3>Não conectado</h3>
              <p>Conecte sua conta do Google para que suas consultas sejam criadas e canceladas automaticamente no seu Google Calendar.</p>
              <button
                type="button"
                className="btn-primary btn-small"
                style={{ width: 'auto' }}
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
