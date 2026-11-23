import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Minus, Plus, ShieldCheck, Trash2, X } from 'lucide-react';
import { formatInr, FREE_SHIPPING_THRESHOLD } from '../api/client';
import { useStore } from '../context/StoreContext';

export default function BagDrawer() {
  const { bagOpen, setBagOpen, cart, updateBagQty } = useStore();
  const navigate = useNavigate();

  if (!bagOpen) return null;

  const items = cart?.items || [];
  const subtotal = Number(cart?.subtotal || 0);
  const remaining = Math.max(0, FREE_SHIPPING_THRESHOLD - subtotal);
  const pct = Math.min(100, Math.round((subtotal / FREE_SHIPPING_THRESHOLD) * 100));

  return (
    <div className="tc-backdrop" onClick={() => setBagOpen(false)}>
      <aside className="tc-drawer" onClick={(e) => e.stopPropagation()} aria-label="Shopping Bag">
        <div
          style={{
            padding: '20px 24px',
            borderBottom: '1px solid var(--border)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div>
            <h3 className="serif" style={{ margin: 0, fontSize: 26 }}>
              Your Bag
            </h3>
            <span style={{ fontSize: 12, color: 'var(--muted)' }}>
              {items.reduce((s, i) => s + i.quantity, 0)} timepiece(s)
            </span>
          </div>
          <button className="tc-icon-btn" onClick={() => setBagOpen(false)} aria-label="Close bag">
            <X size={20} />
          </button>
        </div>

        <div style={{ padding: '14px 24px', background: 'var(--bg)', borderBottom: '1px solid var(--border)' }}>
          <div style={{ fontSize: 12, display: 'flex', alignItems: 'center', gap: 6 }}>
            <ShieldCheck size={15} color="var(--gold)" />
            {remaining === 0 ? (
              <span>Complimentary insured courier delivery unlocked.</span>
            ) : (
              <span>
                Add <strong className="num">{formatInr(remaining)}</strong> more for free insured shipping.
              </span>
            )}
          </div>
          <div className="tc-progress-track">
            <div className="tc-progress-fill" style={{ width: `${pct}%` }} />
          </div>
        </div>

        <div style={{ flex: 1, overflowY: 'auto', padding: '16px 24px' }}>
          {items.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '56px 12px', color: 'var(--muted)' }}>
              <p className="serif" style={{ fontSize: 22, color: 'var(--ink)', marginBottom: 8 }}>
                Your bag is empty
              </p>
              <p style={{ fontSize: 13, marginBottom: 20 }}>
                Explore Swiss maisons and iconic Indian horology from Titan &amp; Fastrack.
              </p>
              <button
                className="btn-primary"
                onClick={() => {
                  setBagOpen(false);
                  navigate('/catalog');
                }}
              >
                Browse Collection
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {items.map((item) => (
                <div
                  key={item.id}
                  style={{
                    display: 'flex',
                    gap: 14,
                    paddingBottom: 16,
                    borderBottom: '1px solid var(--stone)',
                  }}
                >
                  <img
                    src={item.imageUrl || '/images/logo-mark.svg'}
                    alt={item.productName}
                    style={{ width: 72, height: 86, objectFit: 'cover', background: 'var(--stone)' }}
                  />
                  <div style={{ flex: 1 }}>
                    <div className="serif" style={{ fontSize: 18 }}>
                      {item.productName}
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 8 }}>
                      {item.variantName || item.sku}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <div style={{ display: 'inline-flex', alignItems: 'center', border: '1px solid var(--border)' }}>
                        <button
                          className="tc-icon-btn"
                          style={{ padding: '4px 8px' }}
                          onClick={() => updateBagQty(item.id, item.quantity - 1)}
                          aria-label="Decrease quantity"
                        >
                          <Minus size={13} />
                        </button>
                        <span className="num" style={{ padding: '0 10px', fontSize: 13 }}>
                          {item.quantity}
                        </span>
                        <button
                          className="tc-icon-btn"
                          style={{ padding: '4px 8px' }}
                          onClick={() => updateBagQty(item.id, item.quantity + 1)}
                          aria-label="Increase quantity"
                        >
                          <Plus size={13} />
                        </button>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <strong className="num">{formatInr(item.lineTotal || item.unitPrice * item.quantity)}</strong>
                        <button
                          className="tc-icon-btn"
                          style={{ padding: 4, color: 'var(--muted)' }}
                          onClick={() => updateBagQty(item.id, 0)}
                          aria-label="Remove item"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {items.length > 0 && (
          <div style={{ padding: '20px 24px', borderTop: '1px solid var(--border)', background: 'var(--bg)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
              <span style={{ color: 'var(--muted)' }}>Estimated Subtotal</span>
              <strong className="num" style={{ fontSize: 18 }}>
                {formatInr(subtotal)}
              </strong>
            </div>
            <button
              className="btn-primary"
              style={{ width: '100%' }}
              onClick={() => {
                setBagOpen(false);
                navigate('/checkout');
              }}
            >
              Proceed to Checkout
            </button>
          </div>
        )}
      </aside>
    </div>
  );
}
