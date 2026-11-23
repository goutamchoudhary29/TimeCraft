import React from 'react';
import { Link } from 'react-router-dom';
import { Eye, Heart, Scale } from 'lucide-react';
import { formatInr } from '../api/client';
import { useStore } from '../context/StoreContext';

export default function WatchCard({ watch }) {
  const { wishlistIds, toggleWishlist, compareItems, toggleCompare, setQuickViewSlug } = useStore();
  const isWished = wishlistIds.includes(watch.id);
  const isCompared = compareItems.some((w) => w.id === watch.id);
  const price = watch.startingPrice ?? watch.basePrice ?? 0;

  return (
    <article className="tc-watch-card">
      <div className="tc-watch-media">
        <Link to={`/watch/${watch.slug}`}>
          <img src={watch.primaryImageUrl || '/images/logo-mark.svg'} alt={watch.name} loading="lazy" />
        </Link>

        <div className="tc-watch-actions">
          <button
            className={`tc-card-icon ${isWished ? 'active' : ''}`}
            onClick={() => toggleWishlist(watch.id)}
            title={isWished ? 'Remove from Wishlist' : 'Save to Wishlist'}
            aria-label="Save to Wishlist"
          >
            <Heart size={15} fill={isWished ? 'currentColor' : 'none'} />
          </button>
          <button
            className={`tc-card-icon ${isCompared ? 'active' : ''}`}
            onClick={() => toggleCompare(watch)}
            title={isCompared ? 'Remove from Compare' : 'Add to Compare'}
            aria-label="Add to Compare"
          >
            <Scale size={15} />
          </button>
        </div>

        <div className="tc-quickview-bar">
          <button
            className="btn-primary"
            style={{ width: '100%', padding: '9px 12px', fontSize: 11 }}
            onClick={() => setQuickViewSlug(watch.slug)}
          >
            <Eye size={14} /> Quick View
          </button>
        </div>
      </div>

      <div className="tc-watch-body">
        <div className="tc-watch-brand">{watch.brandName || 'TimeCraft'}</div>
        <h3 className="tc-watch-title">
          <Link to={`/watch/${watch.slug}`}>{watch.name}</Link>
        </h3>
        <div className="tc-watch-meta">
          {[watch.movementType, watch.caseSizeMm ? `${watch.caseSizeMm}mm` : null, watch.dialColor]
            .filter(Boolean)
            .join(' · ')}
        </div>
        <div className="tc-watch-price">
          <strong className="num" style={{ fontSize: 16 }}>
            {formatInr(price)}
          </strong>
          {watch.inStock === false ? (
            <span style={{ fontSize: 11, color: 'var(--danger)', textTransform: 'uppercase' }}>Sold Out</span>
          ) : (
            <span style={{ fontSize: 11, color: 'var(--success)', textTransform: 'uppercase' }}>In Stock</span>
          )}
        </div>
      </div>
    </article>
  );
}
