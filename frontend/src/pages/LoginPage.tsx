import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { apiFetch, ApiError } from '../lib/http';
import { useAuth } from '../context/AuthContext';
import type { AuthResponse, LoginPayload } from '../types/auth';
import './auth-pages.css';

export function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const payload: LoginPayload = { email, password };

    setIsSubmitting(true);
    try {
      const auth = await apiFetch<AuthResponse>('/api/auth/login', {
        method: 'POST',
        body: payload,
      });
      login(auth);
      navigate('/dashboard', { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError('E-mail ou senha inválidos.');
      } else if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Não foi possível fazer login. Tente novamente.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Entrar</h1>
        <p className="subtitle">Acesse o painel da sua clínica.</p>

        {error && <div className="form-error">{error}</div>}

        <form onSubmit={handleSubmit} noValidate>
          <div className="form-field">
            <label htmlFor="email">E-mail</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <div className="form-field">
            <label htmlFor="password">Senha</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button type="submit" className="btn-primary" disabled={isSubmitting}>
            {isSubmitting ? 'Entrando...' : 'Entrar'}
          </button>
        </form>

        <p className="auth-switch">
          Ainda não tem conta? <Link to="/cadastro">Cadastre sua clínica</Link>
        </p>
      </div>
    </div>
  );
}
