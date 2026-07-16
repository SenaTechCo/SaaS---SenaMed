import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Stethoscope } from 'lucide-react';
import { apiFetch, ApiError } from '../lib/http';
import { useAuth } from '../context/AuthContext';
import type { AuthResponse, LoginPayload } from '../types/auth';

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
    <div className="min-h-screen relative flex items-center justify-center overflow-hidden p-6 font-sans">
      <div className="absolute inset-0 bg-[linear-gradient(160deg,#0A2342_0%,#0E4D6C_35%,#005F7F_65%,#0A3D55_100%)]" />
      <div className="absolute inset-0 bg-[linear-gradient(145deg,rgba(0,96,120,0.55)_0%,rgba(2,60,100,0.45)_40%,rgba(0,80,110,0.52)_100%)]" />

      <div className="relative w-full max-w-md bg-white/[0.52] backdrop-blur-2xl backdrop-saturate-150 border border-white/60 rounded-[20px] shadow-[0_24px_64px_rgba(0,50,110,0.28),0_1px_0_rgba(255,255,255,0.7)_inset] px-8 pt-9 pb-8">
        <div className="flex items-center gap-2.5 mb-6">
          <div className="w-9 h-9 bg-primary-600 rounded-lg flex items-center justify-center flex-shrink-0 shadow-lg shadow-primary-600/30">
            <Stethoscope className="w-5 h-5 text-white" />
          </div>
          <span className="font-bold text-[17px] text-[#0A2342]">SenaMed</span>
        </div>

        <h1 className="text-2xl font-bold text-[#0A2342] mb-1">Entrar</h1>
        <p className="text-sm text-[#0A2342]/70 mb-6">Acesse o painel da sua clínica.</p>

        {error && (
          <div className="bg-red-50/85 border border-red-400/30 text-red-700 text-sm px-3.5 py-2.5 rounded-[10px] mb-4">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-[#0A2342]/80 mb-1.5">
              E-mail
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full px-3.5 py-2.5 rounded-[10px] text-sm bg-white/70 border-[1.5px] border-white/70 text-[#1A2744] shadow-[0_1px_4px_rgba(0,40,100,0.06)] focus:bg-white/90 focus:border-primary-500 focus:ring-2 focus:ring-primary-500/15 outline-none transition-all"
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-[#0A2342]/80 mb-1.5">
              Senha
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="w-full px-3.5 py-2.5 rounded-[10px] text-sm bg-white/70 border-[1.5px] border-white/70 text-[#1A2744] shadow-[0_1px_4px_rgba(0,40,100,0.06)] focus:bg-white/90 focus:border-primary-500 focus:ring-2 focus:ring-primary-500/15 outline-none transition-all"
            />
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full py-3.5 rounded-xl text-white font-bold bg-gradient-to-br from-primary-500 to-primary-800 shadow-[0_4px_20px_rgba(21,101,192,0.45)] hover:shadow-[0_6px_24px_rgba(21,101,192,0.55)] disabled:shadow-none disabled:opacity-70 transition-all"
          >
            {isSubmitting ? 'Entrando...' : 'Entrar'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-[#0A2342]/70">
          Ainda não tem conta?{' '}
          <Link to="/cadastro" className="font-semibold text-primary-700 hover:text-primary-800">
            Cadastre sua clínica
          </Link>
        </p>
      </div>
    </div>
  );
}
