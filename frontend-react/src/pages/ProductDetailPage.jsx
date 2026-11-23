import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Heart, Scale, ShieldCheck, ShoppingBag } from 'lucide-react';
import { api, formatInr } from '../api/client';
import { useStore } from '../context/StoreContext';

export default function ProductDetailPage() {
  const { slug } = useParams();
  const { addToBag, wishlistIds, toggleWishlist, compareItems, toggleCompare } = useStore();
  const [product, setProduct] = useState(null);
  const [selectedVariantId, setSelectedVariantId] = useState(null);
  const [activeImage, setActiveImage] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    api(`/catalog/products/${slug}`)
      .then((data) => {
        if (!active) return;
        setProduct(data);
        const firstAvail = (data?.variants || []).find((v) => v.availableQuantity > 0) || data?.variants?.[0];
        setSelectedVariantId(firstAvail?.id || null);
        setActiveImage(0);
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
  }, [slug]);

  if (loading) {
    return (
      <div className="container tc-section">
        <p style={{ color: 'var(--muted)' }}>Loading timepiece dossier...</p>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="container tc-section">
        <h2 className="serif">Timepiece not found</h2>
        <Link to="/catalog" className="btn-outline">
          Back to Catalog
        </Link>
      </div>
    );
  }

  const images = product.images?.length
    ? product.images.map((i) => i.imageUrl)
    : [product.primaryImageUrl || '/images/logo-mark.svg'];
  const variant = (product.variants || []).find((v) => v.id === selectedVariantId) || product.variants?.[0];
  const isWished = wishlistIds.includes(product.id);
  const isCompared = compareItems.some((w) => w.id === product.id);

  return (
    <div className="container tc-section">
      <div style={{ marginBottom: 20, fontSize: 13, color: 'var(--muted)' }}>
        <Link to="/">Home</Link> / <Link to="/catalog">Catalog</Link> / <span>{product.name}</span>
      </div>

      <div className="tc-grid-2" style={{ gap: 40, alignItems: 'start' }}>
        <div>
          <div style={{ background: 'var(--stone)', aspectRatio: '4/5', overflow: 'hidden', marginBottom: 12 }}>
            <img src={images[activeImage]} alt={product.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          </div>
          {images.length > 1 && (
            <div style={{ display: 'flex', gap: 10 }}>
              {images.map((src, i) => (
                <button
                  key={src}
                  onClick={() => setActiveImage(i)}
                  style={{
                    width: 72,
                    height: 72,
                    padding: 0,
                    border: i === activeImage ? '2px solid var(--gold)' : '1px solid var(--border)',
                    background: 'var(--stone)',
                  }}
                >
                  <img src={src} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                </button>
              ))}
            </div>
          )}
        </div>

        <div>
          <div className="tc-watch-brand">{product.brand?.name || product.brandName}</div>
          <h1 style={{ fontSize: 44, margin: '4px 0 12px', lineHeight: 1.1 }}>{product.name}</h1>
          <div className="num" style={{ fontSize: 28, fontWeight: 600, marginBottom: 18 }}>
            {formatInr(variant?.price ?? product.basePrice)}
          </div>
          <p style={{ color: 'var(--muted)', marginBottom: 24 }}>{product.description || product.shortDescription}</p>

          {(product.variants || []).length > 0 && (
            <div style={{ marginBottom: 22 }}>
              <label style={{ display: 'block', fontSize: 12, color: 'var(--muted)', marginBottom: 8 }}>
                Configuration / Edition
              </label>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                {product.variants.map((v) => (
                  <button
                    key={v.id}
                    onClick={() => setSelectedVariantId(v.id)}
                    className={v.id === variant?.id ? 'btn-primary' : 'btn-outline'}
                    style={{ padding: '9px 14px', fontSize: 12 }}
                  >
                    {v.variantName} · {formatInr(v.price)}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div style={{ display: 'flex', gap: 10, marginBottom: 24, flexWrap: 'wrap' }}>
            <button
              className="btn-primary"
              style={{ flex: 1, minWidth: 200 }}
              disabled={!variant || variant.availableQuantity <= 0}
              onClick={() => addToBag(variant.id, 1)}
            >
              <ShoppingBag size={16} />{' '}
              {variant && variant.availableQuantity > 0 ? 'Add to Bag' : 'Out of Stock'}
            </button>
            <button
              className="btn-outline"
              onClick={() => toggleWishlist(product.id)}
              title="Wishlist"
            >
              <Heart size={16} fill={isWished ? 'currentColor' : 'none'} />
            </button>
            <button
              className="btn-outline"
              onClick={() =>
                toggleCompare({
                  ...product,
                  brandName: product.brand?.name || product.brandName,
                  primaryImageUrl: images[0],
                  startingPrice: variant?.price ?? product.basePrice,
                })
              }
              title="Compare"
            >
              <Scale size={16} /> {isCompared ? 'Compared' : 'Compare'}
            </button>
          </div>

          <div className="tc-card" style={{ padding: 18 }}>
            <div className="tc-eyebrow" style={{ marginBottom: 10 }}>
              Technical Specifications
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, fontSize: 14 }}>
              <div>
                <span style={{ color: 'var(--muted)' }}>SKU:</span>{' '}
                <strong className="num">{variant?.sku || product.sku || '—'}</strong>
              </div>
              <div>
                <span style={{ color: 'var(--muted)' }}>Movement:</span>{' '}
                <strong>{product.movementType || '—'}</strong>
              </div>
              <div>
                <span style={{ color: 'var(--muted)' }}>Case Size:</span>{' '}
                <strong className="num">{product.caseSizeMm ? `${product.caseSizeMm} mm` : '—'}</strong>
              </div>
              <div>
                <span style={{ color: 'var(--muted)' }}>Water Resistance:</span>{' '}
                <strong>{product.waterResistance || '—'}</strong>
              </div>
              <div>
                <span style={{ color: 'var(--muted)' }}>Dial Color:</span>{' '}
                <strong>{product.dialColor || '—'}</strong>
              </div>
              <div>
                <span style={{ color: 'var(--muted)' }}>Strap:</span>{' '}
                <strong>{product.strapMaterial || '—'}</strong>
              </div>
            </div>
            <div
              style={{
                marginTop: 14,
                paddingTop: 12,
                borderTop: '1px solid var(--stone)',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                fontSize: 13,
                color: 'var(--muted)',
              }}
            >
              <ShieldCheck size={16} color="var(--gold)" />
              Authenticity certificate &amp; insured delivery included.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
