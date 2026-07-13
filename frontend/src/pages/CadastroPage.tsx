import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { apiFetch, ApiError } from '../lib/http';
import { useAuth } from '../context/AuthContext';
import type { AuthResponse, RegisterClinicPayload } from '../types/auth';
import './auth-pages.css';

export function CadastroPage() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [clinicName, setClinicName] = useState('');
  const [adminName, setAdminName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    if (password.length < 8) {
      setError('A senha deve ter no mínimo 8 caracteres.');
      return;
    }

    if (password !== confirmPassword) {
      setError('As senhas não coincidem.');
      return;
    }

    const payload: RegisterClinicPayload = {
      clinicName,
      adminName,
      email,
      password,
    };

    setIsSubmitting(true);
    try {
      const auth = await apiFetch<AuthResponse>('/api/auth/register-clinic', {
        method: 'POST',
        body: payload,
      });
      login(auth);
      navigate('/dashboard', { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError('Este e-mail já está cadastrado.');
      } else if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Não foi possível concluir o cadastro. Tente novamente.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Cadastre sua clínica</h1>
        <p className="subtitle">Crie sua conta para começar a usar o SenaMed.</p>

        {error && <div className="form-error">{error}</div>}

        <form onSubmit={handleSubmit} noValidate>
          <div className="form-field">
            <label htmlFor="clinicName">Nome da clínica</label>
            <input
              id="clinicName"
              type="text"
              value={clinicName}
              onChange={(e) => setClinicName(e.target.value)}
              required
            />
          </div>

          <div className="form-field">
            <label htmlFor="adminName">Nome do administrador</label>
            <input
              id="adminName"
              type="text"
              value={adminName}
              onChange={(e) => setAdminName(e.target.value)}
              required
            />
          </div>

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
              minLength={8}
              required
            />
          </div>

          <div className="form-field">
            <label htmlFor="confirmPassword">Confirme a senha</label>
            <input
              id="confirmPassword"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              minLength={8}
              required
            />
          </div>

          <button type="submit" className="btn-primary" disabled={isSubmitting}>
            {isSubmitting ? 'Cadastrando...' : 'Cadastrar clínica'}
          </button>
        </form>

        <p className="auth-switch">
          Já tem uma conta? <Link to="/login">Entrar</Link>
        </p>
      </div>
    </div>
  );
}
