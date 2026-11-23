import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Award, ShieldCheck, Truck } from 'lucide-react';
import { api } from '../api/client';
import WatchCard from '../components/WatchCard';

export default function HomePage() {
  const [brands, setBrands] = useState([]);
  const [featured, setFeatured] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      api('/catalog/brands').catch(() => []),
      api('/catalog/products?featured=true&size=8').catch(() => ({ content: [] })),
    ])
      .then(([b, p]) => {
        setBrands(Array.isArray(b) ? b : []);
        setFeatured(p?.content || []);
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <section className="tc-hero">
        <div className="container tc-hero-grid">
          <div>
            <div className="tc-eyebrow">Fine Horology · Geneva &amp; Bengaluru</div>
            <h1>Timepieces Crafted for Generations.</h1>
            <p>
              Discover authenticated Swiss automatic chronographs alongside India’s finest horological creations from Titan and Fastrack — curated, inspected, and delivered with insured white-glove courier.
            </p>
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <Link to="/catalog" className="btn-gold">
                Explore Collection <ArrowRight size={15} />
              </Link>
              <Link
                to="/about"
                className="btn-outline"
                style={{ color: '#FAF9F6', borderColor: 'rgba(250,249,246,0.35)' }}
              >
                Our Maison
              </Link>
            </div>
          </div>
          <div
            style={{
              background: 'rgba(255,255,255,0.04)',
              border: '1px solid rgba(176,141,87,0.3)',
              padding: 28,
              borderRadius: 2,
            }}
          >
            <div className="tc-eyebrow">Collector Highlights</div>
            <div style={{ display: 'grid', gap: 18 }}>
              <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                <ShieldCheck size={20} color="var(--gold)" />
                <div>
                  <strong style={{ display: 'block', marginBottom: 2 }}>100% Authenticated Provenance</strong>
                  <span style={{ fontSize: 13, color: 'rgba(250,249,246,0.7)' }}>
                    Every calibre is verified by master horologists before dispatch.
                  </span>
                </div>
              </div>
              <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                <Truck size={20} color="var(--gold)" />
                <div>
                  <strong style={{ display: 'block', marginBottom: 2 }}>Free Insured Delivery Above ₹10,000</strong>
                  <span style={{ fontSize: 13, color: 'rgba(250,249,246,0.7)' }}>
                    Tamper-evident presentation boxes shipped across India.
                  </span>
                </div>
              </div>
              <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                <Award size={20} color="var(--gold)" />
                <div>
                  <strong style={{ display: 'block', marginBottom: 2 }}>Swiss &amp; Indian Maisons</strong>
                  <span style={{ fontSize: 13, color: 'rgba(250,249,246,0.7)' }}>
                    Featuring Titan, Fastrack, Omega, Tissot, Seiko, and TAG Heuer.
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {brands.length > 0 && (
        <section className="tc-section" style={{ paddingBottom: 20 }}>
          <div className="container">
            <div className="tc-section-head">
              <div>
                <div className="tc-eyebrow" style={{ marginBottom: 6 }}>
                  Authorized Houses
                </div>
                <h2>Explore by Maison</h2>
              </div>
              <Link to="/catalog" className="tc-nav-link">
                View All →
              </Link>
            </div>
            <div className="tc-maisons-strip">
              {brands.map((b) => (
                <Link key={b.id} to={`/catalog?brandId=${b.id}`} className="tc-maison-tile">
                  {b.logoUrl ? (
                    <img src={b.logoUrl} alt={b.name} />
                  ) : (
                    <span className="serif" style={{ fontSize: 20 }}>
                      {b.name}
                    </span>
                  )}
                  <span style={{ fontSize: 12, color: 'var(--muted)' }}>{b.name}</span>
                </Link>
              ))}
            </div>
          </div>
        </section>
      )}

      <section className="tc-section">
        <div className="container">
          <div className="tc-section-head">
            <div>
              <div className="tc-eyebrow" style={{ marginBottom: 6 }}>
                Curated Selection
              </div>
              <h2>Featured Timepieces</h2>
            </div>
            <Link to="/catalog" className="btn-outline">
              Shop Full Catalog
            </Link>
          </div>

          {loading ? (
            <p style={{ color: 'var(--muted)' }}>Loading curated watches...</p>
          ) : featured.length === 0 ? (
            <p style={{ color: 'var(--muted)' }}>No featured watches available right now.</p>
          ) : (
            <div className="tc-grid-4">
              {featured.map((w) => (
                <WatchCard key={w.id} watch={w} />
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
