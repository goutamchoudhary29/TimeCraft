import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Trash2 } from 'lucide-react';
import { api, formatInr } from '../api/client';
import { useStore } from '../context/StoreContext';

export default function WishlistPage() {
  const { user, toggleWishlist } = useStore();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = () => {
    if (!user) {
      setLoading(false);
      return;
    }
    setLoading(true);
    api('/wishlist')
      .then((res) => setItems(res?.items || []))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  };

  useEffect(load, [user]);

  if (!user) {
    return (
      <div className="container tc-section" style={{ maxWidth: 480, textAlign: 'center' }}>
        <div className="tc-card">
          <h2 className="serif" style={{ fontSize: 30 }}>
            Saved Wishlist
          </h2>
          <p style={{ color: 'var(--muted)', marginBottom: 18 }}>Sign in to view your curated wishlist.</p>
          <Link to="/login" className="btn-primary">
            Sign In
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container tc-section">
      <div className="tc-section-head">
        <div>
          <div className="tc-eyebrow" style={{ marginBottom: 6 }}>
            Curated Shortlist
          </div>
          <h1 style={{ margin: 0, fontSize: 42 }}>Your Wishlist</h1>
        </div>
        <Link to="/catalog" className="btn-outline">
          Explore More
        </Link>
      </div>

      {loading ? (
        <p style={{ color: 'var(--muted)' }}>Loading wishlist...</p>
      ) : items.length === 0 ? (
        <div className="tc-card" style={{ textAlign: 'center', padding: 48 }}>
          <h3 className="serif" style={{ fontSize: 26 }}>
            No saved timepieces yet
          </h3>
          <Link to="/catalog" className="btn-primary">
            Browse Catalog
          </Link>
        </div>
      ) : (
        <div className="tc-grid-4">
          {items.map((it) => (
            <div key={it.productId} className="tc-watch-card">
              <div className="tc-watch-media">
                <Link to={`/watch/${it.productSlug}`}>
                  <img src={it.imageUrl || '/images/logo-mark.svg'} alt={it.productName} />
                </Link>
              </div>
              <div className="tc-watch-body">
                <div className="tc-watch-brand">{it.brandName}</div>
                <h3 className="tc-watch-title">
                  <Link to={`/watch/${it.productSlug}`}>{it.productName}</Link>
                </h3>
                <div className="tc-watch-price">
                  <strong className="num">{formatInr(it.price)}</strong>
                  <button
                    className="tc-icon-btn"
                    onClick={async () => {
                      await toggleWishlist(it.productId);
                      load();
                    }}
                    title="Remove"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
