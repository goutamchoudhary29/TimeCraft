import React from 'react';
import { Link } from 'react-router-dom';

export default function Footer() {
  return (
    <footer className="tc-footer">
      <div className="container">
        <div className="tc-grid-3" style={{ paddingBottom: 36, borderBottom: '1px solid rgba(255,255,255,0.12)' }}>
          <div>
            <div className="serif" style={{ fontSize: 26, color: '#FAF9F6', letterSpacing: '0.14em', marginBottom: 10 }}>
              TIMECRAFT
            </div>
            <p style={{ fontSize: 13, margin: 0, maxWidth: 320 }}>
              Curated Swiss haute horlogerie and iconic Indian watchmakers — authenticated, insured, and delivered across India.
            </p>
          </div>
          <div>
            <div className="tc-eyebrow">Explore</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 13 }}>
              <Link to="/catalog">All Timepieces</Link>
              <Link to="/about">Our Maison</Link>
              <Link to="/wishlist">Saved Wishlist</Link>
              <a href="/admin/dashboard">Admin Atelier</a>
            </div>
          </div>
          <div>
            <div className="tc-eyebrow">Client Services</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 13 }}>
              <span>Complimentary insured shipping above ₹10,000</span>
              <span>Razorpay verified secure INR checkout</span>
              <span>2-Year International &amp; Indian Warranty</span>
            </div>
          </div>
        </div>
        <div style={{ paddingTop: 20, fontSize: 12, color: 'rgba(250,249,246,0.5)', display: 'flex', justifyContent: 'space-between' }}>
          <span>© {new Date().getFullYear()} TimeCraft Horology Pvt. Ltd.</span>
          <span>React + Spring Boot Luxury Storefront</span>
        </div>
      </div>
    </footer>
  );
}
