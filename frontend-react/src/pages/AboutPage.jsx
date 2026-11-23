import React from 'react';
import { Link } from 'react-router-dom';

export default function AboutPage() {
  return (
    <div className="container tc-section">
      <div style={{ maxWidth: 780, margin: '0 auto 56px', textAlign: 'center' }}>
        <div className="tc-eyebrow">The TimeCraft Maison</div>
        <h1 style={{ fontSize: 'clamp(40px, 5vw, 58px)', lineHeight: 1.08, margin: '0 0 18px' }}>
          Where Swiss Craftsmanship Meets Indian Horological Heritage.
        </h1>
        <p style={{ color: 'var(--muted)', fontSize: 16 }}>
          TimeCraft was founded for collectors who value mechanical integrity, transparent provenance, and timeless design — bringing together Geneva’s storied ateliers and India’s iconic watchmakers Titan and Fastrack.
        </p>
      </div>

      <div className="tc-grid-3" style={{ marginBottom: 48 }}>
        {[
          ['01 · Provenance', 'Every timepiece undergoes a multi-point movement, dial, and serial inspection before entering our vault.'],
          ['02 · Curation', 'From Swiss automatic chronographs to minimalist Titan Edge calibres and bold Fastrack editions.'],
          ['03 · Insured Delivery', 'Complimentary insured courier across India on orders above ₹10,000 with full warranty documentation.'],
        ].map(([title, copy]) => (
          <div key={title} className="tc-card">
            <h3 className="serif" style={{ fontSize: 24, marginTop: 0, color: 'var(--gold)' }}>
              {title}
            </h3>
            <p style={{ color: 'var(--muted)', margin: 0 }}>{copy}</p>
          </div>
        ))}
      </div>

      <div className="tc-card" style={{ textAlign: 'center', padding: '40px 24px', background: 'var(--stone)' }}>
        <blockquote className="serif" style={{ fontSize: 30, margin: '0 auto 16px', maxWidth: 700 }}>
          “A fine watch does not merely measure hours — it marks the chapters you choose to remember.”
        </blockquote>
        <Link to="/catalog" className="btn-primary">
          Discover the Collection
        </Link>
      </div>
    </div>
  );
}
