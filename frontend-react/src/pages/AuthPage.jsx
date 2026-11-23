import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useStore } from '../context/StoreContext';

export default function AuthPage({ mode = 'login' }) {
  const [isRegister, setIsRegister] = useState(mode === 'register');
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '' });
  const [busy, setBusy] = useState(false);
  const { loginSuccess, notify } = useStore();
  const navigate = useNavigate();

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const endpoint = isRegister ? '/auth/register' : '/auth/login';
      const body = isRegister
        ? form
        : { email: form.email, password: form.password };
      const res = await api(endpoint, { method: 'POST', body });
      await loginSuccess(res);
      navigate('/profile');
    } catch (err) {
      notify(err.message || 'Authentication failed', 'error');
    } finally {
      setBusy(false);
    }
  };

  const continueWithGoogle = async () => {
    setBusy(true);
    try {
      const cfg = await api('/auth/google-config').catch(() => ({}));
      if (cfg?.clientId && window.google?.accounts?.oauth2) {
        const client = window.google.accounts.oauth2.initTokenClient({
          client_id: cfg.clientId,
          scope: 'openid email profile',
          callback: async (tokenResp) => {
            if (!tokenResp?.access_token) return;
            try {
              const res = await api('/auth/google', {
                method: 'POST',
                body: { accessToken: tokenResp.access_token },
              });
              await loginSuccess(res);
              navigate('/profile');
            } catch (err) {
              notify(err.message || 'Google sign-in failed', 'error');
            }
          },
        });
        client.requestAccessToken();
        setBusy(false);
        return;
      }

      // Local dev quick Google sign-in
      const email = window.prompt('Enter your Google email to continue:', 'collector@gmail.com');
      if (!email) {
        setBusy(false);
        return;
      }
      const res = await api('/auth/google', {
        method: 'POST',
        body: { email: email.trim(), name: email.split('@')[0] },
      });
      await loginSuccess(res);
      navigate('/profile');
    } catch (err) {
      notify(err.message || 'Google sign-in failed', 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="container tc-section" style={{ maxWidth: 460 }}>
      <div className="tc-card">
        <div className="tc-eyebrow">{isRegister ? 'New Collector' : 'Welcome Back'}</div>
        <h1 style={{ fontSize: 36, margin: '0 0 20px' }}>
          {isRegister ? 'Create Account' : 'Sign In to TimeCraft'}
        </h1>

        <button
          type="button"
          className="btn-outline"
          style={{ width: '100%', marginBottom: 18, justifyContent: 'center' }}
          onClick={continueWithGoogle}
          disabled={busy}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="#4285F4" d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.4h6.5c-.3 1.5-1.1 2.7-2.4 3.6v3h3.9c2.3-2.1 3.5-5.2 3.5-8.7z" />
            <path fill="#34A853" d="M12 24c3.2 0 6-1.1 8-3l-3.9-3c-1.1.7-2.5 1.2-4.1 1.2-3.1 0-5.8-2.1-6.7-5H1.3v3.1C3.3 21.3 7.3 24 12 24z" />
            <path fill="#FBBC05" d="M5.3 14.2c-.2-.7-.4-1.4-.4-2.2s.1-1.5.4-2.2V6.7H1.3C.5 8.3 0 10.1 0 12s.5 3.7 1.3 5.3l4-3.1z" />
            <path fill="#EA4335" d="M12 4.8c1.8 0 3.4.6 4.6 1.8l3.5-3.5C18 1.1 15.2 0 12 0 7.3 0 3.3 2.7 1.3 6.7l4 3.1c.9-2.9 3.6-5 6.7-5z" />
          </svg>
          Continue with Google
        </button>

        <div style={{ textAlign: 'center', fontSize: 12, color: 'var(--muted)', marginBottom: 18 }}>
          — or use email &amp; password —
        </div>

        <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {isRegister && (
            <div className="tc-grid-2" style={{ gap: 10 }}>
              <input
                className="tc-input"
                placeholder="First Name"
                required
                value={form.firstName}
                onChange={(e) => setForm((f) => ({ ...f, firstName: e.target.value }))}
              />
              <input
                className="tc-input"
                placeholder="Last Name"
                required
                value={form.lastName}
                onChange={(e) => setForm((f) => ({ ...f, lastName: e.target.value }))}
              />
            </div>
          )}
          <input
            type="email"
            className="tc-input"
            placeholder="Email address"
            required
            value={form.email}
            onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
          />
          <input
            type="password"
            className="tc-input"
            placeholder="Password"
            required
            value={form.password}
            onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
          />
          <button type="submit" className="btn-primary" disabled={busy}>
            {busy ? 'Please wait...' : isRegister ? 'Create Account' : 'Sign In'}
          </button>
        </form>

        <div style={{ marginTop: 18, textAlign: 'center', fontSize: 13, color: 'var(--muted)' }}>
          {isRegister ? 'Already have an account? ' : "Don't have an account? "}
          <button
            type="button"
            onClick={() => setIsRegister((v) => !v)}
            style={{ background: 'none', border: 'none', color: 'var(--gold)', fontWeight: 600 }}
          >
            {isRegister ? 'Sign In' : 'Register'}
          </button>
        </div>
      </div>
    </div>
  );
}
