import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, formatInr } from '../api/client';
import { useStore } from '../context/StoreContext';

export default function ProfilePage() {
  const { user, setUser, logout, notify } = useStore();
  const navigate = useNavigate();
  const [orders, setOrders] = useState([]);
  const [profile, setProfile] = useState({
    firstName: user?.firstName || '',
    lastName: user?.lastName || '',
    phone: user?.phone || '',
  });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!user) return;
    api('/orders?size=10')
      .then((res) => setOrders(res?.content || []))
      .catch(() => {});
    api('/customers/me')
      .then((me) => {
        if (me) {
          setProfile({
            firstName: me.firstName || '',
            lastName: me.lastName || '',
            phone: me.phone || '',
          });
        }
      })
      .catch(() => {});
  }, [user]);

  if (!user) {
    return (
      <div className="container tc-section" style={{ maxWidth: 480, textAlign: 'center' }}>
        <div className="tc-card">
          <h2 className="serif" style={{ fontSize: 30 }}>
            Collector Account
          </h2>
          <p style={{ color: 'var(--muted)', marginBottom: 18 }}>Sign in to view your orders and profile.</p>
          <Link to="/login" className="btn-primary">
            Sign In
          </Link>
        </div>
      </div>
    );
  }

  const saveProfile = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      const updated = await api('/customers/me', { method: 'PUT', body: profile });
      setUser((prev) => ({ ...prev, ...updated }));
      notify('Profile updated');
    } catch (err) {
      notify(err.message || 'Could not update profile', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="container tc-section">
      <div className="tc-section-head">
        <div>
          <div className="tc-eyebrow" style={{ marginBottom: 6 }}>
            Collector Atelier
          </div>
          <h1 style={{ margin: 0, fontSize: 42 }}>
            {user.firstName} {user.lastName}
          </h1>
          <div style={{ fontSize: 13, color: 'var(--muted)' }}>{user.email}</div>
        </div>
        <button
          className="btn-outline"
          onClick={async () => {
            await logout();
            navigate('/');
          }}
        >
          Sign Out
        </button>
      </div>

      <div className="tc-grid-2" style={{ alignItems: 'stretch' }}>
        <div className="tc-card">
          <h2 className="serif" style={{ fontSize: 26, marginTop: 0 }}>
            Personal Details
          </h2>
          <form onSubmit={saveProfile} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div className="tc-grid-2" style={{ gap: 10 }}>
              <div>
                <label style={{ fontSize: 12, color: 'var(--muted)' }}>First Name</label>
                <input
                  className="tc-input"
                  value={profile.firstName}
                  onChange={(e) => setProfile((p) => ({ ...p, firstName: e.target.value }))}
                />
              </div>
              <div>
                <label style={{ fontSize: 12, color: 'var(--muted)' }}>Last Name</label>
                <input
                  className="tc-input"
                  value={profile.lastName}
                  onChange={(e) => setProfile((p) => ({ ...p, lastName: e.target.value }))}
                />
              </div>
            </div>
            <div>
              <label style={{ fontSize: 12, color: 'var(--muted)' }}>Phone</label>
              <input
                className="tc-input"
                value={profile.phone}
                onChange={(e) => setProfile((p) => ({ ...p, phone: e.target.value }))}
              />
            </div>
            <button type="submit" className="btn-primary" disabled={saving} style={{ alignSelf: 'flex-start' }}>
              {saving ? 'Saving...' : 'Save Changes'}
            </button>
          </form>
        </div>

        <div className="tc-card">
          <h2 className="serif" style={{ fontSize: 26, marginTop: 0 }}>
            Order History
          </h2>
          {orders.length === 0 ? (
            <p style={{ color: 'var(--muted)' }}>You have not placed any orders yet.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {orders.map((o) => (
                <div
                  key={o.id}
                  style={{
                    paddingBottom: 12,
                    borderBottom: '1px solid var(--stone)',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <div>
                    <strong className="num">{o.orderNumber}</strong>
                    <div style={{ fontSize: 12, color: 'var(--muted)' }}>
                      Status: {o.status} · Payment: {o.paymentStatus}
                    </div>
                  </div>
                  <strong className="num">{formatInr(o.grandTotal)}</strong>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
