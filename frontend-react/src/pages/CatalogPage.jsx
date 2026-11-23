import React, { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { SlidersHorizontal, X } from 'lucide-react';
import { api } from '../api/client';
import WatchCard from '../components/WatchCard';

export default function CatalogPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [brands, setBrands] = useState([]);
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);

  const [filters, setFilters] = useState({
    q: searchParams.get('q') || '',
    categoryId: searchParams.get('categoryId') || '',
    brandId: searchParams.get('brandId') || '',
    movement: searchParams.get('movement') || '',
    strap: searchParams.get('strap') || '',
    dialColor: searchParams.get('dialColor') || '',
    inStock: searchParams.get('inStock') === 'true',
    sort: searchParams.get('sort') || 'newest',
  });

  useEffect(() => {
    Promise.all([
      api('/catalog/brands').catch(() => []),
      api('/catalog/categories').catch(() => []),
    ]).then(([b, c]) => {
      setBrands(Array.isArray(b) ? b : []);
      setCategories(Array.isArray(c) ? c : []);
    });
  }, []);

  useEffect(() => {
    const params = new URLSearchParams();
    if (filters.q.trim()) params.set('q', filters.q.trim());
    if (filters.categoryId) params.set('categoryId', filters.categoryId);
    if (filters.brandId) params.set('brandId', filters.brandId);
    if (filters.movement) params.set('movement', filters.movement);
    if (filters.strap) params.set('strap', filters.strap);
    if (filters.dialColor) params.set('dialColor', filters.dialColor);
    if (filters.inStock) params.set('inStock', 'true');
    if (filters.sort && filters.sort !== 'newest') params.set('sort', filters.sort);
    setSearchParams(params, { replace: true });

    let active = true;
    setLoading(true);
    params.set('size', '40');
    api(`/catalog/products?${params.toString()}`)
      .then((res) => {
        if (!active) return;
        setProducts(res?.content || []);
        setTotalElements(res?.totalElements ?? (res?.content?.length || 0));
      })
      .catch(() => {
        if (active) setProducts([]);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [filters, setSearchParams]);

  const activePills = useMemo(() => {
    const pills = [];
    if (filters.q.trim()) pills.push({ key: 'q', label: `Search: ${filters.q}` });
    if (filters.brandId) {
      const b = brands.find((x) => String(x.id) === String(filters.brandId));
      pills.push({ key: 'brandId', label: `Maison: ${b?.name || filters.brandId}` });
    }
    if (filters.categoryId) {
      const c = categories.find((x) => String(x.id) === String(filters.categoryId));
      pills.push({ key: 'categoryId', label: `Category: ${c?.name || filters.categoryId}` });
    }
    if (filters.movement) pills.push({ key: 'movement', label: `Movement: ${filters.movement}` });
    if (filters.strap) pills.push({ key: 'strap', label: `Strap: ${filters.strap}` });
    if (filters.dialColor) pills.push({ key: 'dialColor', label: `Dial: ${filters.dialColor}` });
    if (filters.inStock) pills.push({ key: 'inStock', label: 'In Stock Only' });
    return pills;
  }, [filters, brands, categories]);

  const resetFilters = () =>
    setFilters({
      q: '',
      categoryId: '',
      brandId: '',
      movement: '',
      strap: '',
      dialColor: '',
      inStock: false,
      sort: 'newest',
    });

  const filterForm = (
    <div className="tc-card" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 className="serif" style={{ margin: 0, fontSize: 22 }}>
          Refine Collection
        </h3>
        <button className="tc-icon-btn" style={{ fontSize: 12, color: 'var(--gold)' }} onClick={resetFilters}>
          Reset
        </button>
      </div>

      <div>
        <label style={{ display: 'block', fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>Search</label>
        <input
          className="tc-input"
          placeholder="Name, SKU, Maison..."
          value={filters.q}
          onChange={(e) => setFilters((f) => ({ ...f, q: e.target.value }))}
        />
      </div>

      <div>
        <label style={{ display: 'block', fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>Maison / Brand</label>
        <select
          className="tc-select"
          value={filters.brandId}
          onChange={(e) => setFilters((f) => ({ ...f, brandId: e.target.value }))}
        >
          <option value="">All Maisons</option>
          {brands.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label style={{ display: 'block', fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>Category</label>
        <select
          className="tc-select"
          value={filters.categoryId}
          onChange={(e) => setFilters((f) => ({ ...f, categoryId: e.target.value }))}
        >
          <option value="">All Categories</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label style={{ display: 'block', fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>Movement</label>
        <select
          className="tc-select"
          value={filters.movement}
          onChange={(e) => setFilters((f) => ({ ...f, movement: e.target.value }))}
        >
          <option value="">Any Movement</option>
          <option value="Automatic">Automatic</option>
          <option value="Quartz">Quartz</option>
          <option value="Manual">Manual</option>
          <option value="Solar">Solar</option>
        </select>
      </div>

      <div>
        <label style={{ display: 'block', fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>Strap Material</label>
        <select
          className="tc-select"
          value={filters.strap}
          onChange={(e) => setFilters((f) => ({ ...f, strap: e.target.value }))}
        >
          <option value="">Any Strap</option>
          <option value="Stainless Steel">Stainless Steel</option>
          <option value="Leather">Leather</option>
          <option value="Silicone">Silicone</option>
          <option value="Titanium">Titanium</option>
        </select>
      </div>

      <div>
        <label style={{ display: 'block', fontSize: 12, color: 'var(--muted)', marginBottom: 4 }}>Dial Color</label>
        <select
          className="tc-select"
          value={filters.dialColor}
          onChange={(e) => setFilters((f) => ({ ...f, dialColor: e.target.value }))}
        >
          <option value="">Any Dial</option>
          <option value="Black">Black</option>
          <option value="Blue">Blue</option>
          <option value="White">White</option>
          <option value="Silver">Silver</option>
          <option value="Green">Green</option>
          <option value="Gold">Gold</option>
        </select>
      </div>

      <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, cursor: 'pointer' }}>
        <input
          type="checkbox"
          checked={filters.inStock}
          onChange={(e) => setFilters((f) => ({ ...f, inStock: e.target.checked }))}
        />
        In Stock Only
      </label>
    </div>
  );

  return (
    <div className="container tc-section">
      <div className="tc-section-head">
        <div>
          <div className="tc-eyebrow" style={{ marginBottom: 6 }}>
            Horological Archive
          </div>
          <h1 style={{ margin: 0, fontSize: 42 }}>Watch Catalog</h1>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button
            className="btn-outline tc-mobile-menu-btn"
            onClick={() => setMobileFiltersOpen((v) => !v)}
          >
            <SlidersHorizontal size={14} /> Filters
          </button>
          <select
            className="tc-select"
            style={{ width: 190 }}
            value={filters.sort}
            onChange={(e) => setFilters((f) => ({ ...f, sort: e.target.value }))}
          >
            <option value="newest">Sort: Newest</option>
            <option value="price_asc">Price: Low to High</option>
            <option value="price_desc">Price: High to Low</option>
            <option value="name_asc">Name: A–Z</option>
          </select>
        </div>
      </div>

      {activePills.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 20 }}>
          {activePills.map((p) => (
            <span key={p.key} className="tc-pill">
              {p.label}
              <button
                onClick={() =>
                  setFilters((f) => ({ ...f, [p.key]: p.key === 'inStock' ? false : '' }))
                }
                style={{ background: 'none', border: 'none', padding: 0, display: 'inline-flex' }}
              >
                <X size={13} />
              </button>
            </span>
          ))}
          <button className="tc-icon-btn" style={{ fontSize: 12, color: 'var(--gold)' }} onClick={resetFilters}>
            Reset All
          </button>
        </div>
      )}

      {mobileFiltersOpen && <div style={{ marginBottom: 20 }}>{filterForm}</div>}

      <div className="tc-catalog-layout">
        <aside className="tc-desktop-sidebar">{filterForm}</aside>

        <div>
          <div style={{ marginBottom: 16, fontSize: 13, color: 'var(--muted)' }} className="num">
            Showing {products.length} of {totalElements} timepiece(s)
          </div>

          {loading ? (
            <p style={{ color: 'var(--muted)' }}>Filtering collection...</p>
          ) : products.length === 0 ? (
            <div className="tc-card" style={{ textAlign: 'center', padding: 48 }}>
              <h3 className="serif" style={{ fontSize: 26, marginBottom: 8 }}>
                No matching timepieces
              </h3>
              <p style={{ color: 'var(--muted)', marginBottom: 18 }}>
                Try clearing a filter to explore more watches.
              </p>
              <button className="btn-primary" onClick={resetFilters}>
                Reset Filters
              </button>
            </div>
          ) : (
            <div className="tc-grid-3">
              {products.map((w) => (
                <WatchCard key={w.id} watch={w} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
