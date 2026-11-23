import React from 'react';
import { Link } from 'react-router-dom';
import { Scale, X } from 'lucide-react';
import { formatInr } from '../api/client';
import { useStore } from '../context/StoreContext';

export default function CompareDockAndModal() {
  const { compareItems, toggleCompare, clearCompare, compareOpen, setCompareOpen } = useStore();

  return (
    <>
      {compareItems.length > 0 && !compareOpen && (
        <div className="tc-compare-dock">
          <Scale size={16} color="var(--gold)" />
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {compareItems.map((w) => (
              <div key={w.id} style={{ position: 'relative' }}>
                <img
                  src={w.primaryImageUrl || '/images/logo-mark.svg'}
                  alt={w.name}
                  title={w.name}
                  style={{
                    width: 36,
                    height: 36,
                    objectFit: 'cover',
                    borderRadius: 2,
                    border: '1px solid rgba(255,255,255,0.25)',
                  }}
                />
                <button
                  onClick={() => toggleCompare(w)}
                  style={{
                    position: 'absolute',
                    top: -6,
                    right: -6,
                    width: 16,
                    height: 16,
                    borderRadius: '50%',
                    border: 'none',
                    background: 'var(--gold)',
                    color: '#fff',
                    fontSize: 10,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  ×
                </button>
              </div>
            ))}
          </div>
          <button className="btn-gold" style={{ padding: '7px 14px', fontSize: 11 }} onClick={() => setCompareOpen(true)}>
            Compare ({compareItems.length})
          </button>
          <button
            onClick={clearCompare}
            style={{ background: 'transparent', border: 'none', color: 'rgba(255,255,255,0.7)', fontSize: 12 }}
          >
            Clear
          </button>
        </div>
      )}

      {compareOpen && (
        <div className="tc-backdrop center" onClick={() => setCompareOpen(false)}>
          <div className="tc-modal" style={{ maxWidth: 980 }} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <h2 className="serif" style={{ margin: 0, fontSize: 30 }}>
                Side-by-Side Timepiece Comparison
              </h2>
              <button className="tc-icon-btn" onClick={() => setCompareOpen(false)}>
                <X size={20} />
              </button>
            </div>

            {compareItems.length === 0 ? (
              <p style={{ color: 'var(--muted)' }}>
                Select watches using the Compare icon on any watch card to view specifications side by side.
              </p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
                  <thead>
                    <tr>
                      <th style={{ textAlign: 'left', padding: 10, borderBottom: '1px solid var(--border)' }}>Spec</th>
                      {compareItems.map((w) => (
                        <th key={w.id} style={{ textAlign: 'left', padding: 10, borderBottom: '1px solid var(--border)' }}>
                          <img
                            src={w.primaryImageUrl || '/images/logo-mark.svg'}
                            alt={w.name}
                            style={{ width: 90, height: 105, objectFit: 'cover', marginBottom: 8 }}
                          />
                          <div className="tc-watch-brand">{w.brandName}</div>
                          <Link
                            to={`/watch/${w.slug}`}
                            onClick={() => setCompareOpen(false)}
                            className="serif"
                            style={{ fontSize: 19 }}
                          >
                            {w.name}
                          </Link>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {[
                      ['Price (INR)', (w) => formatInr(w.startingPrice ?? w.basePrice)],
                      ['Movement', (w) => w.movementType || '—'],
                      ['Case Diameter', (w) => (w.caseSizeMm ? `${w.caseSizeMm} mm` : '—')],
                      ['Dial Color', (w) => w.dialColor || '—'],
                      ['Strap Material', (w) => w.strapMaterial || '—'],
                      ['Water Resistance', (w) => w.waterResistance || '—'],
                    ].map(([label, getter]) => (
                      <tr key={label}>
                        <td style={{ padding: 10, borderBottom: '1px solid var(--stone)', color: 'var(--muted)' }}>
                          {label}
                        </td>
                        {compareItems.map((w) => (
                          <td key={w.id} className="num" style={{ padding: 10, borderBottom: '1px solid var(--stone)' }}>
                            {getter(w)}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}
