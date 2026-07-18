import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { apiFetch, ApiError } from '../lib/http';
import type { Appointment, AppointmentPayload, AvailableSlotsResponse, PublicClinic, PublicDoctor } from '../types/appointment';
import './auth-pages.css';
import './dashboard-shared.css';
import './PublicClinicPage.css';

const DEFAULT_PRIMARY_COLOR = '#4f46e5';
const DEFAULT_SECONDARY_COLOR = '#111827';

function todayAsInputValue(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

interface BookingForm {
  patientName: string;
  patientEmail: string;
  patientPhone: string;
  lgpdConsent: boolean;
}

const emptyBookingForm: BookingForm = {
  patientName: '',
  patientEmail: '',
  patientPhone: '',
  lgpdConsent: false,
};

export function PublicClinicPage() {
  const { slug } = useParams<{ slug: string }>();

  const [clinic, setClinic] = useState<PublicClinic | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [selectedDoctor, setSelectedDoctor] = useState<PublicDoctor | null>(null);
  const [selectedDate, setSelectedDate] = useState('');
  const [slots, setSlots] = useState<string[] | null>(null);
  const [isSlotsLoading, setIsSlotsLoading] = useState(false);
  const [slotsError, setSlotsError] = useState<string | null>(null);
  const [selectedSlot, setSelectedSlot] = useState<string | null>(null);

  const [bookingForm, setBookingForm] = useState<BookingForm>(emptyBookingForm);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [appointment, setAppointment] = useState<Appointment | null>(null);

  useEffect(() => {
    if (!slug) return;
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const data = await apiFetch<PublicClinic>(`/api/public/clinics/${slug}`);
        if (cancelled) return;
        setClinic(data);
      } catch (err) {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 404) {
          setLoadError('Clínica não encontrada.');
        } else {
          setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar a clínica.');
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [slug]);

  async function loadSlots(doctorId: number, date: string) {
    setIsSlotsLoading(true);
    setSlotsError(null);
    setSlots(null);
    try {
      const data = await apiFetch<AvailableSlotsResponse>(
        `/api/public/doctors/${doctorId}/available-slots?date=${date}`,
      );
      setSlots(data.slots);
    } catch (err) {
      setSlotsError(err instanceof ApiError ? err.message : 'Não foi possível carregar os horários disponíveis.');
    } finally {
      setIsSlotsLoading(false);
    }
  }

  function handleSelectDoctor(doctor: PublicDoctor) {
    setSelectedDoctor(doctor);
    setSelectedDate('');
    setSlots(null);
    setSlotsError(null);
    setSelectedSlot(null);
    setAppointment(null);
    setSubmitError(null);
  }

  function handleSelectDate(date: string) {
    setSelectedDate(date);
    setSelectedSlot(null);
    setSubmitError(null);
    if (selectedDoctor && date) {
      loadSlots(selectedDoctor.id, date);
    }
  }

  function handleSelectSlot(slot: string) {
    setSelectedSlot(slot);
    setSubmitError(null);
    setBookingForm(emptyBookingForm);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedDoctor || !selectedDate || !selectedSlot) return;
    setSubmitError(null);

    const payload: AppointmentPayload = {
      doctorId: selectedDoctor.id,
      date: selectedDate,
      startTime: selectedSlot,
      patientName: bookingForm.patientName,
      patientEmail: bookingForm.patientEmail,
      patientPhone: bookingForm.patientPhone.trim() ? bookingForm.patientPhone.trim() : null,
      lgpdConsent: bookingForm.lgpdConsent,
    };

    setIsSubmitting(true);
    try {
      const created = await apiFetch<Appointment>('/api/public/appointments', {
        method: 'POST',
        body: payload,
      });
      setAppointment(created);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setSubmitError(err.message || 'Este horário não está mais disponível. Escolha outro horário.');
        setSelectedSlot(null);
        loadSlots(selectedDoctor.id, selectedDate);
      } else if (err instanceof ApiError && err.status === 429) {
        setSubmitError('Muitas tentativas, aguarde um instante e tente novamente.');
      } else if (err instanceof ApiError) {
        setSubmitError(err.message);
      } else {
        setSubmitError('Não foi possível concluir o agendamento. Tente novamente.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  const primaryColor = clinic?.primaryColor ?? DEFAULT_PRIMARY_COLOR;
  const secondaryColor = clinic?.secondaryColor ?? DEFAULT_SECONDARY_COLOR;

  return (
    <div className="public-page">
      {isLoading && <p className="loading-state">Carregando...</p>}
      {loadError && (
        <div className="public-container">
          <div className="form-error">{loadError}</div>
        </div>
      )}

      {!isLoading && !loadError && clinic && (
        <>
          <header className="public-header" style={{ background: primaryColor }}>
            {clinic.coverImageUrl && (
              <img className="public-cover" src={clinic.coverImageUrl} alt={`Capa de ${clinic.name}`} />
            )}
            <div className="public-header-content" style={{ color: secondaryColor }}>
              {clinic.logoUrl && <img className="public-logo" src={clinic.logoUrl} alt={`Logo de ${clinic.name}`} />}
              <div>
                <h1>{clinic.name}</h1>
                {clinic.description && <p className="public-description">{clinic.description}</p>}
                <div className="public-contact">
                  {clinic.phone && <span>{clinic.phone}</span>}
                  {clinic.email && <span>{clinic.email}</span>}
                </div>
              </div>
            </div>
          </header>

          <div className="public-container">
            {appointment ? (
              <div className="card public-confirmation">
                <h2>Agendamento confirmado</h2>
                <p className="form-success">Seu agendamento foi realizado com sucesso.</p>
                <dl className="public-summary">
                  <dt>Médico</dt>
                  <dd>{appointment.doctorName}</dd>
                  <dt>Data</dt>
                  <dd>{appointment.date}</dd>
                  <dt>Horário</dt>
                  <dd>{appointment.startTime} — {appointment.endTime}</dd>
                  <dt>Paciente</dt>
                  <dd>{appointment.patientName}</dd>
                </dl>
                <div className="public-cancel-notice">
                  <p>
                    Guarde o link abaixo caso precise cancelar este agendamento. Ele não será exibido novamente.
                  </p>
                  <Link className="btn-link" to={`/cancelar/${appointment.cancelToken}`}>
                    {`${window.location.origin}/cancelar/${appointment.cancelToken}`}
                  </Link>
                </div>
              </div>
            ) : (
              <>
                <section className="card">
                  <h2>Escolha o médico</h2>
                  {clinic.doctors.length === 0 ? (
                    <p className="empty-state">Nenhum médico disponível para agendamento no momento.</p>
                  ) : (
                    <div className="doctor-grid">
                      {clinic.doctors.map((doctor) => (
                        <button
                          key={doctor.id}
                          type="button"
                          className={`doctor-card ${selectedDoctor?.id === doctor.id ? 'doctor-card-selected' : ''}`}
                          style={selectedDoctor?.id === doctor.id ? { borderColor: primaryColor } : undefined}
                          onClick={() => handleSelectDoctor(doctor)}
                        >
                          <strong>{doctor.name}</strong>
                          {doctor.specialty && <span>{doctor.specialty}</span>}
                        </button>
                      ))}
                    </div>
                  )}
                </section>

                {selectedDoctor && (
                  <section className="card">
                    <h2>Escolha a data</h2>
                    <div className="form-field">
                      <label htmlFor="appointment-date">Data</label>
                      <input
                        id="appointment-date"
                        type="date"
                        min={todayAsInputValue()}
                        value={selectedDate}
                        onChange={(e) => handleSelectDate(e.target.value)}
                      />
                    </div>

                    {selectedDate && (
                      <>
                        {isSlotsLoading && <p className="loading-state">Carregando horários...</p>}
                        {slotsError && <div className="form-error">{slotsError}</div>}
                        {!isSlotsLoading && !slotsError && slots && (
                          slots.length === 0 ? (
                            <p className="empty-state">Nenhum horário disponível nesta data.</p>
                          ) : (
                            <div className="slot-grid">
                              {slots.map((slot) => (
                                <button
                                  key={slot}
                                  type="button"
                                  className={`slot-button ${selectedSlot === slot ? 'slot-button-selected' : ''}`}
                                  style={selectedSlot === slot ? { background: primaryColor, borderColor: primaryColor } : undefined}
                                  onClick={() => handleSelectSlot(slot)}
                                >
                                  {slot}
                                </button>
                              ))}
                            </div>
                          )
                        )}
                      </>
                    )}
                  </section>
                )}

                {selectedDoctor && selectedDate && selectedSlot && (
                  <section className="card">
                    <h2>Seus dados</h2>
                    <p className="subtitle">
                      {selectedDoctor.name} — {selectedDate} às {selectedSlot}
                    </p>
                    {submitError && <div className="form-error">{submitError}</div>}
                    <form onSubmit={handleSubmit} noValidate>
                      <div className="form-field">
                        <label htmlFor="patient-name">Nome completo</label>
                        <input
                          id="patient-name"
                          type="text"
                          value={bookingForm.patientName}
                          onChange={(e) => setBookingForm((f) => ({ ...f, patientName: e.target.value }))}
                          required
                        />
                      </div>
                      <div className="form-inline-row">
                        <div className="form-field">
                          <label htmlFor="patient-email">E-mail</label>
                          <input
                            id="patient-email"
                            type="email"
                            value={bookingForm.patientEmail}
                            onChange={(e) => setBookingForm((f) => ({ ...f, patientEmail: e.target.value }))}
                            required
                          />
                        </div>
                        <div className="form-field">
                          <label htmlFor="patient-phone">Telefone (opcional)</label>
                          <input
                            id="patient-phone"
                            type="tel"
                            value={bookingForm.patientPhone}
                            onChange={(e) => setBookingForm((f) => ({ ...f, patientPhone: e.target.value }))}
                          />
                        </div>
                      </div>

                      <label className="lgpd-consent">
                        <input
                          type="checkbox"
                          checked={bookingForm.lgpdConsent}
                          onChange={(e) => setBookingForm((f) => ({ ...f, lgpdConsent: e.target.checked }))}
                        />
                        Autorizo o uso dos meus dados para fins deste agendamento, conforme a LGPD.
                      </label>

                      <button
                        type="submit"
                        className="btn-primary btn-small"
                        style={{ width: 'auto', background: primaryColor }}
                        disabled={!bookingForm.lgpdConsent || isSubmitting}
                      >
                        {isSubmitting ? 'Confirmando...' : 'Confirmar agendamento'}
                      </button>
                    </form>
                  </section>
                )}
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}
