import type { PatientPayload } from '../../types/patient';

export const fieldClassName =
  'w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all';
export const labelClassName = 'block text-sm font-medium text-slate-700 mb-1.5';

export function PatientFields({
  form,
  setForm,
  idPrefix,
}: {
  form: PatientPayload;
  setForm: (updater: (f: PatientPayload) => PatientPayload) => void;
  idPrefix: string;
}) {
  return (
    <>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
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
          <label htmlFor={`${idPrefix}-social-name`} className={labelClassName}>Nome social (opcional)</label>
          <input
            id={`${idPrefix}-social-name`}
            type="text"
            value={form.socialName ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, socialName: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-birth-date`} className={labelClassName}>Data de nascimento</label>
          <input
            id={`${idPrefix}-birth-date`}
            type="date"
            value={form.birthDate ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, birthDate: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-sex`} className={labelClassName}>Sexo</label>
          <select
            id={`${idPrefix}-sex`}
            value={form.sex ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, sex: e.target.value || null }))}
            className={fieldClassName}
          >
            <option value="">Não informado</option>
            <option value="F">Feminino</option>
            <option value="M">Masculino</option>
            <option value="O">Outro</option>
          </select>
        </div>
        <div>
          <label htmlFor={`${idPrefix}-cpf`} className={labelClassName}>CPF</label>
          <input
            id={`${idPrefix}-cpf`}
            type="text"
            value={form.cpf ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, cpf: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-email`} className={labelClassName}>E-mail</label>
          <input
            id={`${idPrefix}-email`}
            type="email"
            value={form.email ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-phone`} className={labelClassName}>Telefone</label>
          <input
            id={`${idPrefix}-phone`}
            type="tel"
            value={form.phone ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-referral`} className={labelClassName}>Como conheceu a clínica</label>
          <input
            id={`${idPrefix}-referral`}
            type="text"
            value={form.referralSource ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, referralSource: e.target.value }))}
            className={fieldClassName}
          />
        </div>
      </div>

      <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider pt-2">Endereço</p>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <label htmlFor={`${idPrefix}-zip`} className={labelClassName}>CEP</label>
          <input
            id={`${idPrefix}-zip`}
            type="text"
            value={form.zipCode ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, zipCode: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div className="sm:col-span-2">
          <label htmlFor={`${idPrefix}-street`} className={labelClassName}>Logradouro</label>
          <input
            id={`${idPrefix}-street`}
            type="text"
            value={form.street ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, street: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-number`} className={labelClassName}>Número</label>
          <input
            id={`${idPrefix}-number`}
            type="text"
            value={form.number ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, number: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-complement`} className={labelClassName}>Complemento</label>
          <input
            id={`${idPrefix}-complement`}
            type="text"
            value={form.complement ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, complement: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-neighborhood`} className={labelClassName}>Bairro</label>
          <input
            id={`${idPrefix}-neighborhood`}
            type="text"
            value={form.neighborhood ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, neighborhood: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-city`} className={labelClassName}>Cidade</label>
          <input
            id={`${idPrefix}-city`}
            type="text"
            value={form.city ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, city: e.target.value }))}
            className={fieldClassName}
          />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-state`} className={labelClassName}>UF</label>
          <input
            id={`${idPrefix}-state`}
            type="text"
            maxLength={2}
            value={form.state ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, state: e.target.value.toUpperCase() }))}
            className={fieldClassName}
          />
        </div>
      </div>

      <div>
        <label htmlFor={`${idPrefix}-notes`} className={labelClassName}>Observações</label>
        <textarea
          id={`${idPrefix}-notes`}
          value={form.notes ?? ''}
          onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
          rows={3}
          className={`${fieldClassName} resize-vertical`}
        />
      </div>

      <label className="flex items-start gap-2 text-sm text-slate-600">
        <input
          type="checkbox"
          checked={form.lgpdConsent}
          onChange={(e) => setForm((f) => ({ ...f, lgpdConsent: e.target.checked }))}
          className="mt-0.5 w-4 h-4 rounded border-slate-300 text-primary-600 focus:ring-primary-200"
        />
        Paciente autorizou o uso dos seus dados, conforme a LGPD.
      </label>
    </>
  );
}
