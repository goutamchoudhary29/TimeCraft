/**
 * TimeCraft Storefront Client Core — Hodinkee / Mr Porter Luxury Edition
 * Includes:
 * - Single-row 3-column glassmorphism header (never wraps)
 * - Instant live search overlay (watch name, Maison, SKU + Escape support)
 * - Slide-over Bag drawer with free insured shipping progress above ₹10,000
 * - Watch cards with Quick View modal, Wishlist toggle & Compare toggle
 * - Floating Compare Dock + side-by-side comparison modal
 * - Google OAuth 2.0 popup & JWT session rotation
 */
(function () {
    const STORAGE_KEYS = {
        ACCESS: 'tc_access_token',
        REFRESH: 'tc_refresh_token',
        USER: 'tc_user',
        GUEST: 'tc_guest_token',
        COMPARE: 'tc_compare_ids',
        COMPARE_META: 'tc_compare_meta',
        WISHLIST_IDS: 'tc_wishlist_product_ids',
        RECENT: 'tc_recent_watches',
        DELIVERY_PIN: 'tc_delivery_pin'
    };

    const FREE_SHIPPING_THRESHOLD = 10000;

    const inrFormatter = new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 0
    });

    function formatInr(amount) {
        if (amount === null || amount === undefined || isNaN(Number(amount))) return '₹0';
        return inrFormatter.format(Number(amount));
    }

    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function getUser() {
        try {
            const raw = localStorage.getItem(STORAGE_KEYS.USER);
            return raw ? JSON.parse(raw) : null;
        } catch {
            return null;
        }
    }

    function setAuthSession(authResponse) {
        if (!authResponse) return;
        if (authResponse.accessToken) localStorage.setItem(STORAGE_KEYS.ACCESS, authResponse.accessToken);
        if (authResponse.refreshToken) localStorage.setItem(STORAGE_KEYS.REFRESH, authResponse.refreshToken);
        if (authResponse.user) localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(authResponse.user));
    }

    function clearAuthSession() {
        localStorage.removeItem(STORAGE_KEYS.ACCESS);
        localStorage.removeItem(STORAGE_KEYS.REFRESH);
        localStorage.removeItem(STORAGE_KEYS.USER);
        localStorage.removeItem(STORAGE_KEYS.WISHLIST_IDS);
    }

    async function api(path, options = {}, retried = false) {
        const headers = Object.assign({ 'Accept': 'application/json' }, options.headers || {});
        if (options.body && !(options.body instanceof FormData) && !headers['Content-Type']) {
            headers['Content-Type'] = 'application/json';
        }
        const accessToken = localStorage.getItem(STORAGE_KEYS.ACCESS);
        if (accessToken) headers['Authorization'] = 'Bearer ' + accessToken;
        const guestToken = localStorage.getItem(STORAGE_KEYS.GUEST);
        if (guestToken) headers['X-Guest-Token'] = guestToken;

        const res = await fetch('/api/v1' + path, {
            method: options.method || 'GET',
            headers,
            credentials: 'same-origin',
            body: options.body && !(options.body instanceof FormData) && typeof options.body !== 'string'
                ? JSON.stringify(options.body)
                : options.body
        });

        const returnedGuest = res.headers.get('X-Guest-Token');
        if (returnedGuest) localStorage.setItem(STORAGE_KEYS.GUEST, returnedGuest);

        if (res.status === 401 && !retried && !path.startsWith('/auth/')) {
            const refreshed = await tryRefreshToken();
            if (refreshed) return api(path, options, true);
        }

        if (res.status === 204) return null;
        const text = await res.text();
        let data = null;
        try {
            data = text ? JSON.parse(text) : null;
        } catch {
            data = { message: text };
        }
        if (!res.ok) {
            const err = new Error((data && data.message) || ('Request failed (' + res.status + ')'));
            err.status = res.status;
            err.payload = data;
            throw err;
        }
        if (!options.skipCartAutoCommit && isCartDtoPayload(path, data)) {
            commitServerCart(data, { broadcast: true, source: path });
        }
        return data;
    }

    async function tryRefreshToken() {
        const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH);
        try {
            const res = await fetch('/api/v1/auth/refresh', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ refreshToken })
            });
            if (!res.ok) {
                clearAuthSession();
                return false;
            }
            setAuthSession(await res.json());
            return true;
        } catch {
            clearAuthSession();
            return false;
        }
    }

    async function logout() {
        const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH);
        try {
            await api('/auth/logout', { method: 'POST', body: { refreshToken } });
        } catch {}
        clearAuthSession();
        window.location.href = '/pages/login.html';
    }

    function toast(message, type = 'info') {
        let host = document.getElementById('tcToastHost');
        if (!host) {
            host = document.createElement('div');
            host.id = 'tcToastHost';
            host.className = 'tc-toast-host';
            document.body.appendChild(host);
        }
        const el = document.createElement('div');
        el.className = 'tc-toast' + (type === 'error' ? ' error' : '');
        el.textContent = message;
        host.appendChild(el);
        setTimeout(() => el.remove(), 3400);
    }

    // Wishlist cached product IDs
    function getWishlistProductIds() {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEYS.WISHLIST_IDS) || '[]');
        } catch {
            return [];
        }
    }

    function setWishlistProductIds(ids) {
        localStorage.setItem(STORAGE_KEYS.WISHLIST_IDS, JSON.stringify(ids));
        const badge = document.getElementById('tcWishlistCount');
        if (badge) badge.textContent = String(ids.length);
    }

    // Compare tray + metadata cache
    function getCompareIds() {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEYS.COMPARE) || '[]');
        } catch {
            return [];
        }
    }

    function getCompareMeta() {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEYS.COMPARE_META) || '{}');
        } catch {
            return {};
        }
    }

    function toggleCompare(productId, meta = null) {
        const id = Number(productId);
        let ids = getCompareIds();
        const metaMap = getCompareMeta();
        if (meta && meta.id) metaMap[meta.id] = meta;

        if (ids.includes(id)) {
            ids = ids.filter(x => x !== id);
            localStorage.setItem(STORAGE_KEYS.COMPARE, JSON.stringify(ids));
            toast('Removed timepiece from comparison');
        } else {
            if (ids.length >= 4) {
                toast('You can compare up to 4 timepieces at once', 'error');
                return ids;
            }
            ids.push(id);
            localStorage.setItem(STORAGE_KEYS.COMPARE, JSON.stringify(ids));
            localStorage.setItem(STORAGE_KEYS.COMPARE_META, JSON.stringify(metaMap));
            toast(`Added to comparison (${ids.length}/4)`);
        }
        document.querySelectorAll(`[data-compare-btn="${id}"]`).forEach(btn => {
            btn.classList.toggle('active-gold', ids.includes(id));
        });
        updateHeaderBadges();
        renderCompareDock();
        return ids;
    }

    function recordRecentlyViewed(summary) {
        if (!summary || !summary.id) return;
        try {
            let list = JSON.parse(localStorage.getItem(STORAGE_KEYS.RECENT) || '[]');
            list = list.filter(x => x.id !== summary.id);
            list.unshift(summary);
            localStorage.setItem(STORAGE_KEYS.RECENT, JSON.stringify(list.slice(0, 8)));
        } catch {}
    }

    function getRecentlyViewed() {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEYS.RECENT) || '[]');
        } catch {
            return [];
        }
    }

    async function quickAddToCart(variantId, quantity = 1, openDrawer = true) {
        if (!variantId) {
            toast('Please select a variant on the watch detail page', 'error');
            return;
        }
        try {
            const cart = await api('/cart/items', {
                method: 'POST',
                body: { variantId: Number(variantId), quantity: Number(quantity) }
            });
            toast('Added timepiece to your bag');
            updateHeaderBadges(cart);
            if (openDrawer) openBagDrawer(cart);
            return cart;
        } catch (err) {
            toast(err.message, 'error');
        }
    }

    async function quickAddToWishlist(productId, variantId = null) {
        if (!getUser()) {
            toast('Please sign in to save watches to your wishlist', 'error');
            setTimeout(() => { window.location.href = '/pages/login.html'; }, 700);
            return;
        }
        const pid = Number(productId);
        try {
            const list = await api('/wishlist');
            const existing = (list || []).find(item => item.productId === pid);
            let updated;
            if (existing) {
                updated = await api('/wishlist/items/' + existing.id, { method: 'DELETE' });
                toast('Removed from your wishlist');
            } else {
                updated = await api('/wishlist/items', {
                    method: 'POST',
                    body: { productId: pid, variantId: variantId ? Number(variantId) : null }
                });
                toast('Saved to your wishlist');
            }
            const ids = (updated || []).map(i => i.productId);
            setWishlistProductIds(ids);
            document.querySelectorAll(`[data-wish-btn="${pid}"]`).forEach(btn => {
                btn.classList.toggle('active', ids.includes(pid));
            });
        } catch (err) {
            toast(err.message, 'error');
        }
    }

    const HEART_SVG = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M12 20.4s-7.5-4.6-9.3-9.2A5.3 5.3 0 0 1 12 6.3a5.3 5.3 0 0 1 9.3 4.9c-1.8 4.6-9.3 9.2-9.3 9.2z" stroke-linejoin="round"/></svg>`;
    const COMPARE_SVG = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M16 4l4 4-4 4M20 8H4M8 20l-4-4 4-4M4 16h16" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
    const TRUCK_SVG = `<svg class="tc-delivery-icon-inline" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M1 4h14v12H1zM15 8h4l3 4v4h-7V8z" stroke-linejoin="round"/><circle cx="5.5" cy="18" r="2"/><circle cx="18.5" cy="18" r="2"/></svg>`;

    function getSavedDeliveryPin() {
        try {
            const pin = String(localStorage.getItem(STORAGE_KEYS.DELIVERY_PIN) || '').trim();
            return /^\d{6}$/.test(pin) ? pin : '';
        } catch {
            return '';
        }
    }

    function setSavedDeliveryPin(pin) {
        try {
            const clean = String(pin || '').trim();
            if (/^\d{6}$/.test(clean)) {
                localStorage.setItem(STORAGE_KEYS.DELIVERY_PIN, clean);
            } else {
                localStorage.removeItem(STORAGE_KEYS.DELIVERY_PIN);
            }
        } catch {}
    }

    function formatDeliveryDate(isoDate) {
        if (!isoDate) return '';
        const parts = String(isoDate).split('-');
        if (parts.length !== 3) return String(isoDate);
        const dt = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
        if (Number.isNaN(dt.getTime())) return String(isoDate);
        return dt.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
    }

    function formatDeliveryDateRange(startIso, endIso) {
        const s = formatDeliveryDate(startIso);
        const e = formatDeliveryDate(endIso);
        if (s && e && s !== e) return `${s} – ${e}`;
        return s || e || '';
    }

    function renderDeliveryLine(estimate, opts = {}) {
        const cls = opts.className || 'tc-card-delivery';
        if (!estimate) {
            return `<div class="${cls}">${TRUCK_SVG}<span>Estimated Delivery: 3–7 Business Days</span></div>`;
        }
        const unavailable = !estimate.serviceable || !estimate.available || estimate.stockCondition === 'OUT_OF_STOCK';
        const special = estimate.stockCondition === 'PRE_ORDER' || estimate.stockCondition === 'MADE_TO_ORDER';
        const stateCls = unavailable ? ' is-unavailable' : (special ? ' is-special' : '');
        const dateRange = (opts.showDates && estimate.estimatedDeliveryStartDate)
            ? formatDeliveryDateRange(estimate.estimatedDeliveryStartDate, estimate.estimatedDeliveryEndDate)
            : '';
        const text = dateRange && !unavailable
            ? `Estimated Delivery: ${dateRange}`
            : (estimate.shortLabel || 'Estimated Delivery: 3–7 Business Days');
        return `<div class="${cls}${stateCls}" title="${escapeHtml(estimate.detailMessage || text)}">${TRUCK_SVG}<span>${escapeHtml(text)}</span></div>`;
    }

    function getEditorialLabel(p) {
        if (!p) return null;
        if ((p.totalAvailableStock || 0) <= 0) {
            return { text: 'Sold Out', cls: 'tc-watch-tag-muted' };
        }
        const brand = String(p.brandName || '').toLowerCase();
        const price = Number(p.sellingPrice || 0);
        const movement = String(p.movementType || '').toUpperCase();

        if (brand.includes('aurelian')) {
            return { text: price >= 500000 ? 'Limited Edition' : 'Swiss Made', cls: 'tc-watch-tag-gold' };
        }
        if (movement === 'MANUAL' || price >= 250000) {
            return { text: 'Haute Horlogerie', cls: 'tc-watch-tag-gold' };
        }
        if (p.newArrival) {
            return { text: movement === 'AUTOMATIC' || movement === 'SOLAR' ? 'New Calibre' : 'New Edition', cls: '' };
        }
        if (p.featured && price >= 90000) {
            return { text: 'Boutique Exclusive', cls: 'tc-watch-tag-gold' };
        }
        if (brand.includes('vérac') || brand.includes('verac') || brand.includes('kestrel') || brand.includes('halden')) {
            return { text: 'Heritage Collection', cls: '' };
        }
        if (p.featured) {
            return { text: 'Atelier Selection', cls: '' };
        }
        return { text: price >= 55000 ? 'Collector Edition' : 'Contemporary Edit', cls: '' };
    }

    const FALLBACK_CARD_IMG = 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=700&q=80';

    function renderWatchCard(p, options = {}) {
        const outOfStock = (p.totalAvailableStock || 0) <= 0;
        const wishIds = getWishlistProductIds();
        const cmpIds = getCompareIds();
        const isWished = wishIds.includes(p.id);
        const isCompared = cmpIds.includes(p.id);

        if (p && p.id) {
            const metaMap = getCompareMeta();
            metaMap[p.id] = { id: p.id, name: p.name, slug: p.slug, brandName: p.brandName, primaryImageUrl: p.primaryImageUrl, sellingPrice: p.sellingPrice };
            try { localStorage.setItem(STORAGE_KEYS.COMPARE_META, JSON.stringify(metaMap)); } catch {}
        }

        const labelObj = getEditorialLabel(p);
        const badge = labelObj
            ? `<span class="tc-watch-tag ${labelObj.cls || ''}">${escapeHtml(labelObj.text)}</span>`
            : '';

        const modelRefLine = p.modelName
            ? `<div class="tc-watch-model-ref tc-num">Ref. ${escapeHtml(p.modelName)}</div>`
            : `<div class="tc-watch-model-ref">&nbsp;</div>`;

        const cardHtml = `
          <article class="tc-watch-card">
            <div class="tc-watch-media">
              ${badge}
              <div class="tc-card-FloatingActions">
                <button type="button" class="tc-card-icon-pill ${isWished ? 'active' : ''}"
                        data-wish-btn="${p.id}" title="Save to Wishlist" aria-label="Save to Wishlist"
                        onclick="TC.quickAddToWishlist(${p.id}, ${p.defaultVariantId || 'null'})">${HEART_SVG}</button>
                <button type="button" class="tc-card-icon-pill tc-compare-pill ${isCompared ? 'active-gold' : ''}"
                        data-compare-btn="${p.id}" title="Compare Timepiece" aria-label="Compare Timepiece"
                        onclick="TC.toggleCompare(${p.id})">${COMPARE_SVG}</button>
              </div>
              <a href="/pages/product-details.html?slug=${encodeURIComponent(p.slug || p.id)}" class="d-block w-100 h-100">
                <img src="${escapeHtml(p.primaryImageUrl || FALLBACK_CARD_IMG)}"
                     alt="${escapeHtml(p.brandName ? p.brandName + ' ' + p.name : p.name)}"
                     loading="lazy" decoding="async"
                     onerror="this.onerror=null;this.src='${FALLBACK_CARD_IMG}';">
              </a>
              <button type="button" class="tc-quickview-bar"
                      onclick="TC.openQuickView('${escapeHtml(p.slug || p.id)}')">
                Quick View Dossier
              </button>
            </div>
            <div class="tc-watch-body">
              <div class="min-w-0">
                <div class="tc-watch-brand">${escapeHtml(p.brandName)}</div>
                <h3 class="tc-watch-title">
                  <a href="/pages/product-details.html?slug=${encodeURIComponent(p.slug || p.id)}">${escapeHtml(p.name)}</a>
                </h3>
                ${modelRefLine}
                <div class="tc-watch-meta">
                  <span>${escapeHtml(p.movementType || 'AUTOMATIC')}</span>
                  <span>&middot;</span>
                  <span class="tc-num">${escapeHtml(p.caseDiameterMm || 40)} MM</span>
                  <span>&middot;</span>
                  <span class="tc-num">${escapeHtml(p.waterResistanceM || 50)}M</span>
                </div>
                ${renderDeliveryLine(p.deliveryEstimate, { className: 'tc-card-delivery' })}
              </div>
              <div class="tc-watch-footer-row">
                <span class="tc-price fw-semibold fs-6 text-dark">${formatInr(p.sellingPrice)}</span>
                <button type="button" class="tc-card-add-btn"
                        ${outOfStock || !p.defaultVariantId ? 'disabled' : ''}
                        onclick="TC.quickAddToCart(${p.defaultVariantId || 0}, 1, true)">
                  ${outOfStock ? 'Unavailable' : 'Add to Bag'}
                </button>
              </div>
            </div>
          </article>`;

        if (options && options.bare) {
            return cardHtml;
        }
        return `<div class="col-sm-6 col-lg-3">${cardHtml}</div>`;
    }

    // =========================================================================
    // QUICK VIEW MODAL
    // =========================================================================
    async function openQuickView(slugOrId) {
        let backdrop = document.getElementById('tcQuickViewBackdrop');
        if (!backdrop) {
            backdrop = document.createElement('div');
            backdrop.id = 'tcQuickViewBackdrop';
            backdrop.className = 'tc-google-modal-backdrop';
            backdrop.addEventListener('click', e => { if (e.target === backdrop) backdrop.remove(); });
            document.body.appendChild(backdrop);
        }
        backdrop.innerHTML = `<div class="tc-modal-dialog p-4 text-center text-muted">Loading horological dossier…</div>`;
        try {
            const p = await api('/products/' + encodeURIComponent(slugOrId));
            const img = (p.images && p.images[0] && p.images[0].url) || 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=900';
            const activeVariants = (p.variants || []).filter(v => v.active);
            const firstVar = activeVariants.find(v => v.availableStock > 0) || activeVariants[0] || {};
            const outOfStock = (firstVar.availableStock || 0) <= 0;

            backdrop.innerHTML = `
              <div class="tc-modal-dialog p-4 p-lg-5">
                <div class="d-flex justify-content-between align-items-start mb-3">
                  <div>
                    <div class="tc-eyebrow">${escapeHtml(p.brandName)} · Model ${escapeHtml(p.modelName)}</div>
                    <h2 class="tc-serif fs-2 mb-0">${escapeHtml(p.name)}</h2>
                  </div>
                  <button type="button" class="btn-close" onclick="document.getElementById('tcQuickViewBackdrop')?.remove()"></button>
                </div>
                <div class="row g-4 align-items-center">
                  <div class="col-md-5">
                    <div class="tc-watch-media">
                      <img src="${escapeHtml(img)}" alt="${escapeHtml(p.name)}">
                    </div>
                  </div>
                  <div class="col-md-7">
                    <div class="d-flex align-items-baseline gap-2 mb-3 tc-price">
                      <span class="fs-3 fw-semibold">${formatInr(firstVar.sellingPrice || p.sellingPrice)}</span>
                      ${Number(p.discountPercent) > 0 ? `<span class="text-muted text-decoration-line-through">${formatInr(firstVar.regularPrice || p.regularPrice)}</span>` : ''}
                    </div>
                    <p class="small text-muted mb-3">${escapeHtml(p.shortDescription || p.description || '')}</p>
                    <table class="table table-sm table-bordered tc-spec-table small mb-3">
                      <tbody>
                        <tr><th>Calibre / Movement</th><td>${escapeHtml(p.movementType)}</td></tr>
                        <tr><th>Case Size &amp; Material</th><td class="tc-num">${escapeHtml(p.caseDiameterMm)} mm · ${escapeHtml(p.caseMaterial)}</td></tr>
                        <tr><th>Water Resistance</th><td class="tc-num">${escapeHtml(p.waterResistanceM)} m</td></tr>
                        <tr><th>Crystal &amp; Strap</th><td>${escapeHtml(p.crystalType)} · ${escapeHtml(p.strapMaterial)}</td></tr>
                        <tr><th>Dial Color</th><td>${escapeHtml(p.dialColor)}</td></tr>
                        <tr><th>Warranty</th><td>${p.warrantyMonths} Months Official Warranty</td></tr>
                      </tbody>
                    </table>
                    <div class="d-flex flex-wrap gap-2">
                      <button type="button" class="btn-tc-primary flex-grow-1"
                              ${outOfStock || !firstVar.id ? 'disabled' : ''}
                              onclick="TC.quickAddToCart(${firstVar.id || 0}, 1, true);document.getElementById('tcQuickViewBackdrop')?.remove();">
                        ${outOfStock ? 'Sold Out' : 'Add to Bag'}
                      </button>
                      <button type="button" class="btn-tc-secondary" onclick="TC.toggleCompare(${p.id})">⇄ Compare</button>
                      <a href="/pages/product-details.html?slug=${encodeURIComponent(p.slug)}" class="btn-tc-secondary">Full Dossier &rarr;</a>
                    </div>
                  </div>
                </div>
              </div>`;
        } catch (err) {
            backdrop.innerHTML = `<div class="tc-modal-dialog p-4 text-danger">${escapeHtml(err.message)}</div>`;
        }
    }

    // =========================================================================
    // FLOATING COMPARE DOCK & SIDE-BY-SIDE COMPARISON MODAL
    // =========================================================================
    function renderCompareDock() {
        let dock = document.getElementById('tcCompareDock');
        if (!dock) {
            dock = document.createElement('div');
            dock.id = 'tcCompareDock';
            dock.className = 'tc-compare-dock';
            document.body.appendChild(dock);
        }
        const ids = getCompareIds();
        if (!ids.length) {
            dock.classList.remove('visible');
            dock.innerHTML = '';
            return;
        }
        const meta = getCompareMeta();
        dock.innerHTML = `
          <div class="d-flex align-items-center gap-2">
            <span class="tc-eyebrow" style="color:#B08D57;">Compare (${ids.length}/4)</span>
            <div class="d-flex gap-2">
              ${ids.map(id => {
                  const m = meta[id] || {};
                  return `
                  <div class="tc-compare-thumb" title="${escapeHtml(m.name || 'Watch #' + id)}">
                    ${m.primaryImageUrl ? `<img src="${escapeHtml(m.primaryImageUrl)}" alt="">` : ''}
                    <button type="button" onclick="TC.toggleCompare(${id})">&times;</button>
                  </div>`;
              }).join('')}
            </div>
          </div>
          <div class="d-flex align-items-center gap-2">
            <button type="button" class="btn-tc-primary py-1 px-3" style="background:#B08D57;border-color:#B08D57;"
                    onclick="TC.openCompareModal()">Compare Specs</button>
            <button type="button" class="btn-tc-secondary py-1 px-2 text-white border-secondary"
                    onclick="localStorage.removeItem('tc_compare_ids');TC.updateHeaderBadges();TC.renderCompareDock();">Clear</button>
          </div>`;
        dock.classList.add('visible');
    }

    async function openCompareModal() {
        const ids = getCompareIds();
        if (!ids.length) return;
        let backdrop = document.getElementById('tcCompareModalBackdrop');
        if (!backdrop) {
            backdrop = document.createElement('div');
            backdrop.id = 'tcCompareModalBackdrop';
            backdrop.className = 'tc-google-modal-backdrop';
            backdrop.addEventListener('click', e => { if (e.target === backdrop) backdrop.remove(); });
            document.body.appendChild(backdrop);
        }
        backdrop.innerHTML = `<div class="tc-modal-dialog p-4 text-center text-muted">Loading side-by-side comparison…</div>`;
        try {
            const watches = await api('/products/compare?ids=' + ids.join(','));
            const row = (label, fn) => `<tr><th>${label}</th>${watches.map(w => `<td>${fn(w)}</td>`).join('')}</tr>`;
            backdrop.innerHTML = `
              <div class="tc-modal-dialog p-4" style="max-width:980px;">
                <div class="d-flex justify-content-between align-items-center mb-3">
                  <div>
                    <div class="tc-eyebrow">Horological Specification Matrix</div>
                    <h2 class="tc-serif fs-3 mb-0">Side-by-Side Watch Comparison</h2>
                  </div>
                  <div class="d-flex gap-2 align-items-center">
                    <a href="/pages/compare.html" class="btn-tc-secondary py-1 px-3">Open Full Page</a>
                    <button type="button" class="btn-close" onclick="document.getElementById('tcCompareModalBackdrop')?.remove()"></button>
                  </div>
                </div>
                <div class="table-responsive">
                  <table class="table table-bordered tc-spec-table align-middle small mb-0">
                    <tbody>
                      ${row('Timepiece', w => `
                        <div class="text-center">
                          <img src="${escapeHtml((w.images && w.images[0] && w.images[0].url) || '')}" alt="" style="width:80px;height:100px;object-fit:cover;border:1px solid #E5E2DC;">
                          <div class="tc-eyebrow mt-1">${escapeHtml(w.brandName)}</div>
                          <a href="/pages/product-details.html?slug=${encodeURIComponent(w.slug)}" class="tc-serif fs-5 d-block">${escapeHtml(w.name)}</a>
                        </div>`)}
                      ${row('Price (INR)', w => `<span class="tc-price fw-semibold">${formatInr(w.sellingPrice)}</span>`)}
                      ${row('Calibre / Movement', w => escapeHtml(w.movementType))}
                      ${row('Case Diameter', w => `<span class="tc-num">${escapeHtml(w.caseDiameterMm)} mm (${escapeHtml(w.caseMaterial)})</span>`)}
                      ${row('Water Resistance', w => `<span class="tc-num">${escapeHtml(w.waterResistanceM)} m</span>`)}
                      ${row('Crystal', w => escapeHtml(w.crystalType))}
                      ${row('Strap / Bracelet', w => escapeHtml(w.strapMaterial))}
                      ${row('Dial Color', w => escapeHtml(w.dialColor))}
                      ${row('Warranty', w => `${w.warrantyMonths} Months`)}
                    </tbody>
                  </table>
                </div>
              </div>`;
        } catch (err) {
            backdrop.innerHTML = `<div class="tc-modal-dialog p-4 text-danger">${escapeHtml(err.message)}</div>`;
        }
    }

    // =========================================================================
    // SLIDE-OVER BAG DRAWER
    // =========================================================================
    function ensureGlobalOverlays() {
        if (document.getElementById('tcSearchOverlay')) return;
        const host = document.createElement('div');
        host.innerHTML = `
          <!-- Live Instant Search Overlay -->
          <div class="tc-overlay-backdrop" id="tcSearchOverlay">
            <div class="tc-search-sheet">
              <div class="container">
                <div class="d-flex justify-content-between align-items-center mb-2">
                  <span class="tc-eyebrow">Instant Horological Search (Press ESC to close)</span>
                  <button type="button" class="btn-close" id="tcSearchCloseBtn"></button>
                </div>
                <input type="search" id="tcInstantSearchInput" class="tc-search-input-lg"
                       placeholder="Type watch name, Maison (e.g. Titan, Aurelian) or SKU…" autocomplete="off">
                <div class="row g-3 mt-3" id="tcInstantSearchResults"></div>
              </div>
            </div>
          </div>

          <!-- Slide-Over Bag Drawer -->
          <div class="tc-overlay-backdrop" id="tcBagBackdrop"></div>
          <aside class="tc-drawer" id="tcBagDrawer" aria-label="Shopping Bag Drawer">
            <div class="tc-drawer-header">
              <div>
                <div class="tc-eyebrow">Insured Vault Bag</div>
                <div class="d-flex align-items-center gap-2">
                  <h2 class="tc-serif fs-3 mb-0">Shopping Bag</h2>
                  <span class="tc-badge-count tc-num" data-tc-bag-count>0</span>
                </div>
              </div>
              <button type="button" class="btn-close" id="tcBagCloseBtn" aria-label="Close Shopping Bag"></button>
            </div>
            <div class="tc-drawer-body" id="tcBagDrawerBody"></div>
            <div class="tc-drawer-footer" id="tcBagDrawerFooter"></div>
          </aside>`;
        document.body.appendChild(host);

        const searchOverlay = document.getElementById('tcSearchOverlay');
        const searchInput = document.getElementById('tcInstantSearchInput');
        const searchResults = document.getElementById('tcInstantSearchResults');

        document.getElementById('tcSearchCloseBtn').addEventListener('click', closeSearchOverlay);
        searchOverlay.addEventListener('click', e => { if (e.target === searchOverlay) closeSearchOverlay(); });

        let debounceTimer = null;
        searchInput.addEventListener('input', () => {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => runInstantSearch(searchInput.value.trim()), 180);
        });
        searchInput.addEventListener('keydown', e => {
            if (e.key === 'Enter' && searchInput.value.trim()) {
                window.location.href = '/pages/search.html?q=' + encodeURIComponent(searchInput.value.trim());
            }
        });

        async function runInstantSearch(q) {
            if (!q) {
                searchResults.innerHTML = `<div class="col-12 small text-muted">Popular searches: <a href="/pages/search.html?q=Titan" class="text-decoration-underline">Titan</a> · <a href="/pages/search.html?q=Fastrack" class="text-decoration-underline">Fastrack</a> · <a href="/pages/search.html?q=Moonphase" class="text-decoration-underline">Moonphase</a> · <a href="/pages/search.html?q=Diver" class="text-decoration-underline">Diver 300</a></div>`;
                return;
            }
            try {
                const page = await api('/products/search?size=6&q=' + encodeURIComponent(q));
                if (!page.content || !page.content.length) {
                    searchResults.innerHTML = `<div class="col-12 small text-muted">No watches matched “${escapeHtml(q)}”.</div>`;
                    return;
                }
                searchResults.innerHTML = page.content.map(w => `
                  <div class="col-md-6">
                    <a href="/pages/product-details.html?slug=${encodeURIComponent(w.slug)}" class="tc-search-hit">
                      <img src="${escapeHtml(w.primaryImageUrl)}" alt="">
                      <div class="flex-grow-1 min-w-0">
                        <div class="tc-eyebrow">${escapeHtml(w.brandName)} · SKU ${escapeHtml(w.sku)}</div>
                        <div class="tc-serif fs-5 text-truncate">${escapeHtml(w.name)}</div>
                        <div class="small text-muted">${escapeHtml(w.movementType)} · ${escapeHtml(w.caseDiameterMm)} mm</div>
                      </div>
                      <div class="tc-price fw-semibold">${formatInr(w.sellingPrice)}</div>
                    </a>
                  </div>`).join('') + `
                  <div class="col-12 pt-1">
                    <a href="/pages/search.html?q=${encodeURIComponent(q)}" class="small text-uppercase fw-semibold">View all ${page.totalElements} results for “${escapeHtml(q)}” &rarr;</a>
                  </div>`;
            } catch (err) {
                searchResults.innerHTML = `<div class="col-12 small text-danger">${escapeHtml(err.message)}</div>`;
            }
        }

        document.getElementById('tcBagCloseBtn').addEventListener('click', closeBagDrawer);
        document.getElementById('tcBagBackdrop').addEventListener('click', closeBagDrawer);

        document.addEventListener('keydown', e => {
            if (e.key === 'Escape') {
                closeSearchOverlay();
                closeBagDrawer();
                document.getElementById('tcQuickViewBackdrop')?.remove();
                document.getElementById('tcCompareModalBackdrop')?.remove();
            }
        });
    }

    function openSearchOverlay() {
        ensureGlobalOverlays();
        const ov = document.getElementById('tcSearchOverlay');
        ov.classList.add('open');
        const input = document.getElementById('tcInstantSearchInput');
        input.value = '';
        input.dispatchEvent(new Event('input'));
        setTimeout(() => input.focus(), 60);
    }

    function closeSearchOverlay() {
        document.getElementById('tcSearchOverlay')?.classList.remove('open');
    }

    // =========================================================================
    // REAL-TIME CART STATE, OPTIMISTIC QUANTITY ENGINE & CROSS-TAB SYNC
    // =========================================================================
    const CART_SYNC_STORAGE_KEY = 'tc_cart_sync_stamp';
    const cartChannel = (typeof window.BroadcastChannel !== 'undefined')
        ? new window.BroadcastChannel('tc_cart_channel_v1')
        : null;

    let serverCartSnapshot = null;
    let viewCartState = null;
    const pendingItemQty = new Map();
    const coalesceTimers = new Map();
    const inFlightItems = new Set();
    const removingItems = new Set();
    const confirmRemoveItemIds = new Set();

    function cloneCart(cart) {
        if (!cart) return null;
        return JSON.parse(JSON.stringify(cart));
    }

    function isCartDtoPayload(path, data) {
        if (!data || typeof data !== 'object') return false;
        if (!Array.isArray(data.items) || typeof data.totalQuantity !== 'number') return false;
        return path.startsWith('/cart') || path.includes('/move-to-cart');
    }

    function getItemMaxAllowed(item) {
        if (!item) return 5;
        if (item.maxOrderQuantity !== undefined && item.maxOrderQuantity !== null) {
            return Math.max(0, Number(item.maxOrderQuantity));
        }
        const stock = item.availableStock !== undefined && item.availableStock !== null ? Number(item.availableStock) : 5;
        return Math.min(5, Math.max(0, stock));
    }

    function computeOptimisticCart(baseCart, itemId, nextQty) {
        const draft = cloneCart(baseCart);
        if (!draft || !Array.isArray(draft.items)) return draft;

        const prevTotals = draft.totals || {};
        const prevSubtotal = Number(prevTotals.subtotal || 0);
        const taxRate = Number(prevTotals.taxRatePercent !== undefined ? prevTotals.taxRatePercent : 18);
        const taxInclusive = prevTotals.taxInclusive !== undefined ? Boolean(prevTotals.taxInclusive) : true;
        const freeThreshold = Number(prevTotals.freeShippingThreshold !== undefined ? prevTotals.freeShippingThreshold : FREE_SHIPPING_THRESHOLD);

        let subtotal = 0;
        let totalQty = 0;

        draft.items.forEach(it => {
            if (Number(it.id) === Number(itemId)) {
                it.quantity = nextQty;
                const stock = it.availableStock !== undefined ? Number(it.availableStock) : nextQty;
                it.available = stock >= nextQty;
            }
            const unitSelling = Number(it.sellingUnitPrice || 0);
            if (it.available !== false) {
                it.lineSubtotal = Math.round(unitSelling * Number(it.quantity) * 100) / 100;
                subtotal += it.lineSubtotal;
                totalQty += Number(it.quantity);
            } else {
                it.lineSubtotal = 0;
            }
        });

        subtotal = Math.round(subtotal * 100) / 100;
        draft.totalQuantity = totalQty;

        let discountTotal = 0;
        if (draft.appliedCouponCode && prevSubtotal > 0 && Number(prevTotals.discountTotal || 0) > 0 && subtotal > 0) {
            const ratio = Number(prevTotals.discountTotal) / prevSubtotal;
            discountTotal = Math.min(subtotal, Math.round(subtotal * ratio * 100) / 100);
        }

        const discounted = Math.max(0, Math.round((subtotal - discountTotal) * 100) / 100);
        let taxTotal = 0;
        let shippingTotal = 0;
        let grandTotal = 0;

        if (subtotal > 0) {
            taxTotal = taxInclusive
                ? Math.round(((discounted * taxRate) / (100 + taxRate)) * 100) / 100
                : Math.round(((discounted * taxRate) / 100) * 100) / 100;
            shippingTotal = discounted >= freeThreshold ? 0 : 250;
            grandTotal = taxInclusive
                ? Math.round((discounted + shippingTotal) * 100) / 100
                : Math.round((discounted + taxTotal + shippingTotal) * 100) / 100;
        }

        draft.totals = Object.assign({}, prevTotals, {
            subtotal,
            discountTotal,
            discountedSubtotal: discounted,
            taxRatePercent: taxRate,
            taxInclusive,
            taxTotal,
            shippingTotal,
            freeShippingThreshold: freeThreshold,
            grandTotal
        });
        return draft;
    }

    function applyBagCountDom(totalQty) {
        const nextStr = String(Math.max(0, Number(totalQty) || 0));
        const targets = document.querySelectorAll('#tcCartCount, [data-tc-bag-count]');
        targets.forEach(el => {
            const changed = el.textContent !== nextStr;
            el.textContent = nextStr;
            if (changed && el.id === 'tcCartCount') {
                el.classList.remove('tc-badge-pulse');
                void el.offsetWidth;
                el.classList.add('tc-badge-pulse');
            }
        });
    }

    function emitCartUpdated(meta = {}) {
        const detail = {
            cart: cloneCart(viewCartState),
            syncingIds: Array.from(inFlightItems).concat(Array.from(pendingItemQty.keys())),
            removingIds: Array.from(removingItems),
            confirmRemoveIds: Array.from(confirmRemoveItemIds),
            optimistic: Boolean(meta.optimistic),
            source: meta.source || 'local'
        };
        window.dispatchEvent(new CustomEvent('tc:cart-updated', { detail }));
        if (document.getElementById('tcBagDrawer')?.classList.contains('open')) {
            renderBagDrawer(viewCartState, { skipFetch: true });
        }
    }

    function commitServerCart(cartDto, options = {}) {
        if (!cartDto) return;
        serverCartSnapshot = cloneCart(cartDto);
        let mergedView = cloneCart(cartDto);
        // Re-apply any still-pending user target quantities on other items
        pendingItemQty.forEach((targetQty, pItemId) => {
            if (Number(pItemId) !== Number(options.settledItemId)) {
                mergedView = computeOptimisticCart(mergedView, pItemId, targetQty);
            }
        });
        viewCartState = mergedView;
        applyBagCountDom(viewCartState.totalQuantity);

        if (options.broadcast) {
            const payload = { totalQuantity: serverCartSnapshot.totalQuantity, ts: Date.now() };
            try { localStorage.setItem(CART_SYNC_STORAGE_KEY, JSON.stringify(payload)); } catch {}
            try { if (cartChannel) cartChannel.postMessage(payload); } catch {}
        }
        emitCartUpdated({ optimistic: false, source: options.source || 'server' });
    }

    async function handleExternalCartSignal(payload) {
        if (payload && typeof payload.totalQuantity === 'number') {
            applyBagCountDom(payload.totalQuantity);
        }
        try {
            const fresh = await api('/cart', { skipCartAutoCommit: true });
            commitServerCart(fresh, { broadcast: false, source: 'cross-tab' });
        } catch {}
    }

    if (cartChannel) {
        cartChannel.addEventListener('message', ev => {
            if (ev && ev.data) handleExternalCartSignal(ev.data);
        });
    }
    window.addEventListener('storage', ev => {
        if (ev.key === CART_SYNC_STORAGE_KEY && ev.newValue) {
            try { handleExternalCartSignal(JSON.parse(ev.newValue)); } catch {}
        }
    });

    async function getCartState(forceRefresh = false) {
        if (!forceRefresh && viewCartState) {
            return cloneCart(viewCartState);
        }
        const fresh = await api('/cart');
        return cloneCart(fresh);
    }

    function requestRemoveConfirmation(itemId) {
        confirmRemoveItemIds.add(Number(itemId));
        emitCartUpdated({ optimistic: false, source: 'confirm-prompt' });
    }

    function cancelRemoveConfirmation(itemId) {
        confirmRemoveItemIds.delete(Number(itemId));
        emitCartUpdated({ optimistic: false, source: 'confirm-cancel' });
    }

    async function flushItemQuantitySync(itemId) {
        const id = Number(itemId);
        if (inFlightItems.has(id)) return;
        if (!pendingItemQty.has(id)) return;

        const targetQty = pendingItemQty.get(id);
        pendingItemQty.delete(id);

        const confirmedItem = serverCartSnapshot && Array.isArray(serverCartSnapshot.items)
            ? serverCartSnapshot.items.find(x => Number(x.id) === id)
            : null;

        if (confirmedItem && Number(confirmedItem.quantity) === Number(targetQty)) {
            emitCartUpdated({ optimistic: false, source: 'noop' });
            return;
        }

        inFlightItems.add(id);
        emitCartUpdated({ optimistic: true, source: 'sync-start' });

        try {
            const updatedCart = await api('/cart/items/' + id, {
                method: 'PUT',
                body: { quantity: Number(targetQty) },
                skipCartAutoCommit: true
            });
            inFlightItems.delete(id);
            if (pendingItemQty.has(id)) {
                serverCartSnapshot = cloneCart(updatedCart);
                await flushItemQuantitySync(id);
            } else {
                commitServerCart(updatedCart, { broadcast: true, settledItemId: id, source: 'qty-put' });
            }
        } catch (err) {
            inFlightItems.delete(id);
            pendingItemQty.delete(id);
            const timer = coalesceTimers.get(id);
            if (timer) {
                clearTimeout(timer);
                coalesceTimers.delete(id);
            }
            // Restore previous server-verified state immediately
            if (serverCartSnapshot) {
                viewCartState = cloneCart(serverCartSnapshot);
                applyBagCountDom(viewCartState.totalQuantity);
                emitCartUpdated({ optimistic: false, source: 'rollback' });
            }
            toast(err.message || 'Unable to update timepiece quantity', 'error');
            if (err.status === 409 || err.status === 404) {
                try { await api('/cart'); } catch {}
            }
        }
    }

    function stepCartItemQuantity(itemId, delta) {
        const id = Number(itemId);
        if (removingItems.has(id)) return;
        const activeCart = viewCartState || serverCartSnapshot;
        if (!activeCart || !Array.isArray(activeCart.items)) return;

        const item = activeCart.items.find(x => Number(x.id) === id);
        if (!item) return;

        const currentQty = Number(item.quantity || 1);
        const nextQty = currentQty + Number(delta);

        // Never allow negative or zero quantities; at 1, minus triggers remove confirmation
        if (nextQty < 1) {
            requestRemoveConfirmation(id);
            return;
        }

        confirmRemoveItemIds.delete(id);
        const maxAllowed = getItemMaxAllowed(item);
        if (Number(delta) > 0 && nextQty > maxAllowed) {
            const stock = item.availableStock !== undefined ? Number(item.availableStock) : maxAllowed;
            if (stock <= maxAllowed) {
                toast(`Only ${stock} timepiece${stock === 1 ? '' : 's'} available in vault stock`, 'error');
            } else {
                toast(`Maximum ${maxAllowed} units of a single reference allowed per order`, 'error');
            }
            return;
        }

        // Immediate optimistic calculation of item subtotal, cart totals & header bag count
        viewCartState = computeOptimisticCart(activeCart, id, nextQty);
        pendingItemQty.set(id, nextQty);
        applyBagCountDom(viewCartState.totalQuantity);
        emitCartUpdated({ optimistic: true, source: 'step' });

        // Coalesce rapid clicks before hitting backend
        const prevTimer = coalesceTimers.get(id);
        if (prevTimer) clearTimeout(prevTimer);
        coalesceTimers.set(id, setTimeout(() => {
            coalesceTimers.delete(id);
            flushItemQuantitySync(id);
        }, 140));
    }

    async function removeCartItem(itemId) {
        const id = Number(itemId);
        if (removingItems.has(id)) return;

        const prevTimer = coalesceTimers.get(id);
        if (prevTimer) {
            clearTimeout(prevTimer);
            coalesceTimers.delete(id);
        }
        pendingItemQty.delete(id);
        confirmRemoveItemIds.delete(id);
        removingItems.add(id);

        // Optimistically update header counter and totals while DELETE executes
        const prevSnapshot = cloneCart(serverCartSnapshot || viewCartState);
        if (viewCartState && Array.isArray(viewCartState.items)) {
            const draft = cloneCart(viewCartState);
            draft.items = draft.items.filter(x => Number(x.id) !== id);
            viewCartState = computeOptimisticCart(draft, -1, 0);
            applyBagCountDom(viewCartState.totalQuantity);
        }
        emitCartUpdated({ optimistic: true, source: 'remove-start' });

        try {
            const updatedCart = await api('/cart/items/' + id, {
                method: 'DELETE',
                skipCartAutoCommit: true
            });
            removingItems.delete(id);
            commitServerCart(updatedCart, { broadcast: true, settledItemId: id, source: 'remove-done' });
            toast('Removed timepiece from your shopping bag');
            return updatedCart;
        } catch (err) {
            removingItems.delete(id);
            if (prevSnapshot) {
                serverCartSnapshot = prevSnapshot;
                viewCartState = cloneCart(prevSnapshot);
                applyBagCountDom(viewCartState.totalQuantity);
                emitCartUpdated({ optimistic: false, source: 'remove-rollback' });
            }
            toast(err.message || 'Unable to remove timepiece', 'error');
        }
    }

    function syncBodyDrawerLock() {
        const bagOpen = document.getElementById('tcBagDrawer')?.classList.contains('open');
        const navOpen = document.getElementById('tcMobileNavDrawer')?.classList.contains('open');
        const shouldLock = Boolean(bagOpen || navOpen);
        document.documentElement.classList.toggle('tc-drawer-lock', shouldLock);
        document.body.classList.toggle('tc-drawer-lock', shouldLock);
    }

    async function openBagDrawer(prefetchedCart = null) {
        ensureGlobalOverlays();
        document.getElementById('tcBagBackdrop').classList.add('open');
        document.getElementById('tcBagDrawer').classList.add('open');
        syncBodyDrawerLock();
        await renderBagDrawer(prefetchedCart);
    }

    function closeBagDrawer() {
        document.getElementById('tcBagBackdrop')?.classList.remove('open');
        document.getElementById('tcBagDrawer')?.classList.remove('open');
        syncBodyDrawerLock();
    }

    async function renderBagDrawer(prefetchedCart = null, opts = {}) {
        const drawerEl = document.getElementById('tcBagDrawer');
        const bodyEl = document.getElementById('tcBagDrawerBody');
        const footEl = document.getElementById('tcBagDrawerFooter');
        if (!bodyEl || !footEl) return;
        const prevScrollTop = bodyEl.scrollTop;
        try {
            let cart = prefetchedCart;
            if (!cart) {
                cart = (!opts.skipFetch && !viewCartState) ? await api('/cart') : (viewCartState || await api('/cart'));
            }
            const t = cart.totals || { subtotal: 0, discountTotal: 0, taxLabel: 'GST', taxRatePercent: 18, taxInclusive: true, taxTotal: 0, shippingTotal: 0, freeShippingThreshold: FREE_SHIPPING_THRESHOLD, grandTotal: 0 };
            const threshold = Number(t.freeShippingThreshold || FREE_SHIPPING_THRESHOLD);
            const discountedBase = Number(t.discountedSubtotal !== undefined ? t.discountedSubtotal : t.subtotal || 0);
            const pct = threshold > 0 ? Math.min(100, Math.round((discountedBase / threshold) * 100)) : 100;
            const remaining = Math.max(0, threshold - discountedBase);

            const shippingBanner = `
              <div class="tc-shipping-bar-wrap" style="margin-bottom:14px;">
                <div class="d-flex justify-content-between small">
                  <span>${discountedBase > 0 && remaining === 0
                      ? '&#10003; <strong>Complimentary Insured Delivery</strong> unlocked'
                      : `Add <strong class="tc-price">${formatInr(remaining || threshold)}</strong> for complimentary insured delivery`}</span>
                  <span class="tc-num text-muted">${discountedBase > 0 ? pct : 0}%</span>
                </div>
                <div class="tc-shipping-progress">
                  <div class="tc-shipping-progress-fill" style="width:${discountedBase > 0 ? pct : 0}%;"></div>
                </div>
              </div>`;

            const isEmpty = !cart.items || cart.items.length === 0;
            if (drawerEl) drawerEl.classList.toggle('is-empty-bag', isEmpty);

            if (isEmpty) {
                bodyEl.innerHTML = shippingBanner + `
                  <div class="text-center py-5">
                    <div class="tc-serif fs-3 mb-1">Your shopping bag is empty</div>
                    <p class="small text-muted mb-3">Explore our Swiss, European, Japanese and Indian horological collections.</p>
                    <a href="/pages/shop.html" class="btn-tc-primary" onclick="TC.closeBagDrawer()">Explore Timepieces</a>
                  </div>`;
                footEl.innerHTML = '';
                return;
            }

            let hasUnavailable = false;
            bodyEl.innerHTML = shippingBanner + `
              <div class="tc-drawer-product-list">
                ${cart.items.map(item => {
                    const maxQty = getItemMaxAllowed(item);
                    const atMax = Number(item.quantity) >= maxQty;
                    const isSyncing = inFlightItems.has(Number(item.id)) || pendingItemQty.has(Number(item.id));
                    const isConfirming = confirmRemoveItemIds.has(Number(item.id));
                    if (!item.available) hasUnavailable = true;
                    return `
                  <div class="tc-drawer-item ${removingItems.has(Number(item.id)) ? 'opacity-50' : ''}">
                    <a href="/pages/product-details.html?slug=${encodeURIComponent(item.productSlug)}" class="flex-shrink-0" onclick="TC.closeBagDrawer()">
                      <img src="${escapeHtml(item.imageUrl)}" alt="${escapeHtml(item.productName)}" style="width:62px;height:78px;object-fit:cover;border:1px solid var(--tc-border);background:var(--tc-pearl);display:block;">
                    </a>
                    <div class="flex-grow-1 min-w-0">
                      <div class="d-flex justify-content-between align-items-start gap-2">
                        <div class="min-w-0">
                          <div class="tc-eyebrow" style="font-size:9.5px;">${escapeHtml(item.brandName)}</div>
                          <a href="/pages/product-details.html?slug=${encodeURIComponent(item.productSlug)}" class="tc-serif fs-5 d-block text-truncate" onclick="TC.closeBagDrawer()">${escapeHtml(item.productName)}</a>
                        </div>
                        <button type="button" class="tc-bag-remove-link mt-0" style="font-size:9.5px;" aria-label="Remove ${escapeHtml(item.productName)}" onclick="TC.drawerRemoveItem(${item.id})">Remove</button>
                      </div>
                      <div class="small text-muted text-truncate" style="font-size:11.5px;">
                        ${item.modelName ? 'Model ' + escapeHtml(item.modelName) + ' &middot; ' : ''}${escapeHtml(item.variantName)}
                      </div>
                      <div class="small text-muted tc-price" style="font-size:11.5px;">${formatInr(item.sellingUnitPrice)} each</div>
                      ${item.deliveryEstimate ? renderDeliveryLine(item.deliveryEstimate, { className: 'tc-checkout-delivery-line' }) : ''}
                      ${!item.available ? `<div class="small text-danger fw-medium mt-1" style="font-size:11px;">Vault stock insufficient (${item.availableStock || 0} left)</div>` : ''}
                      <div class="d-flex align-items-center justify-content-between mt-2 gap-2">
                        <div class="tc-bag-stepper ${isSyncing ? 'is-syncing' : ''}" style="height:30px;grid-template-columns:28px minmax(32px,auto) 28px;">
                          <button type="button" class="tc-bag-step-btn" style="width:28px;height:28px;font-size:14px;" aria-label="Decrease quantity"
                                  onclick="TC.drawerStepQty(${item.id}, -1)">&minus;</button>
                          <span class="tc-bag-step-val" style="font-size:12px;padding:0 6px;">${item.quantity}</span>
                          <button type="button" class="tc-bag-step-btn" style="width:28px;height:28px;font-size:14px;" aria-label="Increase quantity"
                                  ${atMax ? 'disabled title="Maximum available stock reached"' : ''}
                                  onclick="TC.drawerStepQty(${item.id}, 1)">+</button>
                        </div>
                        <span class="tc-price fw-semibold small">${item.available ? formatInr(item.lineSubtotal) : 'Unavailable'}</span>
                      </div>
                      ${isConfirming ? `
                      <div class="tc-bag-remove-confirm mt-2">
                        <span>Remove timepiece?</span>
                        <button type="button" class="tc-bag-confirm-btn" onclick="TC.drawerRemoveItem(${item.id})">Remove</button>
                        <button type="button" class="tc-bag-cancel-btn" onclick="TC.cancelRemoveConfirmation(${item.id})">Keep</button>
                      </div>` : ''}
                    </div>
                  </div>`;
                }).join('')}
              </div>`;

            bodyEl.scrollTop = prevScrollTop;

            const taxLineLabel = t.taxInclusive
                ? `Included ${escapeHtml(t.taxLabel || 'GST')} (${t.taxRatePercent !== undefined ? t.taxRatePercent : 18}%)`
                : `${escapeHtml(t.taxLabel || 'GST')} (${t.taxRatePercent !== undefined ? t.taxRatePercent : 18}%)`;

            footEl.innerHTML = `
              <div class="tc-eyebrow mb-2" style="font-size:9.5px;">Order Summary</div>
              <div class="tc-drawer-summary-rows small tc-price">
                <div class="d-flex justify-content-between text-muted">
                  <span>Subtotal (${cart.totalQuantity} ${cart.totalQuantity === 1 ? 'piece' : 'pieces'})</span>
                  <span class="text-dark fw-medium">${formatInr(t.subtotal)}</span>
                </div>
                ${Number(t.discountTotal) > 0 ? `
                <div class="d-flex justify-content-between" style="color:var(--tc-gold);">
                  <span>Privilege${cart.appliedCouponCode ? ' (' + escapeHtml(cart.appliedCouponCode) + ')' : ''}</span>
                  <span>&minus; ${formatInr(t.discountTotal)}</span>
                </div>` : ''}
                <div class="d-flex justify-content-between text-muted">
                  <span>${taxLineLabel}</span>
                  <span>${formatInr(t.taxTotal || 0)}</span>
                </div>
                <div class="d-flex justify-content-between text-muted">
                  <span>Insured Delivery</span>
                  <span class="${Number(t.shippingTotal) === 0 ? 'text-dark fw-medium' : ''}">${Number(t.shippingTotal) === 0 ? 'Complimentary' : formatInr(t.shippingTotal)}</span>
                </div>
                <div class="d-flex justify-content-between border-top pt-2 mt-1 fs-5 fw-semibold text-dark">
                  <span>Payable Total</span>
                  <span>${formatInr(t.grandTotal)}</span>
                </div>
              </div>
              <div class="d-grid gap-2">
                ${hasUnavailable
                    ? `<button type="button" class="btn-tc-primary w-100" disabled>Adjust Stock to Proceed</button>`
                    : `<a href="/pages/checkout.html" class="btn-tc-primary w-100">Proceed to Checkout</a>`}
                <a href="/pages/cart.html" class="btn-tc-secondary w-100 py-2" onclick="TC.closeBagDrawer()">Open Full Shopping Bag</a>
              </div>`;
        } catch (err) {
            bodyEl.innerHTML = `<div class="alert alert-danger rounded-0 small">${escapeHtml(err.message)}</div>`;
        }
    }

    function drawerStepQty(itemId, delta) {
        stepCartItemQuantity(itemId, delta);
    }

    async function drawerUpdateQty(itemId, newQty) {
        const activeCart = viewCartState || serverCartSnapshot;
        const current = activeCart && Array.isArray(activeCart.items)
            ? activeCart.items.find(x => Number(x.id) === Number(itemId))
            : null;
        if (newQty <= 0) {
            requestRemoveConfirmation(itemId);
            return;
        }
        if (current) {
            stepCartItemQuantity(itemId, Number(newQty) - Number(current.quantity));
        }
    }

    async function drawerRemoveItem(itemId) {
        await removeCartItem(itemId);
    }

    async function updateHeaderBadges(prefetchedCart) {
        const compareBadge = document.getElementById('tcCompareCount');
        if (compareBadge) compareBadge.textContent = String(getCompareIds().length);

        const wishBadge = document.getElementById('tcWishlistCount');
        if (wishBadge) wishBadge.textContent = String(getWishlistProductIds().length);

        const cartBadge = document.getElementById('tcCartCount');
        if (!cartBadge && !prefetchedCart) return;
        try {
            if (prefetchedCart) {
                commitServerCart(prefetchedCart, { broadcast: false, source: 'badge-prefetch' });
            } else if (viewCartState) {
                applyBagCountDom(viewCartState.totalQuantity);
            } else {
                const cart = await api('/cart', { skipCartAutoCommit: true });
                commitServerCart(cart, { broadcast: false, source: 'badge-init' });
            }
        } catch {
            applyBagCountDom(0);
        }
    }

    async function mountChrome(activeNav = '') {
        if (!document.querySelector('link[rel="icon"]')) {
            const fav = document.createElement('link');
            fav.rel = 'icon';
            fav.type = 'image/svg+xml';
            fav.href = '/images/favicon.svg';
            document.head.appendChild(fav);
        }
        const headerHost = document.getElementById('tcHeader');
        const footerHost = document.getElementById('tcFooter');
        const user = getUser();

        if (headerHost) {
            headerHost.innerHTML = `
            <div class="tc-announcement" id="tcAnnouncementBar">
              Authorised Horological Maisons &middot; Complimentary Insured Vault Delivery Above ₹10,000
            </div>
            <header class="tc-navbar" id="tcMainNavbar">
              <div class="container tc-header-grid">
                <!-- Left Column: Curated Primary Navigation -->
                <nav class="tc-header-left" aria-label="Primary Navigation">
                  <button type="button" class="tc-icon-btn d-lg-none" onclick="TC.openMobileNav()" aria-label="Open Menu" title="Menu">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M4 7h16M4 12h16M4 17h16" stroke-linecap="round"/></svg>
                  </button>
                  <a href="/pages/shop.html" class="tc-nav-link ${activeNav === 'shop' ? 'active' : ''}">Timepieces</a>
                  <a href="/pages/brands.html" class="tc-nav-link ${activeNav === 'brands' ? 'active' : ''}">Maisons</a>
                  <a href="/pages/shop.html?minPrice=80000" class="tc-nav-link tc-desktop-nav">Haute Horlogerie</a>
                  <a href="/pages/shop.html?gender=WOMEN" class="tc-nav-link tc-nav-hide-md">Women's</a>
                  <a href="/pages/about.html" class="tc-nav-link tc-nav-hide-md ${activeNav === 'about' ? 'active' : ''}">Atelier</a>
                </nav>

                <!-- Center Column: TIMECRAFT Crest & Wordmark -->
                <div class="tc-header-center">
                  <a href="/index.html" class="tc-logo" aria-label="TimeCraft Home">
                    <img src="/images/logo-mark.svg" alt="" class="tc-logo-mark">
                    <span>TimeCraft</span>
                  </a>
                </div>

                <!-- Right Column: Precision Horological Utility Icons -->
                <div class="tc-header-right">
                  <button type="button" class="tc-icon-btn" onclick="TC.openSearchOverlay()" title="Search Archive" aria-label="Search">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="11" cy="11" r="6.8"/><path d="M19.5 19.5L16 16" stroke-linecap="round"/></svg>
                    <span class="tc-icon-label">Search</span>
                  </button>
                  <a href="/pages/compare.html" class="tc-icon-btn d-none d-sm-inline-flex ${activeNav === 'compare' ? 'active' : ''}" title="Compare Timepieces" aria-label="Compare Timepieces">
                    ${COMPARE_SVG}
                    <span class="tc-badge-count" id="tcCompareCount">0</span>
                  </a>
                  <a href="/pages/wishlist.html" class="tc-icon-btn d-none d-sm-inline-flex ${activeNav === 'wishlist' ? 'active' : ''}" title="Saved Wishlist" aria-label="Wishlist">
                    ${HEART_SVG}
                    <span class="tc-badge-count" id="tcWishlistCount">0</span>
                  </a>
                  <a href="${user ? '/pages/profile.html' : '/pages/login.html'}" class="tc-icon-btn ${activeNav === 'profile' ? 'active' : ''}" title="Collector Account" aria-label="Account">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="8" r="3.8"/><path d="M4.5 19.5c1.8-3.4 4.7-4.8 7.5-4.8s5.7 1.4 7.5 4.8" stroke-linecap="round"/></svg>
                    <span class="tc-icon-label">${user ? escapeHtml(user.firstName) : 'Account'}</span>
                  </a>
                  <button type="button" class="tc-icon-btn tc-bag-trigger" onclick="TC.openBagDrawer()" title="Shopping Bag" aria-label="Shopping Bag">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M6.2 7.5h11.6l.9 12.5H5.3l.9-12.5z"/><path d="M9 7.5V5.8a3 3 0 0 1 6 0v1.7"/></svg>
                    <span class="tc-icon-label">Bag</span>
                    <span class="tc-badge-count" id="tcCartCount">0</span>
                  </button>
                </div>
              </div>
            </header>`;

            const navEl = document.getElementById('tcMainNavbar');
            if (navEl && !window.__tcNavScrollBound) {
                window.__tcNavScrollBound = true;
                window.addEventListener('scroll', () => {
                    const n = document.getElementById('tcMainNavbar');
                    if (n) n.classList.toggle('is-scrolled', window.scrollY > 24);
                }, { passive: true });
            }
        }

        if (footerHost) {
            footerHost.innerHTML = `
            <footer class="tc-footer">
              <div class="container">
                <div class="row g-4 align-items-center pb-5 mb-5" style="border-bottom:1px solid rgba(248,246,241,0.12);">
                  <div class="col-lg-6">
                    <div class="tc-eyebrow mb-1" style="color:var(--tc-gold-light);">The Horological Journal</div>
                    <h2 class="tc-serif text-white fs-2 mb-1">Private Dispatches &amp; New Calibre Releases</h2>
                    <p class="small mb-0" style="color:rgba(248,246,241,0.65);">
                      Receive invitations to limited maison allocations, collector essays, and private atelier previews.
                    </p>
                  </div>
                  <div class="col-lg-6">
                    <form id="tcFooterNewsletterForm" class="d-flex flex-wrap gap-2">
                      <input type="email" id="tcFooterNewsletterEmail" class="tc-newsletter-input" required placeholder="Enter your email address" aria-label="Email address for newsletter">
                      <button type="submit" class="btn-tc-gold">Subscribe</button>
                    </form>
                  </div>
                </div>

                <div class="row g-4 pb-5" style="border-bottom:1px solid rgba(248,246,241,0.12);">
                  <div class="col-lg-4">
                    <div class="d-flex align-items-center gap-2 mb-3">
                      <img src="/images/favicon.svg" alt="TimeCraft" style="width:28px;height:28px;">
                      <span class="tc-serif text-white fs-3" style="letter-spacing:0.2em;">TIMECRAFT</span>
                    </div>
                    <p class="small mb-3" style="color:rgba(248,246,241,0.7);max-width:340px;">
                      Authorised destination for Swiss, European, Japanese and pioneering Indian watchmaking. Every timepiece is serial-verified, regulated across five positions, and dispatched via insured vault courier.
                    </p>
                    <div class="small" style="color:rgba(248,246,241,0.62);">
                      Concierge Desk: <span id="tcFooterEmail" class="text-white">support@timecraft.example</span><br>
                      Private Line: <span id="tcFooterPhone" class="tc-num text-white">+91 80 4000 1234</span>
                    </div>
                  </div>
                  <div class="col-6 col-lg-2">
                    <div class="tc-footer-heading">Collections</div>
                    <ul class="list-unstyled vstack gap-2 small mb-0">
                      <li><a href="/pages/shop.html?minPrice=80000">Haute Horlogerie</a></li>
                      <li><a href="/pages/shop.html?gender=MEN">Men's Timepieces</a></li>
                      <li><a href="/pages/shop.html?gender=WOMEN">Women's Timepieces</a></li>
                      <li><a href="/pages/category.html?slug=chronograph">Chronographs</a></li>
                      <li><a href="/pages/category.html?slug=dive">Dive Instruments</a></li>
                      <li><a href="/pages/brands.html">Partner Maisons</a></li>
                    </ul>
                  </div>
                  <div class="col-6 col-lg-3">
                    <div class="tc-footer-heading">Client Concierge</div>
                    <ul class="list-unstyled vstack gap-2 small mb-0">
                      <li><a href="/pages/orders.html">Collector Order Archive</a></li>
                      <li><a href="/pages/order-tracking.html">Track Insured Dispatch</a></li>
                      <li><a href="/pages/return-request.html">7-Day Inspection Returns</a></li>
                      <li><a href="/pages/reviews.html">Verified Collector Chronicles</a></li>
                      <li><a href="/pages/faq.html">Horological Care &amp; FAQ</a></li>
                      <li><a href="/pages/contact.html">Book Private Consultation</a></li>
                    </ul>
                  </div>
                  <div class="col-lg-3">
                    <div class="tc-footer-heading">Maison &amp; Governance</div>
                    <ul class="list-unstyled vstack gap-2 small mb-0">
                      <li><a href="/pages/about.html">The TimeCraft Manifesto</a></li>
                      <li><a href="/pages/privacy-policy.html">Client Privacy Charter</a></li>
                      <li><a href="/pages/terms.html">Terms of Sale &amp; Provenance</a></li>
                      <li><a href="/admin/login">Atelier Administration</a></li>
                    </ul>
                  </div>
                </div>
                <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 pt-4 small" style="color:rgba(248,246,241,0.52);">
                  <span>&copy; ${new Date().getFullYear()} TimeCraft Fine Horology. All rights reserved.</span>
                  <span>Authorised Retailer &middot; Values in INR</span>
                </div>
              </div>
            </footer>`;

            document.getElementById('tcFooterNewsletterForm')?.addEventListener('submit', async (ev) => {
                ev.preventDefault();
                const input = document.getElementById('tcFooterNewsletterEmail');
                const email = input ? input.value.trim() : '';
                if (!email) return;
                try {
                    await api('/store/newsletter', { method: 'POST', body: { email } });
                    toast('Subscribed to The Horological Journal');
                    if (input) input.value = '';
                } catch (err) {
                    toast(err.message || 'Unable to subscribe right now', 'error');
                }
            });
        }

        ensureGlobalOverlays();
        updateHeaderBadges();
        renderCompareDock();
        initScrollAnimations();

        if (user) {
            api('/wishlist').then(list => {
                setWishlistProductIds((list || []).map(i => i.productId));
            }).catch(() => {});
        }

        function applyStoreConfigToDom(cfg) {
            if (!cfg) return;
            const ann = document.getElementById('tcAnnouncementBar');
            if (ann && cfg['content.announcement']) ann.textContent = cfg['content.announcement'];
            const em = document.getElementById('tcFooterEmail');
            if (em && cfg['store.support_email']) em.textContent = cfg['store.support_email'];
            const ph = document.getElementById('tcFooterPhone');
            if (ph && cfg['store.support_phone']) ph.textContent = cfg['store.support_phone'];
            window.TC_STORE_CONFIG = cfg;
        }

        // Cache /store/config in sessionStorage (60s TTL) to eliminate redundant page-load calls
        let cachedStoreCfg = null;
        try {
            const raw = sessionStorage.getItem('tc_store_cfg_cache');
            if (raw) {
                const parsed = JSON.parse(raw);
                if (parsed && parsed.data && (Date.now() - Number(parsed.ts || 0)) < 60000) {
                    cachedStoreCfg = parsed.data;
                }
            }
        } catch {}

        if (cachedStoreCfg) {
            applyStoreConfigToDom(cachedStoreCfg);
        } else {
            api('/store/config').then(cfg => {
                if (cfg) {
                    applyStoreConfigToDom(cfg);
                    try {
                        sessionStorage.setItem('tc_store_cfg_cache', JSON.stringify({ ts: Date.now(), data: cfg }));
                    } catch {}
                }
            }).catch(() => {});
        }
    }

    function initScrollAnimations() {
        const targets = document.querySelectorAll('.tc-section, .tc-section-stone, .tc-watch-card, .tc-panel');
        if (!('IntersectionObserver' in window)) {
            targets.forEach(el => el.classList.add('tc-visible'));
            return;
        }
        const obs = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('tc-visible');
                    obs.unobserve(entry.target);
                }
            });
        }, { threshold: 0.08 });
        targets.forEach((el, idx) => {
            el.classList.add('tc-reveal');
            el.style.transitionDelay = ((idx % 4) * 45) + 'ms';
            obs.observe(el);
        });
    }

    const GOOGLE_G_SVG = `
      <svg width="18" height="18" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
        <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
        <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
        <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
        <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
      </svg>`;

    function loadGoogleGsiScript() {
        if (window.google && window.google.accounts) return Promise.resolve();
        return new Promise((resolve, reject) => {
            const existing = document.querySelector('script[src="https://accounts.google.com/gsi/client"]');
            if (existing) {
                existing.addEventListener('load', () => resolve());
                existing.addEventListener('error', () => reject(new Error('Failed to load Google Identity script')));
                return;
            }
            const s = document.createElement('script');
            s.src = 'https://accounts.google.com/gsi/client';
            s.async = true;
            s.defer = true;
            s.onload = () => resolve();
            s.onerror = () => reject(new Error('Failed to load Google Identity script'));
            document.head.appendChild(s);
        });
    }

    let publicConfigPromise = null;
    function getPublicAuthConfig() {
        if (!publicConfigPromise) {
            publicConfigPromise = api('/auth/public-config').catch(() => {
                publicConfigPromise = null;
                return null;
            });
        }
        return publicConfigPromise;
    }

    async function mountGoogleAuthButton(hostId, redirectUrl = '/pages/profile.html') {
        const host = document.getElementById(hostId);
        if (!host) return;
        let googleClientId = '';
        try {
            const cfg = await getPublicAuthConfig();
            googleClientId = (cfg && (cfg.googleClientId || cfg.clientId) || '').trim();
        } catch {}

        host.innerHTML = `
          <button type="button" class="btn-tc-google" id="${hostId}_btn">
            <span class="tc-google-icon-wrap" aria-hidden="true">${GOOGLE_G_SVG}</span>
            <span>Continue with Google</span>
          </button>`;

        document.getElementById(hostId + '_btn').addEventListener('click', async () => {
            if (!googleClientId) {
                toast('Google Sign-In is currently unavailable. Please sign in with your email and password.', 'error');
                return;
            }
            try {
                await loadGoogleGsiScript();
                const tokenClient = window.google.accounts.oauth2.initTokenClient({
                    client_id: googleClientId,
                    scope: 'openid email profile',
                    prompt: 'select_account',
                    error_callback: (err) => {
                        const msg = err && err.type === 'popup_closed'
                            ? 'Google sign-in popup was closed.'
                            : 'Google Sign-In was blocked for this domain (' + window.location.origin + '). Please add this origin in Google Cloud Console or sign in with email & password.';
                        toast(msg, 'error');
                    },
                    callback: async (tokenResponse) => {
                        if (tokenResponse && tokenResponse.error) {
                            toast('Google Sign-In failed: ' + tokenResponse.error, 'error');
                            return;
                        }
                        if (tokenResponse && tokenResponse.access_token) {
                            try {
                                const authData = await api('/auth/google', {
                                    method: 'POST',
                                    body: { credential: tokenResponse.access_token }
                                });
                                setAuthSession(authData);
                                toast('Signed in with Google as ' + authData.user.email);
                                window.location.href = redirectUrl;
                            } catch (err) {
                                toast(err.message, 'error');
                            }
                        }
                    }
                });
                tokenClient.requestAccessToken();
            } catch (err) {
                toast('Google Sign-In is currently unavailable. Please sign in with your email and password.', 'error');
            }
        });
    }

    function openMobileNav() {
        let backdrop = document.getElementById('tcMobileNavBackdrop');
        let drawer = document.getElementById('tcMobileNavDrawer');
        if (!backdrop) {
            backdrop = document.createElement('div');
            backdrop.id = 'tcMobileNavBackdrop';
            backdrop.className = 'tc-overlay-backdrop';
            backdrop.addEventListener('click', closeMobileNav);
            document.body.appendChild(backdrop);
        }
        if (!drawer) {
            const user = getUser();
            drawer = document.createElement('aside');
            drawer.id = 'tcMobileNavDrawer';
            drawer.className = 'tc-drawer tc-drawer-left';
            drawer.style.maxWidth = '310px';
            drawer.innerHTML = `
              <div class="tc-drawer-header">
                <a href="/index.html" class="tc-logo" style="font-size:20px;">
                  <img src="/images/logo-mark.svg" alt="TimeCraft" class="tc-logo-mark" style="width:24px;height:24px;">
                  <span>TimeCraft</span>
                </a>
                <button type="button" class="btn-close" aria-label="Close Menu" onclick="TC.closeMobileNav()"></button>
              </div>
              <div class="tc-drawer-body">
                <nav class="vstack">
                  <div class="tc-mobile-nav-group">Collections</div>
                  <a href="/pages/shop.html" class="tc-mobile-nav-link"><span>All Timepieces</span><span>&rarr;</span></a>
                  <a href="/pages/shop.html?minPrice=80000" class="tc-mobile-nav-link"><span>Haute Horlogerie</span><span>&rarr;</span></a>
                  <a href="/pages/shop.html?gender=MEN" class="tc-mobile-nav-link"><span>Men's Watches</span><span>&rarr;</span></a>
                  <a href="/pages/shop.html?gender=WOMEN" class="tc-mobile-nav-link"><span>Women's Watches</span><span>&rarr;</span></a>
                  <a href="/pages/brands.html" class="tc-mobile-nav-link"><span>Partner Maisons</span><span>&rarr;</span></a>
                  <div class="tc-mobile-nav-group">Specialisations</div>
                  <a href="/pages/category.html?slug=dress" class="tc-mobile-nav-link"><span>Dress &amp; Complication</span><span>&rarr;</span></a>
                  <a href="/pages/category.html?slug=chronograph" class="tc-mobile-nav-link"><span>Chronographs</span><span>&rarr;</span></a>
                  <a href="/pages/category.html?slug=dive" class="tc-mobile-nav-link"><span>Dive Instruments</span><span>&rarr;</span></a>
                  <a href="/pages/about.html" class="tc-mobile-nav-link"><span>The Atelier</span><span>&rarr;</span></a>
                  <div class="tc-mobile-nav-group">Private Vault</div>
                  <a href="/pages/wishlist.html" class="tc-mobile-nav-link"><span>Saved Wishlist</span><span>&rarr;</span></a>
                  <a href="/pages/compare.html" class="tc-mobile-nav-link"><span>Compare Timepieces</span><span>&rarr;</span></a>
                  <a href="${user ? '/pages/profile.html' : '/pages/login.html'}" class="tc-mobile-nav-link"><span>${user ? escapeHtml(user.firstName) + ' · Account' : 'Sign In / Register'}</span><span>&rarr;</span></a>
                </nav>
              </div>`;
            document.body.appendChild(drawer);
        }
        requestAnimationFrame(() => {
            backdrop.classList.add('open');
            drawer.classList.add('open');
            syncBodyDrawerLock();
        });
    }

    function closeMobileNav() {
        document.getElementById('tcMobileNavBackdrop')?.classList.remove('open');
        document.getElementById('tcMobileNavDrawer')?.classList.remove('open');
        syncBodyDrawerLock();
    }

    function toPaiseBigInt(amount) {
        if (amount === null || amount === undefined || amount === '') return 0n;
        const clean = String(amount).trim().replace(/,/g, '');
        const match = clean.match(/^(-?\d+)(?:\.(\d+))?$/);
        if (!match) {
            const n = Math.round(Number(clean) * 100);
            return BigInt(Number.isFinite(n) ? n : 0);
        }
        const whole = BigInt(match[1]);
        const fracStr = (match[2] || '').padEnd(2, '0').slice(0, 2);
        const frac = BigInt(fracStr || '0');
        return whole < 0n ? (whole * 100n - frac) : (whole * 100n + frac);
    }

    function getMaxCodAmount() {
        const cfg = window.TC_STORE_CONFIG || {};
        return cfg.max_cod_amount || cfg.cod_max_amount || '500000.00';
    }

    function isOrderCodEligible(grandTotal, maxCodOverride) {
        const cap = maxCodOverride !== undefined && maxCodOverride !== null ? maxCodOverride : getMaxCodAmount();
        return toPaiseBigInt(grandTotal) <= toPaiseBigInt(cap);
    }

    function ensureRazorpayScriptLoaded() {
        if (typeof window.Razorpay !== 'undefined') return Promise.resolve(true);
        return new Promise((resolve) => {
            const existing = document.querySelector('script[src="https://checkout.razorpay.com/v1/checkout.js"]');
            if (existing) {
                existing.addEventListener('load', () => resolve(typeof window.Razorpay !== 'undefined'), { once: true });
                existing.addEventListener('error', () => resolve(false), { once: true });
                setTimeout(() => resolve(typeof window.Razorpay !== 'undefined'), 3500);
                return;
            }
            const s = document.createElement('script');
            s.src = 'https://checkout.razorpay.com/v1/checkout.js';
            s.async = true;
            s.onload = () => resolve(typeof window.Razorpay !== 'undefined');
            s.onerror = () => resolve(false);
            document.head.appendChild(s);
        });
    }

    function openSandboxGatewayModal(order, paymentOrder, callbacks) {
        const existing = document.getElementById('tcRzpSandboxModal');
        if (existing) existing.remove();

        const wrap = document.createElement('div');
        wrap.id = 'tcRzpSandboxModal';
        wrap.className = 'tc-google-modal-backdrop';
        wrap.innerHTML = `
          <div class="tc-google-modal" style="max-width:440px;padding:0;overflow:hidden;">
            <div style="background:#1A1A1A;color:#FAF9F6;padding:18px 22px;" class="d-flex justify-content-between align-items-center gap-2">
              <div>
                <div style="font-size:10px;letter-spacing:0.16em;text-transform:uppercase;color:#B08D57;">Razorpay Test Mode Gateway</div>
                <div class="tc-serif fs-4">TimeCraft Fine Horology</div>
                <div class="small text-white-50 tc-num">Order ${escapeHtml(paymentOrder.orderNumber)} · ${escapeHtml(paymentOrder.razorpayOrderId)}</div>
              </div>
              <div class="text-end">
                <div class="fs-5 fw-bold tc-price">${formatInr(paymentOrder.amount)}</div>
                <span class="badge bg-warning text-dark rounded-0" style="font-size:10px;">TEST MODE</span>
              </div>
            </div>

            <div style="padding:22px;">
              <label class="form-label small text-uppercase fw-semibold">Select Payment Instrument</label>
              <div class="vstack gap-2 mb-3">
                <label class="tc-google-account-pill">
                  <input type="radio" name="rzpMethod" value="UPI" checked>
                  <div>
                    <div class="fw-semibold small">UPI (GPay / PhonePe / BHIM)</div>
                    <div class="text-muted" style="font-size:12px;">Instant UPI VPA authorization · success@razorpay</div>
                  </div>
                </label>
                <label class="tc-google-account-pill">
                  <input type="radio" name="rzpMethod" value="CARD">
                  <div>
                    <div class="fw-semibold small">Credit / Debit Card</div>
                    <div class="text-muted" style="font-size:12px;">Visa / Mastercard Test Card ·••• 4242</div>
                  </div>
                </label>
                <label class="tc-google-account-pill">
                  <input type="radio" name="rzpMethod" value="NETBANKING">
                  <div>
                    <div class="fw-semibold small">Netbanking</div>
                    <div class="text-muted" style="font-size:12px;">HDFC / ICICI / SBI Test Bank</div>
                  </div>
                </label>
              </div>

              <div class="d-grid gap-2">
                <button type="button" id="rzpSimSuccessBtn" class="btn-tc-primary w-100">
                  Authorize &amp; Pay ${formatInr(paymentOrder.amount)} (HMAC Verified)
                </button>
                <div class="d-flex flex-wrap gap-2">
                  <button type="button" id="rzpSimFailBtn" class="btn-tc-secondary flex-grow-1 text-danger">
                    Simulate Bank Decline
                  </button>
                  <button type="button" id="rzpSimCancelBtn" class="btn-tc-secondary flex-grow-1">
                    Cancel
                  </button>
                </div>
              </div>
              <div class="text-muted mt-3" style="font-size:11px;">
                Generates a signed <code>razorpay_payment_id</code> + HMAC-SHA256 <code>razorpay_signature</code> and verifies it through <code>POST /api/v1/payments/verify</code>.
              </div>
            </div>
          </div>`;
        document.body.appendChild(wrap);

        document.getElementById('rzpSimSuccessBtn').addEventListener('click', async () => {
            const btn = document.getElementById('rzpSimSuccessBtn');
            btn.disabled = true;
            btn.textContent = 'Verifying HMAC-SHA256 Signature…';
            const method = document.querySelector('input[name="rzpMethod"]:checked')?.value || 'UPI';
            try {
                const signed = await api('/payments/sandbox-authorize', {
                    method: 'POST',
                    body: {
                        orderId: order.id,
                        razorpayOrderId: paymentOrder.razorpayOrderId,
                        paymentMethod: method
                    }
                });
                const verifiedOrder = await api('/payments/verify', {
                    method: 'POST',
                    body: {
                        orderId: order.id,
                        razorpayOrderId: signed.razorpayOrderId,
                        razorpayPaymentId: signed.razorpayPaymentId,
                        razorpaySignature: signed.razorpaySignature,
                        paymentMethod: signed.paymentMethod
                    }
                });
                wrap.remove();
                try { sessionStorage.removeItem('tc_pending_order'); } catch {}
                if (callbacks && typeof callbacks.onSuccess === 'function') {
                    callbacks.onSuccess(verifiedOrder || order);
                } else {
                    window.location.href = '/pages/order-confirmation.html?order=' + encodeURIComponent(order.orderNumber);
                }
            } catch (err) {
                wrap.remove();
                if (callbacks && typeof callbacks.onFailure === 'function') {
                    callbacks.onFailure({ order, status: 'failed', reason: err.message || 'Payment verification failed', cancelled: false });
                }
            }
        });

        document.getElementById('rzpSimFailBtn').addEventListener('click', async () => {
            const method = document.querySelector('input[name="rzpMethod"]:checked')?.value || 'ONLINE';
            const reason = method + ' payment declined by issuing bank in Razorpay Test Mode';
            let updatedOrder = order;
            try {
                updatedOrder = await api('/payments/failure', {
                    method: 'POST',
                    body: {
                        orderId: order.id,
                        razorpayOrderId: paymentOrder.razorpayOrderId,
                        reason,
                        cancelled: false
                    }
                });
            } catch {}
            wrap.remove();
            if (callbacks && typeof callbacks.onFailure === 'function') {
                callbacks.onFailure({ order: updatedOrder || order, status: 'failed', reason, cancelled: false });
            }
        });

        document.getElementById('rzpSimCancelBtn').addEventListener('click', async () => {
            const reason = 'Payment window was closed before completing authorization';
            let updatedOrder = order;
            try {
                updatedOrder = await api('/payments/failure', {
                    method: 'POST',
                    body: {
                        orderId: order.id,
                        razorpayOrderId: paymentOrder.razorpayOrderId,
                        reason,
                        cancelled: true
                    }
                });
            } catch {}
            wrap.remove();
            if (callbacks && typeof callbacks.onFailure === 'function') {
                callbacks.onFailure({ order: updatedOrder || order, status: 'cancelled', reason, cancelled: true });
            }
        });
    }

    async function launchOrderRazorpayPayment(order, options = {}) {
        const isRetry = Boolean(options.retry);
        const paymentOrder = await api('/payments/create', {
            method: 'POST',
            body: { orderId: order.id, retry: isRetry }
        });

        if (paymentOrder.sandboxMode) {
            openSandboxGatewayModal(order, paymentOrder, options);
            return paymentOrder;
        }

        const scriptReady = await ensureRazorpayScriptLoaded();
        if (!scriptReady || typeof window.Razorpay === 'undefined') {
            const reason = 'Razorpay checkout gateway could not be reached. Please check your connection and retry.';
            if (typeof options.onFailure === 'function') {
                options.onFailure({ order, status: 'failed', reason, cancelled: false });
            }
            return paymentOrder;
        }

        const cleanContact = (function (raw) {
            const digits = String(raw || '').replace(/\D/g, '');
            const last10 = digits.length >= 10 ? digits.slice(-10) : '';
            return /^[6-9]\d{9}$/.test(last10) ? last10 : '9876543210';
        })(paymentOrder.customerPhone);

        let settled = false;
        let rzpInstance = null;

        rzpInstance = new window.Razorpay({
            key: paymentOrder.keyId || paymentOrder.razorpayKeyId,
            amount: paymentOrder.amountInPaise || paymentOrder.amountInMinorUnits,
            currency: 'INR',
            name: 'TimeCraft Fine Horology',
            description: 'Order ' + paymentOrder.orderNumber,
            order_id: paymentOrder.razorpayOrderId,
            method: {
                upi: true,
                card: true,
                netbanking: true,
                wallet: true
            },
            prefill: {
                name: paymentOrder.customerName || 'TimeCraft Collector',
                email: paymentOrder.customerEmail || '',
                contact: cleanContact
            },
            theme: { color: '#1A1A1A' },
            handler: async function (response) {
                if (settled) return;
                settled = true;
                try {
                    const verifiedOrder = await api('/payments/verify', {
                        method: 'POST',
                        body: {
                            orderId: order.id,
                            razorpayOrderId: response.razorpay_order_id,
                            razorpayPaymentId: response.razorpay_payment_id,
                            razorpaySignature: response.razorpay_signature,
                            paymentMethod: 'RAZORPAY'
                        }
                    });
                    try { sessionStorage.removeItem('tc_pending_order'); } catch {}
                    if (typeof options.onSuccess === 'function') {
                        options.onSuccess(verifiedOrder || order);
                    } else {
                        window.location.href = '/pages/order-confirmation.html?order=' + encodeURIComponent(order.orderNumber);
                    }
                } catch (verifyErr) {
                    const reason = verifyErr.message || 'Payment verification failed';
                    toast('Payment verification failed: ' + reason, 'error');
                    if (typeof options.onFailure === 'function') {
                        options.onFailure({ order, status: 'failed', reason, cancelled: false });
                    }
                }
            },
            modal: {
                ondismiss: async function () {
                    if (settled) return;
                    settled = true;
                    const reason = 'Payment window was closed before completing authorization.';
                    let updatedOrder = order;
                    try {
                        updatedOrder = await api('/payments/failure', {
                            method: 'POST',
                            body: {
                                orderId: order.id,
                                razorpayOrderId: paymentOrder.razorpayOrderId,
                                reason,
                                cancelled: true
                            }
                        });
                    } catch (_) { /* keep order recoverable client-side */ }
                    if (typeof options.onFailure === 'function') {
                        options.onFailure({ order: updatedOrder || order, status: 'cancelled', reason, cancelled: true });
                    }
                }
            }
        });

        rzpInstance.on('payment.failed', async function (resp) {
            if (settled) return;
            settled = true;
            const errObj = (resp && resp.error) || {};
            const reason = errObj.description || errObj.reason || 'Payment was declined by the bank or gateway.';
            try { rzpInstance.close(); } catch (_) {}
            let updatedOrder = order;
            try {
                updatedOrder = await api('/payments/failure', {
                    method: 'POST',
                    body: {
                        orderId: order.id,
                        razorpayOrderId: paymentOrder.razorpayOrderId,
                        razorpayPaymentId: errObj.metadata ? errObj.metadata.payment_id : null,
                        reason,
                        cancelled: false
                    }
                });
            } catch (_) { /* keep order recoverable client-side */ }
            if (typeof options.onFailure === 'function') {
                options.onFailure({ order: updatedOrder || order, status: 'failed', reason, cancelled: false });
            }
        });

        rzpInstance.open();
        return paymentOrder;
    }

    async function switchOrderToCod(orderNumberOrId) {
        const updated = await api('/orders/' + encodeURIComponent(orderNumberOrId) + '/switch-to-cod', {
            method: 'POST'
        });
        try { sessionStorage.removeItem('tc_pending_order'); } catch {}
        return updated;
    }

    async function launchOrderPayuPayment(order, options = {}) {
        const isRetry = Boolean(options.retry);
        const initResp = await api('/payments/payu/initiate', {
            method: 'POST',
            body: { orderId: order.id, retry: isRetry }
        });

        try { sessionStorage.setItem('tc_pending_order', order.orderNumber); } catch {}

        const existingForm = document.getElementById('tcPayuRedirectForm');
        if (existingForm) existingForm.remove();

        const form = document.createElement('form');
        form.id = 'tcPayuRedirectForm';
        form.method = 'POST';
        form.action = initResp.actionUrl;
        form.style.display = 'none';

        const fields = {
            key: initResp.key,
            txnid: initResp.txnid,
            amount: initResp.amountFormatted || String(initResp.amount),
            productinfo: initResp.productinfo,
            firstname: initResp.firstname,
            email: initResp.email,
            phone: initResp.phone,
            udf1: initResp.udf1 || '',
            udf2: initResp.udf2 || '',
            udf3: initResp.udf3 || '',
            udf4: initResp.udf4 || '',
            udf5: initResp.udf5 || '',
            surl: initResp.surl,
            furl: initResp.furl,
            hash: initResp.hash
        };

        Object.entries(fields).forEach(([name, value]) => {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = name;
            input.value = value !== null && value !== undefined ? String(value) : '';
            form.appendChild(input);
        });

        document.body.appendChild(form);
        form.submit();
        return initResp;
    }

    /**
     * Synchronizes an analog mechanical watch SVG to exact live Asia/Kolkata (IST) time.
     * Uses mathematical hand angles around the exact dial center with 8 Hz mechanical sweep
     * (or 1 Hz discrete step under prefers-reduced-motion).
     */
    function initHorologicalClock(config = {}) {
        const timeZone = config.timeZone || 'Asia/Kolkata';
        const hourHand = document.getElementById(config.hourHandId || 'tcClockHourHand');
        const minuteHand = document.getElementById(config.minuteHandId || 'tcClockMinuteHand');
        const secondHand = document.getElementById(config.secondHandId || 'tcClockSecondHand');
        const dateEl = document.getElementById(config.dateTextId || 'tcClockDateNum');
        const digitalEl = document.getElementById(config.digitalReadoutId || 'tcClockDigitalIst');

        if (!hourHand || !minuteHand || !secondHand) return null;

        const reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        const istFormatter = new Intl.DateTimeFormat('en-GB', {
            timeZone,
            hour12: false,
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            day: '2-digit'
        });

        let timerId = null;

        function extractIstParts(now) {
            const parts = istFormatter.formatToParts(now);
            let h = 0, m = 0, s = 0, day = '01';
            for (const p of parts) {
                if (p.type === 'hour') h = Number(p.value) % 24;
                else if (p.type === 'minute') m = Number(p.value);
                else if (p.type === 'second') s = Number(p.value);
                else if (p.type === 'day') day = p.value;
            }
            return { h, m, s, day };
        }

        function updateDial() {
            const now = new Date();
            const { h, m, s, day } = extractIstParts(now);
            // Mechanical 28,800 vph (8 beats/second) smooth sweep, or exact 1s step for reduced motion
            const subSecond = reducedMotion ? 0 : (Math.floor((now.getMilliseconds() / 1000) * 8) / 8);
            const exactSeconds = s + subSecond;
            const exactMinutes = m + (exactSeconds / 60);
            const exactHours = (h % 12) + (exactMinutes / 60);

            const secondAngle = (exactSeconds * 6) % 360;
            const minuteAngle = (exactMinutes * 6) % 360;
            const hourAngle = (exactHours * 30) % 360;

            hourHand.setAttribute('transform', `rotate(${hourAngle.toFixed(2)})`);
            minuteHand.setAttribute('transform', `rotate(${minuteAngle.toFixed(2)})`);
            secondHand.setAttribute('transform', `rotate(${secondAngle.toFixed(2)})`);

            if (dateEl && dateEl.textContent !== day) {
                dateEl.textContent = day;
            }
            if (digitalEl) {
                const hh = String(h).padStart(2, '0');
                const mm = String(m).padStart(2, '0');
                const ss = String(s).padStart(2, '0');
                const nextText = `${hh}:${mm}:${ss} IST`;
                if (digitalEl.textContent !== nextText) {
                    digitalEl.textContent = nextText;
                }
            }
        }

        function start() {
            stop();
            updateDial();
            timerId = window.setInterval(updateDial, reducedMotion ? 1000 : 125);
        }

        function stop() {
            if (timerId !== null) {
                clearInterval(timerId);
                timerId = null;
            }
        }

        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                stop();
            } else {
                start();
            }
        });

        start();
        return { updateDial, stop, start };
    }

    window.TC = {
        api,
        formatInr,
        escapeHtml,
        getUser,
        setAuthSession,
        clearAuthSession,
        logout,
        toast,
        getCompareIds,
        toggleCompare,
        renderCompareDock,
        openCompareModal,
        openQuickView,
        openSearchOverlay,
        closeSearchOverlay,
        openBagDrawer,
        closeBagDrawer,
        openMobileNav,
        closeMobileNav,
        getCartState,
        getItemMaxAllowed,
        stepCartItemQuantity,
        removeCartItem,
        requestRemoveConfirmation,
        cancelRemoveConfirmation,
        drawerStepQty,
        drawerUpdateQty,
        drawerRemoveItem,
        recordRecentlyViewed,
        getRecentlyViewed,
        quickAddToCart,
        quickAddToWishlist,
        getEditorialLabel,
        renderWatchCard,
        updateHeaderBadges,
        mountChrome,
        initScrollAnimations,
        mountGoogleAuthButton,
        getMaxCodAmount,
        isOrderCodEligible,
        launchOrderRazorpayPayment,
        launchOrderPayuPayment,
        switchOrderToCod,
        initHorologicalClock,
        TRUCK_SVG,
        getSavedDeliveryPin,
        setSavedDeliveryPin,
        formatDeliveryDate,
        formatDeliveryDateRange,
        renderDeliveryLine
    };
})();
