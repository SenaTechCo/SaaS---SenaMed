import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ClipboardList, Pencil, Plus, RotateCcw, Search, X } from 'lucide-react';
import { apiFetch, ApiError } from '../../lib/http';
import type { ServiceOffering, ServiceOfferingPayload } from '../../types/service';

const emptyForm: ServiceOfferingPayload = {
  name: '',
  description: null,
  durationMinutes: 30,
  price: 0,
};

function toPayload(form: ServiceOfferingPayload): ServiceOfferingPayload {
  return {
    ...form,
    description: form.description?.trim() || null,
  };
}

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export function ServicosTab() {
  const [services, setServices] = useState<ServiceOffering[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSlow, setIsSlow] = useState(false);
  const [search, setSearch] = useState('');

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState<ServiceOfferingPayload>(emptyForm);
  const [createError, setCreateError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<ServiceOfferingPayload>(emptyForm);
  const [editError, setEditError] = useState<string | null>(null);
  const [isSavingEdit, setIsSavingEdit] = useState(false);

  const [rowError, setRowError] = useState<string | null>(null);
  const [deactivatingId, setDeactivatingId] = useState<string | null>(null);
  const [restoringId, setRestoringId] = useState<string | null>(null);

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
        const query = search.trim() ? `?search=${encodeURIComponent(search.trim())}` : '';
        const list = await apiFetch<ServiceOffering[]>(`/api/services${query}`);
        if (cancelled) return;
        setServices(list);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar os serviços.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    const debounce = setTimeout(load, search ? 300 : 0);
    return () => {
      cancelled = true;
      clearTimeout(slowTimer);
      clearTimeout(debounce);
    };
  }, [search]);

  function openCreateForm() {
    setCreateForm(emptyForm);
    setCreateError(null);
    setShowCreateForm(true);
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateError(null);
    setIsCreating(true);
    try {
      const created = await apiFetch<ServiceOffering>('/api/services', {
        method: 'POST',
        body: toPayload(createForm),
      });
      setServices((prev) => [...prev, created].sort((a, b) => a.name.localeCompare(b.name)));
      setShowCreateForm(false);
      setCreateForm(emptyForm);
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : 'Não foi possível cadastrar o serviço. Tente novamente.');
    } finally {
      setIsCreating(false);
    }
  }

  function startEdit(service: ServiceOffering) {
    setEditingId(service.id);
    setEditForm({
      name: service.name,
      description: service.description,
      durationMinutes: service.durationMinutes,
      price: service.price,
    });
    setEditError(null);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditError(null);
  }

  async function handleSaveEdit(event: FormEvent<HTMLFormElement>, serviceId: string) {
    event.preventDefault();
    setEditError(null);
    setIsSavingEdit(true);
    try {
      const updated = await apiFetch<ServiceOffering>(`/api/services/${serviceId}`, {
        method: 'PUT',
        body: toPayload(editForm),
      });
      setServices((prev) => prev.map((s) => (s.id === serviceId ? updated : s)));
      setEditingId(null);
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : 'Não foi possível salvar as alterações.');
    } finally {
      setIsSavingEdit(false);
    }
  }

  async function handleDeactivate(service: ServiceOffering) {
    setRowError(null);
    const confirmed = window.confirm(`Desativar o serviço "${service.name}"?`);
    if (!confirmed) return;

    setDeactivatingId(service.id);
    try {
      await apiFetch<void>(`/api/services/${service.id}`, { method: 'DELETE' });
      setServices((prev) => prev.map((s) => (s.id === service.id ? { ...s, active: false } : s)));
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : 'Não foi possível desativar o serviço.');
    } finally {
      setDeactivatingId(null);
    }
  }

  async function handleRestore(service: ServiceOffering) {
    setRowError(null);
    setRestoringId(service.id);
    try {
      const updated = await apiFetch<ServiceOffering>(`/api/services/${service.id}/restaurar`, { method: 'PATCH' });
      setServices((prev) => prev.map((s) => (s.id === service.id ? updated : s)));
    } catch (err) {
      setRowError(err instanceof ApiError ? err.message : 'Não foi possível restaurar o serviço.');
    } finally {
      setRestoringId(null);
    }
  }

  const editingService = editingId !== null ? services.find((s) => s.id === editingId) : undefined;

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h3 className="text-base font-semibold text-slate-900">Serviços</h3>
          <p className="text-sm text-slate-500 mt-1">Cadastre e gerencie os serviços oferecidos pela clínica.</p>
        </div>
        <button
          type="button"
          onClick={openCreateForm}
          className="flex items-center justify-center gap-2 bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm w-full sm:w-auto"
        >
          <Plus className="w-4 h-4" /> Novo serviço
        </button>
      </div>

      <div className="relative mb-6 max-w-sm">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar por nome..."
          className="w-full pl-9 pr-4 py-2.5 border border-slate-200 rounded-xl text-sm bg-white shadow-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 transition-all"
        />
      </div>

      {showCreateForm && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-[2px] flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center gap-3">
              <div className="flex-1">
                <h2 className="text-lg font-semibold text-slate-900">Cadastrar serviço</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowCreateForm(false)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleCreate} noValidate className="p-6 space-y-4">
              {createError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{createError}</div>
              )}
              <ServiceFields form={createForm} setForm={setCreateForm} idPrefix="create" />
              <div className="flex gap-3 justify-end pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreateForm(false)}
                  className="px-4 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-sm font-medium hover:bg-slate-50 transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isCreating}
                  className="px-5 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 shadow-sm disabled:opacity-50 transition-all"
                >
                  {isCreating ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {editingId !== null && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-[2px] flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center gap-3">
              <div className="flex-1">
                <h2 className="text-lg font-semibold text-slate-900">
                  Editar serviço{editingService ? ` — ${editingService.name}` : ''}
                </h2>
              </div>
              <button
                type="button"
                onClick={cancelEdit}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={(e) => handleSaveEdit(e, editingId)} noValidate className="p-6 space-y-4">
              {editError && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{editError}</div>
              )}
              <ServiceFields form={editForm} setForm={setEditForm} idPrefix="edit" />
              <div className="flex gap-3 justify-end pt-2">
                <button
                  type="button"
                  onClick={cancelEdit}
                  className="px-4 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-sm font-medium hover:bg-slate-50 transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSavingEdit}
                  className="px-5 py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold hover:bg-primary-700 shadow-sm disabled:opacity-50 transition-all"
                >
                  {isSavingEdit ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {isLoading && (
        <div className="flex flex-col items-center gap-3 py-24">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
          {isSlow && (
            <p className="text-sm text-slate-500 text-center">
              Isso está demorando mais que o esperado — o servidor pode estar iniciando, aguarde alguns segundos.
            </p>
          )}
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
          {services.length === 0 ? (
            <div className="px-6 py-16 text-center">
              <div className="flex flex-col items-center gap-3">
                <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
                  <ClipboardList className="w-6 h-6 text-slate-400" />
                </div>
                <p className="text-slate-500 text-sm">
                  {search ? 'Nenhum serviço encontrado para essa busca.' : 'Nenhum serviço cadastrado ainda.'}
                </p>
              </div>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[700px]">
                <thead>
                  <tr className="bg-slate-50/80 border-b border-slate-100">
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Nome</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Duração</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Preço</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-slate-500 uppercase tracking-wider">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {services.map((service) => (
                    <tr key={service.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/80 transition-colors">
                      <td className="px-5 py-3.5 text-sm text-slate-700 font-medium">
                        {service.name}
                        {service.description && <div className="text-slate-500 font-normal">{service.description}</div>}
                      </td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">{service.durationMinutes} min</td>
                      <td className="px-5 py-3.5 text-sm text-slate-700">{currencyFormatter.format(service.price)}</td>
                      <td className="px-5 py-3.5 text-sm">
                        <span
                          className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                            service.active ? 'bg-green-50 text-green-700' : 'bg-slate-100 text-slate-500'
                          }`}
                        >
                          {service.active ? 'Ativo' : 'Inativo'}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-sm">
                        <div className="flex items-center gap-3 flex-wrap">
                          <button
                            type="button"
                            onClick={() => startEdit(service)}
                            className="p-1.5 rounded-lg hover:bg-primary-50 text-slate-400 hover:text-primary-600 transition-colors"
                            aria-label={`Editar ${service.name}`}
                          >
                            <Pencil className="w-4 h-4" />
                          </button>
                          {service.active ? (
                            <button
                              type="button"
                              onClick={() => handleDeactivate(service)}
                              disabled={deactivatingId === service.id}
                              className="px-4 py-2.5 bg-red-600 text-white rounded-xl text-sm font-semibold hover:bg-red-700 disabled:opacity-50 transition-colors"
                            >
                              {deactivatingId === service.id ? 'Desativando...' : 'Desativar'}
                            </button>
                          ) : (
                            <button
                              type="button"
                              onClick={() => handleRestore(service)}
                              disabled={restoringId === service.id}
                              className="flex items-center gap-1 text-primary-600 hover:text-primary-700 text-sm font-medium"
                            >
                              <RotateCcw className="w-3.5 h-3.5" /> {restoringId === service.id ? 'Restaurando...' : 'Restaurar'}
                            </button>
                          )}
                        </div>
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

const fieldClassName =
  'w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all';
const labelClassName = 'block text-sm font-medium text-slate-700 mb-1.5';

function ServiceFields({
  form,
  setForm,
  idPrefix,
}: {
  form: ServiceOfferingPayload;
  setForm: (updater: (f: ServiceOfferingPayload) => ServiceOfferingPayload) => void;
  idPrefix: string;
}) {
  return (
    <>
      <div>
        <label htmlFor={`${idPrefix}-name`} className={labelClassName}>Nome</label>
        <input
          id={`${idPrefix}-name`}
          type="text"
          value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          required
          className={fieldClassName}
        />
      </div>
      <div>
        <label htmlFor={`${idPrefix}-description`} className={labelClassName}>Descrição (opcional)</label>
        <textarea
          id={`${idPrefix}-description`}
          value={form.description ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
          rows={3}
          className={`${fieldClassName} resize-vertical`}
        />
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label htmlFor={`${idPrefix}-duration`} className={labelClassName}>Duração em minutos</label>
          <input
            id={`${idPrefix}-duration`}
            type="number"
            min="5"
            value={form.durationMinutes}
            onChange={(e) => setForm((f) => ({ ...f, durationMinutes: Number(e.target.value) }))}
            required
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-price`} className={labelClassName}>Preço</label>
          <input
            id={`${idPrefix}-price`}
            type="number"
            step="0.01"
            min="0"
            value={form.price}
            onChange={(e) => setForm((f) => ({ ...f, price: Number(e.target.value) }))}
            required
            className={fieldClassName}
          />
        </div>
      </div>
    </>
  );
}
