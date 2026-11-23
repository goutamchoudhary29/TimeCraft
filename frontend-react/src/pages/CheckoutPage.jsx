import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { api, formatInr } from '../api/client';
import { useStore } from '../context/StoreContext';

export default function CheckoutPage() {
  const { user, cart, refreshCart, notify } = useStore();
  const navigate = useNavigate();
  const [addresses, setAddresses] = useState([]);
  const [selectedAddressId, setSelectedAddressId] = useState('');
  const [couponCode, setCouponCode] = useState('');
  const [placing, setPlacing] = useState(false);

  const [newAddr, setNewAddr] = useState({
    fullName: user ? `${user.firstName || ''} ${user.lastName || ''}`.trim() : '',
    phone: '',
    line1: '',
    city: '',
    state: '',
    postalCode: '',
    country: 'India',
  });

  useEffect(() => {
    if (!user) return;
    api('/addresses')
      .then((list) => {
        const arr = Array.isArray(list) ? list : [];
        setAddresses(arr);
        if (arr.length > 0) setSelectedAddressId(String(arr[0].id));
      })
      .catch(() => {});
  }, [user]);

  if (!user) {
    return (
      <div className="container tc-section" style={{ maxWidth: 540, textAlign: 'center' }}>
        <div className="tc-card">
          <h2 className="serif" style={{ fontSize: 32 }}>
            Sign in to Complete Checkout
          </h2>
          <p style={{ color: 'var(--muted)', marginBottom: 20 }}>
            Please sign in or continue with Google so your order, warranty, and Razorpay receipt are linked to your account.
          </p>
          <Link to="/login" className="btn-primary">
            Sign In / Continue with Google
          </Link>
        </div>
      </div>
    );
  }

  const items = cart?.items || [];
  if (items.length === 0) {
    return (
      <div className="container tc-section" style={{ maxWidth: 540, textAlign: 'center' }}>
        <div className="tc-card">
          <h2 className="serif" style={{ fontSize: 32 }}>
            Your bag is empty
          </h2>
          <Link to="/catalog" className="btn-primary">
            Explore Watches
          </Link>
        </div>
      </div>
    );
  }

  const applyCoupon = async () => {
    if (!couponCode.trim()) return;
    try {
      await api('/cart/coupon', { method: 'POST', body: { code: couponCode.trim() } });
      await refreshCart();
      notify('Coupon applied');
    } catch (err) {
      notify(err.message || 'Invalid coupon', 'error');
    }
  };

  const handleCheckout = async (e) => {
    e.preventDefault();
    setPlacing(true);
    try {
      let addressId = selectedAddressId ? Number(selectedAddressId) : null;
      if (!addressId) {
        const created = await api('/addresses', { method: 'POST', body: newAddr });
        addressId = created.id;
      }

      const order = await api('/orders', {
        method: 'POST',
        body: { shippingAddressId: addressId, billingAddressId: addressId },
      });

      const paymentInit = await api(`/payments/orders/${order.id}/initiate`, { method: 'POST' });

      if (paymentInit.sandboxMode || !window.Razorpay) {
        await api('/payments/verify', {
          method: 'POST',
          body: {
            orderId: order.id,
            razorpayOrderId: paymentInit.razorpayOrderId,
            razorpayPaymentId: 'pay_sandbox_' + Date.now(),
            razorpaySignature: paymentInit.sandboxSignature || 'sandbox',
          },
        });
        await refreshCart();
        notify(`Order ${order.orderNumber} confirmed!`);
        navigate('/profile');
        return;
      }

      const rzp = new window.Razorpay({
        key: paymentInit.keyId,
        amount: paymentInit.amountPaise,
        currency: paymentInit.currency || 'INR',
        name: 'TimeCraft Horology',
        description: `Order ${order.orderNumber}`,
        order_id: paymentInit.razorpayOrderId,
        prefill: {
          name: `${user.firstName || ''} ${user.lastName || ''}`.trim(),
          email: user.email,
        },
        theme: { color: '#1A1A1A' },
        handler: async (response) => {
          try {
            await api('/payments/verify', {
              method: 'POST',
              body: {
                orderId: order.id,
                razorpayOrderId: response.razorpay_order_id,
                razorpayPaymentId: response.razorpay_payment_id,
                razorpaySignature: response.razorpay_signature,
              },
            });
            await refreshCart();
            notify(`Payment verified! Order ${order.orderNumber} confirmed.`);
            navigate('/profile');
          } catch (err) {
            notify(err.message || 'Payment verification failed', 'error');
          }
        },
      });
      rzp.open();
    } catch (err) {
      notify(err.message || 'Checkout failed', 'error');
    } finally {
      setPlacing(false);
    }
  };

  return (
    <div className="container tc-section">
      <h1 style={{ fontSize: 42, marginBottom: 24 }}>Secure Checkout</h1>
      <form onSubmit={handleCheckout} className="tc-grid-2" style={{ gap: 32, alignItems: 'start' }}>
        <div className="tc-card" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <h3 className="serif" style={{ margin: 0, fontSize: 24 }}>
            Shipping Address
          </h3>

          {addresses.length > 0 && (
            <div>
              <label style={{ fontSize: 12, color: 'var(--muted)' }}>Saved Addresses</label>
              <select
                className="tc-select"
                value={selectedAddressId}
                onChange={(e) => setSelectedAddressId(e.target.value)}
              >
                {addresses.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.fullName} — {a.line1}, {a.city} ({a.postalCode})
                  </option>
                ))}
                <option value="">+ Add a new address</option>
              </select>
            </div>
          )}

          {!selectedAddressId && (
            <>
              <input
                className="tc-input"
                placeholder="Full Name"
                required
                value={newAddr.fullName}
                onChange={(e) => setNewAddr((a) => ({ ...a, fullName: e.target.value }))}
              />
              <input
                className="tc-input"
                placeholder="Phone (+91...)"
                required
                value={newAddr.phone}
                onChange={(e) => setNewAddr((a) => ({ ...a, phone: e.target.value }))}
              />
              <input
                className="tc-input"
                placeholder="Address Line 1"
                required
                value={newAddr.line1}
                onChange={(e) => setNewAddr((a) => ({ ...a, line1: e.target.value }))}
              />
              <div className="tc-grid-2" style={{ gap: 10 }}>
                <input
                  className="tc-input"
                  placeholder="City"
                  required
                  value={newAddr.city}
                  onChange={(e) => setNewAddr((a) => ({ ...a, city: e.target.value }))}
                />
                <input
                  className="tc-input"
                  placeholder="State"
                  required
                  value={newAddr.state}
                  onChange={(e) => setNewAddr((a) => ({ ...a, state: e.target.value }))}
                />
              </div>
              <input
                className="tc-input"
                placeholder="Postal Code"
                required
                value={newAddr.postalCode}
                onChange={(e) => setNewAddr((a) => ({ ...a, postalCode: e.target.value }))}
              />
            </>
          )}
        </div>

        <div className="tc-card">
          <h3 className="serif" style={{ margin: '0 0 16px', fontSize: 24 }}>
            Order Summary
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 16 }}>
            {items.map((it) => (
              <div key={it.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
                <span>
                  {it.productName} × {it.quantity}
                </span>
                <strong className="num">{formatInr(it.lineTotal || it.unitPrice * it.quantity)}</strong>
              </div>
            ))}
          </div>

          <div style={{ display: 'flex', gap: 8, marginBottom: 18 }}>
            <input
              className="tc-input"
              placeholder="Coupon code (e.g. WELCOME10)"
              value={couponCode}
              onChange={(e) => setCouponCode(e.target.value)}
            />
            <button type="button" className="btn-outline" onClick={applyCoupon}>
              Apply
            </button>
          </div>

          <div
            style={{
              borderTop: '1px solid var(--border)',
              paddingTop: 14,
              display: 'flex',
              justifyContent: 'space-between',
              fontSize: 18,
              marginBottom: 18,
            }}
          >
            <span>Total</span>
            <strong className="num">{formatInr(cart?.grandTotal ?? cart?.subtotal ?? 0)}</strong>
          </div>

          <button type="submit" className="btn-primary" style={{ width: '100%' }} disabled={placing}>
            {placing ? 'Opening Razorpay...' : 'Pay Securely with Razorpay'}
          </button>

          <div style={{ marginTop: 12, fontSize: 12, color: 'var(--muted)', display: 'flex', gap: 6, alignItems: 'center' }}>
            <ShieldCheck size={15} color="var(--gold)" />
            256-bit encrypted Razorpay checkout · UPI, Cards, NetBanking
          </div>
        </div>
      </form>
    </div>
  );
}
