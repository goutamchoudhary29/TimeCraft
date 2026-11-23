import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { X } from 'lucide-react';
import { api, formatInr } from '../api/client';
import { useStore } from '../context/StoreContext';

export default function QuickViewModal() {
  const { quickViewSlug, setQuickViewSlug, addToBag } = useStore();
  const [product, setProduct] = useState(null);
  const [selectedVariantId, setSelectedVariantId] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!quickViewSlug) {
      setProduct(null);
      return;
    }
    let active = true;
    setLoading(true);
    api(`/catalog/products/${quickViewSlug}`)
      .then((data) => {
        if (!active) return;
        setProduct(data);
        const firstAvail = (data?.variants || []).find((v) => v.availableQuantity > 0) || data?.variants?.[0];
        setSelectedVariantId(firstAvail?.id || null);
      })
      .catch(() => {
        if (active) setProduct(null);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [quickViewSlug]);

  if (!quickViewSlug) return null;

  const variant = (product?.variants || []).find((v) => v.id === selectedVariantId) || product?.variants?.[0];

  return (
    <div className="tc-backdrop center" onClick={() => setQuickViewSlug(null)}>
      <div className="tc-modal" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <span className="tc-eyebrow" style={{ margin: 0 }}>
            Horological Quick View
          </span>
          <button className="tc-icon-btn" onClick={() => setQuickViewSlug(null)}>
            <X size={20} />
          </button>
        </div>

        {loading || !product ? (
          <p style={{ color: 'var(--muted)', padding: '32px 0' }}>Loading timepiece specifications...</p>
        ) : (
          <div className="tc-grid-2" style={{ alignItems: 'center' }}>
            <div style={{ background: 'var(--stone)', aspectRatio: '4/5', overflow: 'hidden' }}>
              <img
                src={product.images?.[0]?.imageUrl || product.primaryImageUrl || '/images/logo-mark.svg'}
                alt={product.name}
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
            </div>

            <div>
              <div className="tc-watch-brand">{product.brand?.name || product.brandName}</div>
              <h2 className="serif" style={{ fontSize: 34, margin: '0 0 10px' }}>
                {product.name}
              </h2>
              <div className="num" style={{ fontSize: 22, fontWeight: 600, marginBottom: 14 }}>
                {formatInr(variant?.price ?? product.basePrice)}
              </div>
              <p style={{ color: 'var(--muted)', fontSize: 14, marginBottom: 18 }}>
                {product.shortDescription || product.description}
              </p>

              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 10,
                  padding: '12px 0',
                  borderTop: '1px solid var(--border)',
                  borderBottom: '1px solid var(--border)',
                  marginBottom: 18,
                  fontSize: 13,
                }}
              >
                <div>
                  <span style={{ color: 'var(--muted)' }}>Movement:</span>{' '}
                  <strong>{product.movementType || '—'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--muted)' }}>Case Size:</span>{' '}
                  <strong className="num">{product.caseSizeMm ? `${product.caseSizeMm}mm` : '—'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--muted)' }}>Water Resistance:</span>{' '}
                  <strong>{product.waterResistance || '—'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--muted)' }}>Strap:</span>{' '}
                  <strong>{product.strapMaterial || '—'}</strong>
                </div>
              </div>

              {(product.variants || []).length > 1 && (
                <div style={{ marginBottom: 18 }}>
                  <label style={{ display: 'block', fontSize: 12, color: 'var(--muted)', marginBottom: 6 }}>
                    Select Edition / Variant
                  </label>
                  <select
                    className="tc-select"
                    value={selectedVariantId || ''}
                    onChange={(e) => setSelectedVariantId(Number(e.target.value))}
                  >
                    {product.variants.map((v) => (
                      <option key={v.id} value={v.id} disabled={v.availableQuantity <= 0}>
                        {v.variantName} — {formatInr(v.price)} ({v.availableQuantity > 0 ? 'In Stock' : 'Sold Out'})
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <div style={{ display: 'flex', gap: 10 }}>
                <button
                  className="btn-primary"
                  style={{ flex: 1 }}
                  disabled={!variant || variant.availableQuantity <= 0}
                  onClick={() => {
                    addToBag(variant.id, 1);
                    setQuickViewSlug(null);
                  }}
                >
                  Add to Bag
                </button>
                <Link
                  to={`/watch/${product.slug}`}
                  className="btn-outline"
                  onClick={() => setQuickViewSlug(null)}
                >
                  Full Details
                </Link>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
