import React, { useEffect, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { Heart, Menu, Scale, Search, ShoppingBag, User, X } from 'lucide-react';
import { api, formatInr } from '../api/client';
import { useStore } from '../context/StoreContext';

export default function Header() {
  const {
    user,
    cart,
    wishlistIds,
    compareItems,
    setBagOpen,
    searchOpen,
    setSearchOpen,
    setCompareOpen,
  } = useStore();
  const [mobileMenu, setMobileMenu] = useState(false);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const navigate = useNavigate();

  const bagCount = (cart?.items || []).reduce((sum, it) => sum + (it.quantity || 0), 0);

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') {
        setSearchOpen(false);
        setMobileMenu(false);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [setSearchOpen]);

  useEffect(() => {
    if (!searchOpen) return;
    const trimmed = query.trim();
    if (!trimmed) {
      setResults([]);
      return;
    }
    const timer = setTimeout(async () => {
      setSearching(true);
      try {
        const res = await api(`/catalog/products?q=${encodeURIComponent(trimmed)}&size=8`);
        setResults(res?.content || []);
      } catch {
        setResults([]);
      } finally {
        setSearching(false);
      }
    }, 220);
    return () => clearTimeout(timer);
  }, [query, searchOpen]);

  return (
    <>
      <header className="tc-header">
        <div className="container tc-header-inner">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <button
              className="tc-icon-btn tc-mobile-menu-btn"
              onClick={() => setMobileMenu((v) => !v)}
              aria-label="Toggle navigation"
            >
              {mobileMenu ? <X size={18} /> : <Menu size={18} />}
            </button>
            <nav className="tc-nav-left" aria-label="Primary">
              <NavLink to="/" end className="tc-nav-link">
                Home
              </NavLink>
              <NavLink to="/catalog" className="tc-nav-link">
                Timepieces
              </NavLink>
              <NavLink to="/about" className="tc-nav-link">
                Maison
              </NavLink>
              <a href="/admin/dashboard" className="tc-nav-link">
                Admin
              </a>
            </nav>
          </div>

          <Link to="/" className="tc-brand">
            <img src="/images/favicon.svg" alt="TimeCraft" />
            <span>TimeCraft</span>
          </Link>

          <div className="tc-nav-right">
            <button
              className="tc-icon-btn"
              onClick={() => setSearchOpen(true)}
              title="Search watches"
              aria-label="Search watches"
            >
              <Search size={17} />
            </button>

            <button
              className="tc-icon-btn"
              onClick={() => setCompareOpen(true)}
              title="Compare watches"
              aria-label="Compare watches"
            >
              <Scale size={17} />
              {compareItems.length > 0 && <span className="tc-badge">{compareItems.length}</span>}
            </button>

            <Link to="/wishlist" className="tc-icon-btn" title="Wishlist" aria-label="Wishlist">
              <Heart size={17} />
              {wishlistIds.length > 0 && <span className="tc-badge">{wishlistIds.length}</span>}
            </Link>

            <Link
              to={user ? '/profile' : '/login'}
              className="tc-icon-btn"
              title={user ? user.firstName : 'Sign In'}
            >
              <User size={17} />
            </Link>

            <button
              className="tc-icon-btn"
              onClick={() => setBagOpen(true)}
              title="Shopping Bag"
              aria-label="Shopping Bag"
            >
              <ShoppingBag size={17} />
              <span className="tc-badge">{bagCount}</span>
            </button>
          </div>
        </div>
      </header>

      {mobileMenu && (
        <div
          style={{
            background: '#fff',
            borderBottom: '1px solid var(--border)',
            padding: '16px 24px',
            display: 'flex',
            flexDirection: 'column',
            gap: 12,
          }}
        >
          <Link to="/" onClick={() => setMobileMenu(false)}>
            Home
          </Link>
          <Link to="/catalog" onClick={() => setMobileMenu(false)}>
            Timepieces
          </Link>
          <Link to="/about" onClick={() => setMobileMenu(false)}>
            Maison
          </Link>
          <Link to="/wishlist" onClick={() => setMobileMenu(false)}>
            Wishlist ({wishlistIds.length})
          </Link>
          <a href="/admin/dashboard">Admin Dashboard</a>
        </div>
      )}

      {searchOpen && (
        <div className="tc-search-overlay" onClick={() => setSearchOpen(false)}>
          <div className="container" style={{ maxWidth: 820 }} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <span className="tc-eyebrow" style={{ margin: 0 }}>
                Instant Horological Search
              </span>
              <button className="tc-icon-btn" onClick={() => setSearchOpen(false)}>
                <X size={20} /> ESC
              </button>
            </div>
            <input
              autoFocus
              type="text"
              className="tc-input"
              style={{ fontSize: 20, padding: '16px 18px', fontFamily: 'var(--serif)' }}
              placeholder="Search by watch name, Maison (Titan, Fastrack, Omega, Tissot...), or SKU..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />

            <div style={{ marginTop: 24 }}>
              {searching && <p style={{ color: 'var(--muted)' }}>Searching collection...</p>}
              {!searching && query.trim() && results.length === 0 && (
                <p style={{ color: 'var(--muted)' }}>No timepieces matched "{query}".</p>
              )}
              <div className="tc-grid-2">
                {results.map((w) => (
                  <div
                    key={w.id}
                    className="tc-card"
                    style={{ padding: 14, display: 'flex', gap: 14, alignItems: 'center', cursor: 'pointer' }}
                    onClick={() => {
                      setSearchOpen(false);
                      navigate(`/watch/${w.slug}`);
                    }}
                  >
                    <img
                      src={w.primaryImageUrl || '/images/logo-mark.svg'}
                      alt={w.name}
                      style={{ width: 64, height: 64, objectFit: 'cover', background: 'var(--stone)' }}
                    />
                    <div style={{ flex: 1 }}>
                      <div className="tc-watch-brand">{w.brandName}</div>
                      <div className="serif" style={{ fontSize: 18 }}>
                        {w.name}
                      </div>
                      <div className="num" style={{ fontSize: 13, color: 'var(--muted)' }}>
                        {formatInr(w.startingPrice || w.basePrice)}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
