import { useState } from 'react';
import type { FormEvent } from 'react';
import { DashboardLayout } from '../components/DashboardLayout';
import { ServicosTab } from '../components/configuracoes/ServicosTab';
import { useAuth } from '../context/AuthContext';
import { apiFetch, ApiError } from '../lib/http';
import type { User } from '../types/auth';

type Tab = 'conta' | 'seguranca' | 'servicos';

interface AccountForm {
  name: string;
  email: string;
}

interface PasswordForm {
  currentPassword: string;
  newPassword: string;
  confirmNewPassword: string;
}

const emptyPasswordForm: PasswordForm = {
  currentPassword: '',
  newPassword: '',
  confirmNewPassword: '',
};

export function ConfiguracoesPage() {
  const { user, updateUser } = useAuth();
  const [tab, setTab] = useState<Tab>('conta');

  const [accountForm, setAccountForm] = useState<AccountForm>({
    name: user?.name ?? '',
    email: user?.email ?? '',
  });
  const [isSavingAccount, setIsSavingAccount] = useState(false);
  const [accountError, setAccountError] = useState<string | null>(null);
  const [accountSuccess, setAccountSuccess] = useState(false);

  const [passwordForm, setPasswordForm] = useState<PasswordForm>(emptyPasswordForm);
  const [isSavingPassword, setIsSavingPassword] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState(false);

  function updateAccountField<K extends keyof AccountForm>(field: K, value: AccountForm[K]) {
    setAccountForm((prev) => ({ ...prev, [field]: value }));
    setAccountSuccess(false);
  }

  function updatePasswordField<K extends keyof PasswordForm>(field: K, value: PasswordForm[K]) {
    setPasswordForm((prev) => ({ ...prev, [field]: value }));
    setPasswordSuccess(false);
  }

  async function handleSaveAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAccountError(null);
    setAccountSuccess(false);
    setIsSavingAccount(true);
    try {
      const updated = await apiFetch<User>('/api/users/me', {
        method: 'PUT',
        body: { name: accountForm.name, email: accountForm.email },
      });
      updateUser(updated);
      setAccountSuccess(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setAccountError('Este e-mail já está em uso.');
      } else {
        setAccountError(err instanceof ApiError ? err.message : 'Não foi possível salvar as alterações.');
      }
    } finally {
      setIsSavingAccount(false);
    }
  }

  async function handleSavePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordError(null);
    setPasswordSuccess(false);

    if (passwordForm.newPassword !== passwordForm.confirmNewPassword) {
      setPasswordError('A nova senha e a confirmação não coincidem.');
      return;
    }

    setIsSavingPassword(true);
    try {
      await apiFetch<void>('/api/users/me/password', {
        method: 'PUT',
        body: {
          currentPassword: passwordForm.currentPassword,
          newPassword: passwordForm.newPassword,
        },
      });
      setPasswordForm(emptyPasswordForm);
      setPasswordSuccess(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) {
        setPasswordError(err.message || 'Senha atual incorreta.');
      } else {
        setPasswordError(err instanceof ApiError ? err.message : 'Não foi possível alterar a senha.');
      }
    } finally {
      setIsSavingPassword(false);
    }
  }

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Configurações</h1>
          <p className="text-sm text-slate-500 mt-1">Gerencie seus dados de conta e segurança.</p>
        </div>
      </div>

      <div className="flex gap-2 mb-6 border-b border-slate-200">
        <button
          type="button"
          onClick={() => setTab('conta')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'conta'
              ? 'border-primary-600 text-primary-600'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          Conta
        </button>
        <button
          type="button"
          onClick={() => setTab('seguranca')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'seguranca'
              ? 'border-primary-600 text-primary-600'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          Segurança
        </button>
        <button
          type="button"
          onClick={() => setTab('servicos')}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            tab === 'servicos'
              ? 'border-primary-600 text-primary-600'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          Serviços
        </button>
      </div>

      {tab === 'servicos' && <ServicosTab />}

      {tab === 'conta' && (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] p-6 max-w-lg">
          <h3 className="text-base font-semibold text-slate-900 mb-4">Dados da conta</h3>

          {accountError && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">
              {accountError}
            </div>
          )}
          {accountSuccess && (
            <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-4">
              Alterações salvas com sucesso.
            </div>
          )}

          <form onSubmit={handleSaveAccount} noValidate className="space-y-4">
            <div>
              <label htmlFor="account-name" className="block text-sm font-medium text-slate-700 mb-1.5">
                Nome
              </label>
              <input
                id="account-name"
                type="text"
                value={accountForm.name}
                onChange={(e) => updateAccountField('name', e.target.value)}
                required
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
              />
            </div>

            <div>
              <label htmlFor="account-email" className="block text-sm font-medium text-slate-700 mb-1.5">
                E-mail
              </label>
              <input
                id="account-email"
                type="email"
                value={accountForm.email}
                onChange={(e) => updateAccountField('email', e.target.value)}
                required
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
              />
            </div>

            <div>
              <span className="block text-sm font-medium text-slate-700 mb-1.5">Perfil</span>
              <span className="inline-block px-2.5 py-0.5 bg-primary-50 text-primary-700 text-xs font-semibold rounded-full">
                {user?.role === 'ADMIN' ? 'Administrador' : 'Médico'}
              </span>
            </div>

            <button
              type="submit"
              disabled={isSavingAccount}
              className="bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm disabled:opacity-50"
            >
              {isSavingAccount ? 'Salvando...' : 'Salvar alterações'}
            </button>
          </form>
        </div>
      )}

      {tab === 'seguranca' && (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)] p-6 max-w-lg">
          <h3 className="text-base font-semibold text-slate-900 mb-4">Alterar senha</h3>

          {passwordError && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">
              {passwordError}
            </div>
          )}
          {passwordSuccess && (
            <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-4">
              Senha alterada com sucesso.
            </div>
          )}

          <form onSubmit={handleSavePassword} noValidate className="space-y-4">
            <div>
              <label htmlFor="current-password" className="block text-sm font-medium text-slate-700 mb-1.5">
                Senha atual
              </label>
              <input
                id="current-password"
                type="password"
                value={passwordForm.currentPassword}
                onChange={(e) => updatePasswordField('currentPassword', e.target.value)}
                required
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
              />
            </div>

            <div>
              <label htmlFor="new-password" className="block text-sm font-medium text-slate-700 mb-1.5">
                Nova senha
              </label>
              <input
                id="new-password"
                type="password"
                value={passwordForm.newPassword}
                onChange={(e) => updatePasswordField('newPassword', e.target.value)}
                required
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
              />
            </div>

            <div>
              <label htmlFor="confirm-new-password" className="block text-sm font-medium text-slate-700 mb-1.5">
                Confirmar nova senha
              </label>
              <input
                id="confirm-new-password"
                type="password"
                value={passwordForm.confirmNewPassword}
                onChange={(e) => updatePasswordField('confirmNewPassword', e.target.value)}
                required
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
              />
            </div>

            <button
              type="submit"
              disabled={isSavingPassword}
              className="bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm disabled:opacity-50"
            >
              {isSavingPassword ? 'Salvando...' : 'Salvar alterações'}
            </button>
          </form>
        </div>
      )}
    </DashboardLayout>
  );
}
