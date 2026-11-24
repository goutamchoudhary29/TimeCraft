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
        RECENT: 'tc_recent_watches'
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

    function renderWatchCard(p, options = {}) {
        const outOfStock = (p.totalAvailableStock || 0) <= 0;
        const hasDiscount = Number(p.discountPercent || 0) > 0;
        const wishIds = getWishlistProductIds();
        const cmpIds = getCompareIds();
        const isWished = wishIds.includes(p.id);
        const isCompared = cmpIds.includes(p.id);

        if (p && p.id) {
            const metaMap = getCompareMeta();
            metaMap[p.id] = { id: p.id, name: p.name, slug: p.slug, brandName: p.brandName, primaryImageUrl: p.primaryImageUrl, sellingPrice: p.sellingPrice };
            try { localStorage.setItem(STORAGE_KEYS.COMPARE_META, JSON.stringify(metaMap)); } catch {}
        }

        const badge = outOfStock
            ? '<span class="tc-watch-tag">Sold Out</span>'
            : (hasDiscount
                ? `<span class="tc-watch-tag tc-watch-tag-sale">-${Math.round(p.discountPercent)}%</span>`
                : (p.newArrival ? '<span class="tc-watch-tag tc-watch-tag-gold">New Arrival</span>' : ''));

        const cardHtml = `
          <article class="tc-watch-card">
            <div class="tc-watch-media">
              ${badge}
              <div class="tc-card-FloatingActions">
                <button type="button" class="tc-card-icon-pill ${isWished ? 'active' : ''}"
                        data-wish-btn="${p.id}" title="Save to Wishlist"
                        onclick="TC.quickAddToWishlist(${p.id}, ${p.defaultVariantId || 'null'})">♥</button>
                <button type="button" class="tc-card-icon-pill ${isCompared ? 'active-gold' : ''}"
                        data-compare-btn="${p.id}" title="Compare Timepiece"
                        onclick="TC.toggleCompare(${p.id})">⇄</button>
              </div>
              <a href="/pages/product-details.html?slug=${encodeURIComponent(p.slug || p.id)}" class="d-block w-100 h-100">
                <img src="${escapeHtml(p.primaryImageUrl)}" alt="${escapeHtml(p.name)}" loading="lazy">
              </a>
              <button type="button" class="tc-quickview-bar"
                      onclick="TC.openQuickView('${escapeHtml(p.slug || p.id)}')">
                Quick View Specs
              </button>
            </div>
            <div class="tc-watch-body">
              <div>
                <div class="tc-watch-brand">${escapeHtml(p.brandName)}</div>
                <h3 class="tc-watch-title">
                  <a href="/pages/product-details.html?slug=${encodeURIComponent(p.slug || p.id)}">${escapeHtml(p.name)}</a>
                </h3>
                <div class="tc-watch-meta mb-2">
                  <span>${escapeHtml(p.movementType || 'AUTOMATIC')}</span>
                  <span>·</span>
                  <span>${escapeHtml(p.caseDiameterMm || 40)}mm</span>
                  <span>·</span>
                  <span>${escapeHtml(p.waterResistanceM || 50)}m</span>
                </div>
              </div>
              <div>
                <div class="d-flex align-items-baseline gap-2 mb-3 tc-price">
                  <span class="fw-semibold fs-6">${formatInr(p.sellingPrice)}</span>
                  ${hasDiscount ? `<span class="text-muted small text-decoration-line-through">${formatInr(p.regularPrice)}</span>` : ''}
                </div>
                <button type="button" class="btn-tc-primary w-100"
                        ${outOfStock || !p.defaultVariantId ? 'disabled' : ''}
                        onclick="TC.quickAddToCart(${p.defaultVariantId || 0}, 1, true)">
                  ${outOfStock ? 'Currently Unavailable' : 'Add to Bag'}
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
                <h2 class="tc-serif fs-3 mb-0">Your Shopping Bag</h2>
              </div>
              <button type="button" class="btn-close" id="tcBagCloseBtn"></button>
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

    async function openBagDrawer(prefetchedCart = null) {
        ensureGlobalOverlays();
        document.getElementById('tcBagBackdrop').classList.add('open');
        document.getElementById('tcBagDrawer').classList.add('open');
        await renderBagDrawer(prefetchedCart);
    }

    function closeBagDrawer() {
        document.getElementById('tcBagBackdrop')?.classList.remove('open');
        document.getElementById('tcBagDrawer')?.classList.remove('open');
    }

    async function renderBagDrawer(prefetchedCart = null) {
        const bodyEl = document.getElementById('tcBagDrawerBody');
        const footEl = document.getElementById('tcBagDrawerFooter');
        if (!bodyEl || !footEl) return;
        try {
            const cart = prefetchedCart || await api('/cart');
            updateHeaderBadges(cart);
            const t = cart.totals || { subtotal: 0, discountTotal: 0, shippingTotal: 0, grandTotal: 0 };
            const subtotal = Number(t.subtotal || 0);
            const pct = Math.min(100, Math.round((subtotal / FREE_SHIPPING_THRESHOLD) * 100));
            const remaining = Math.max(0, FREE_SHIPPING_THRESHOLD - subtotal);

            const shippingBanner = `
              <div class="tc-shipping-bar-wrap">
                <div class="d-flex justify-content-between small">
                  <span>${remaining === 0
                      ? '✓ <strong>Complimentary Insured Shipping</strong> unlocked'
                      : `Add <strong class="tc-price">${formatInr(remaining)}</strong> more for free insured shipping`}</span>
                  <span class="tc-num text-muted">${pct}%</span>
                </div>
                <div class="tc-shipping-progress">
                  <div class="tc-shipping-progress-fill" style="width:${pct}%;"></div>
                </div>
              </div>`;

            if (!cart.items || cart.items.length === 0) {
                bodyEl.innerHTML = shippingBanner + `
                  <div class="text-center py-5">
                    <div class="tc-serif fs-3 mb-1">Your bag is empty</div>
                    <p class="small text-muted mb-3">Explore our Swiss, Japanese and Indian horological collections.</p>
                    <a href="/pages/shop.html" class="btn-tc-primary" onclick="TC.closeBagDrawer()">Explore Timepieces</a>
                  </div>`;
                footEl.innerHTML = '';
                return;
            }

            bodyEl.innerHTML = shippingBanner + `
              <div class="vstack gap-3">
                ${cart.items.map(item => `
                  <div class="d-flex gap-3 border-bottom pb-3 align-items-center">
                    <img src="${escapeHtml(item.imageUrl)}" alt="" style="width:58px;height:72px;object-fit:cover;border:1px solid #E5E2DC;">
                    <div class="flex-grow-1 min-w-0">
                      <div class="tc-eyebrow">${escapeHtml(item.brandName)}</div>
                      <a href="/pages/product-details.html?slug=${encodeURIComponent(item.productSlug)}" class="tc-serif fs-5 d-block text-truncate">${escapeHtml(item.productName)}</a>
                      <div class="small text-muted text-truncate">${escapeHtml(item.variantName)}</div>
                      <div class="d-flex align-items-center justify-content-between mt-2">
                        <div class="tc-qty-stepper">
                          <button type="button" onclick="TC.drawerUpdateQty(${item.id}, ${item.quantity - 1})">&minus;</button>
                          <span>${item.quantity}</span>
                          <button type="button" onclick="TC.drawerUpdateQty(${item.id}, ${item.quantity + 1})">+</button>
                        </div>
                        <span class="tc-price fw-semibold small">${formatInr(item.lineSubtotal)}</span>
                      </div>
                    </div>
                    <button type="button" class="btn-close small" aria-label="Remove" onclick="TC.drawerRemoveItem(${item.id})"></button>
                  </div>`).join('')}
              </div>`;

            footEl.innerHTML = `
              <div class="small vstack gap-1 mb-3 tc-price">
                <div class="d-flex justify-content-between text-muted"><span>Subtotal</span><span>${formatInr(t.subtotal)}</span></div>
                ${Number(t.discountTotal) > 0 ? `<div class="d-flex justify-content-between text-success"><span>Coupon (${escapeHtml(cart.appliedCouponCode || '')})</span><span>- ${formatInr(t.discountTotal)}</span></div>` : ''}
                <div class="d-flex justify-content-between text-muted"><span>Insured Shipping</span><span>${Number(t.shippingTotal) === 0 ? 'Complimentary' : formatInr(t.shippingTotal)}</span></div>
                <div class="d-flex justify-content-between border-top pt-2 fs-5 fw-semibold text-dark">
                  <span>Total</span><span>${formatInr(t.grandTotal)}</span>
                </div>
              </div>
              <div class="d-grid gap-2">
                <a href="/pages/checkout.html" class="btn-tc-primary w-100">Proceed to Insured Checkout</a>
                <a href="/pages/cart.html" class="btn-tc-secondary w-100 py-2">View Full Shopping Bag</a>
              </div>`;
        } catch (err) {
            bodyEl.innerHTML = `<div class="alert alert-danger rounded-0 small">${escapeHtml(err.message)}</div>`;
        }
    }

    async function drawerUpdateQty(itemId, newQty) {
        if (newQty <= 0) return drawerRemoveItem(itemId);
        try {
            const cart = await api('/cart/items/' + itemId, { method: 'PUT', body: { quantity: newQty } });
            await renderBagDrawer(cart);
        } catch (err) {
            toast(err.message, 'error');
        }
    }

    async function drawerRemoveItem(itemId) {
        try {
            const cart = await api('/cart/items/' + itemId, { method: 'DELETE' });
            toast('Removed item from bag');
            await renderBagDrawer(cart);
        } catch (err) {
            toast(err.message, 'error');
        }
    }

    async function updateHeaderBadges(prefetchedCart) {
        const compareBadge = document.getElementById('tcCompareCount');
        if (compareBadge) compareBadge.textContent = String(getCompareIds().length);

        const wishBadge = document.getElementById('tcWishlistCount');
        if (wishBadge) wishBadge.textContent = String(getWishlistProductIds().length);

        const cartBadge = document.getElementById('tcCartCount');
        if (!cartBadge) return;
        try {
            const cart = prefetchedCart || await api('/cart');
            cartBadge.textContent = String((cart && cart.totalQuantity) || 0);
        } catch {
            cartBadge.textContent = '0';
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
              Complimentary insured shipping on orders above ₹10,000 · 7-day horological inspection returns
            </div>
            <header class="tc-navbar">
              <div class="container tc-header-grid">
                <!-- Left Column: Primary Navigation -->
                <nav class="tc-header-left" aria-label="Primary Navigation">
                  <a href="/pages/shop.html" class="tc-nav-link ${activeNav === 'shop' ? 'active' : ''}">Watches</a>
                  <a href="/pages/brands.html" class="tc-nav-link ${activeNav === 'brands' ? 'active' : ''}">Maisons</a>
                  <a href="/pages/category.html?slug=dive" class="tc-nav-link tc-desktop-nav ${activeNav === 'dive' ? 'active' : ''}">Dive</a>
                  <a href="/pages/category.html?slug=chronograph" class="tc-nav-link tc-desktop-nav ${activeNav === 'chronograph' ? 'active' : ''}">Chrono</a>
                  <a href="/pages/category.html?slug=dress" class="tc-nav-link tc-nav-hide-md ${activeNav === 'dress' ? 'active' : ''}">Dress</a>
                  <a href="/pages/about.html" class="tc-nav-link tc-nav-hide-md ${activeNav === 'about' ? 'active' : ''}">Maison</a>
                </nav>

                <!-- Center Column: TIMECRAFT Crest & Wordmark -->
                <div class="tc-header-center">
                  <a href="/index.html" class="tc-logo">
                    <img src="/images/logo-mark.svg" alt="TimeCraft Crest" class="tc-logo-mark">
                    <span>TimeCraft</span>
                  </a>
                </div>

                <!-- Right Column: Compact Action Icons -->
                <div class="tc-header-right">
                  <button type="button" class="tc-icon-btn" onclick="TC.openSearchOverlay()" title="Instant Search">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5" stroke-linecap="round"/></svg>
                    <span class="tc-icon-label">Search</span>
                  </button>
                  <a href="/pages/compare.html" class="tc-icon-btn ${activeNav === 'compare' ? 'active' : ''}" title="Compare Watches">
                    <span>⇄</span>
                    <span class="tc-badge-count" id="tcCompareCount">0</span>
                  </a>
                  <a href="/pages/wishlist.html" class="tc-icon-btn ${activeNav === 'wishlist' ? 'active' : ''}" title="Wishlist">
                    <span>♥</span>
                    <span class="tc-badge-count" id="tcWishlistCount">0</span>
                  </a>
                  <a href="${user ? '/pages/profile.html' : '/pages/login.html'}" class="tc-icon-btn ${activeNav === 'profile' ? 'active' : ''}" title="Account">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="8" r="4"/><path d="M4 20c1.8-3.5 5-5 8-5s6.2 1.5 8 5" stroke-linecap="round"/></svg>
                    <span class="tc-icon-label">${user ? escapeHtml(user.firstName) : 'Account'}</span>
                  </a>
                  <button type="button" class="tc-icon-btn tc-bag-trigger" onclick="TC.openBagDrawer()" title="Open Shopping Bag">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M6 7h12l1 13H5L6 7z"/><path d="M9 7V5a3 3 0 0 1 6 0v2"/></svg>
                    <span class="tc-icon-label">Bag</span>
                    <span class="tc-badge-count" id="tcCartCount">0</span>
                  </button>
                </div>
              </div>
            </header>`;
        }

        if (footerHost) {
            footerHost.innerHTML = `
            <footer class="tc-footer">
              <div class="container">
                <div class="row g-4 pb-4 border-bottom border-secondary">
                  <div class="col-lg-4">
                    <div class="d-flex align-items-center gap-2 mb-2">
                      <img src="/images/favicon.svg" alt="TimeCraft" style="width:32px;height:32px;">
                      <span class="tc-serif text-white fs-3" style="letter-spacing:0.14em;">TIMECRAFT</span>
                    </div>
                    <p class="small mb-3">
                      Curated mechanical, solar and high-precision quartz timepieces from Swiss, Japanese, European and Indian maisons. Every watch is inspected, serial-recorded and shipped insured.
                    </p>
                    <div class="small text-white-50">Concierge: <span id="tcFooterEmail">support@timecraft.example</span> · <span id="tcFooterPhone">+91 80 4000 1234</span></div>
                  </div>
                  <div class="col-6 col-lg-2">
                    <div class="tc-footer-heading">Collections</div>
                    <ul class="list-unstyled vstack gap-2 small">
                      <li><a href="/pages/shop.html">Shop All Watches</a></li>
                      <li><a href="/pages/products.html?featured=true">Featured Pieces</a></li>
                      <li><a href="/pages/products.html?newArrival=true">New Arrivals</a></li>
                      <li><a href="/pages/products.html?bestSeller=true">Best Sellers</a></li>
                      <li><a href="/pages/brands.html">Watch Maisons</a></li>
                    </ul>
                  </div>
                  <div class="col-6 col-lg-3">
                    <div class="tc-footer-heading">Client Care</div>
                    <ul class="list-unstyled vstack gap-2 small">
                      <li><a href="/pages/order-tracking.html">Track Shipment</a></li>
                      <li><a href="/pages/return-request.html">Returns &amp; Refunds</a></li>
                      <li><a href="/pages/reviews.html">Verified Client Reviews</a></li>
                      <li><a href="/pages/faq.html">Horological FAQ</a></li>
                      <li><a href="/pages/contact.html">Contact Concierge</a></li>
                    </ul>
                  </div>
                  <div class="col-lg-3">
                    <div class="tc-footer-heading">Legal &amp; Atelier</div>
                    <ul class="list-unstyled vstack gap-2 small">
                      <li><a href="/pages/about.html">About TimeCraft</a></li>
                      <li><a href="/pages/privacy-policy.html">Privacy Policy</a></li>
                      <li><a href="/pages/terms.html">Terms &amp; Conditions</a></li>
                      <li><a href="/admin/login">Admin Atelier Portal</a></li>
                    </ul>
                  </div>
                </div>
                <div class="d-flex flex-wrap justify-content-between align-items-center pt-4 small text-white-50">
                  <span>&copy; ${new Date().getFullYear()} TimeCraft Fine Horology. All rights reserved.</span>
                  <span>Prices in INR · Tabular precision</span>
                </div>
              </div>
            </footer>`;
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

        try {
            const cfg = await api('/store/config');
            if (cfg) {
                const ann = document.getElementById('tcAnnouncementBar');
                if (ann && cfg['content.announcement']) ann.textContent = cfg['content.announcement'];
                const em = document.getElementById('tcFooterEmail');
                if (em && cfg['store.support_email']) em.textContent = cfg['store.support_email'];
                const ph = document.getElementById('tcFooterPhone');
                if (ph && cfg['store.support_phone']) ph.textContent = cfg['store.support_phone'];
                window.TC_STORE_CONFIG = cfg;
            }
        } catch {}
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

    async function mountGoogleAuthButton(hostId, redirectUrl = '/pages/profile.html') {
        const host = document.getElementById(hostId);
        if (!host) return;
        let gConfig = { clientId: '', devQuickLoginEnabled: true };
        try {
            gConfig = await api('/auth/google-config');
        } catch {}

        host.innerHTML = `
          <button type="button" class="btn-tc-google" id="${hostId}_btn">
            ${GOOGLE_G_SVG}
            <span>Continue with Google</span>
          </button>`;

        document.getElementById(hostId + '_btn').addEventListener('click', async () => {
            const clientId = (gConfig.clientId && gConfig.clientId.trim())
                || (localStorage.getItem('tc_google_client_id') || '').trim();
            if (clientId) {
                try {
                    await loadGoogleGsiScript();
                    const tokenClient = window.google.accounts.oauth2.initTokenClient({
                        client_id: clientId,
                        scope: 'openid email profile',
                        prompt: 'select_account',
                        callback: async (tokenResponse) => {
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
                    return;
                } catch (err) {
                    toast(err.message, 'error');
                }
            }
            openGoogleAccountPickerModal(redirectUrl);
        });
    }

    function openGoogleAccountPickerModal(redirectUrl) {
        const existing = document.getElementById('tcGoogleModal');
        if (existing) existing.remove();

        const wrap = document.createElement('div');
        wrap.id = 'tcGoogleModal';
        wrap.className = 'tc-google-modal-backdrop';
        wrap.innerHTML = `
          <div class="tc-google-modal" style="max-width:460px;">
            <div class="d-flex align-items-center justify-content-between mb-3">
              <div class="d-flex align-items-center gap-2">
                ${GOOGLE_G_SVG}
                <span class="fw-semibold small text-uppercase" style="letter-spacing:0.08em;">Google OAuth 2.0 Sign-In</span>
              </div>
              <button type="button" class="btn-close" id="tcGoogleClose"></button>
            </div>
            <div class="p-3 mb-3" style="background:var(--tc-stone);border:1px solid var(--tc-border);border-radius:3px;">
              <div class="fw-semibold small mb-1">Connect Real Google Account Popup</div>
              <p class="text-muted mb-2" style="font-size:12px;">
                Paste your Google OAuth 2.0 Web Client ID (with Authorized JavaScript Origin <code>http://localhost:8080</code>) to launch the live <strong>accounts.google.com</strong> browser chooser:
              </p>
              <form id="tcSaveGoogleClientIdForm" class="d-flex gap-2">
                <input type="text" id="gClientIdInput" class="form-control form-control-sm"
                       placeholder="xxxx.apps.googleusercontent.com"
                       value="${escapeHtml(localStorage.getItem('tc_google_client_id') || '')}" required>
                <button type="submit" class="btn-tc-primary py-1 px-3">Launch</button>
              </form>
            </div>
            <div class="tc-divider">or sign in immediately (Local Dev Mode)</div>
            <form id="tcGoogleCustomForm" class="vstack gap-2">
              <div class="row g-2">
                <div class="col-6"><input type="text" id="gCustomFirst" class="form-control form-control-sm" placeholder="First Name" required></div>
                <div class="col-6"><input type="text" id="gCustomLast" class="form-control form-control-sm" placeholder="Last Name" required></div>
              </div>
              <input type="email" id="gCustomEmail" class="form-control form-control-sm" placeholder="Enter your Gmail address (e.g. you@gmail.com)" required>
              <button type="submit" class="btn-tc-primary w-100 mt-1">Continue with This Gmail</button>
            </form>
          </div>`;
        document.body.appendChild(wrap);

        const completeGoogleSignIn = async (email, firstName, lastName) => {
            try {
                const authData = await api('/auth/google', { method: 'POST', body: { email, firstName, lastName } });
                setAuthSession(authData);
                wrap.remove();
                toast('Welcome, ' + authData.user.firstName);
                window.location.href = redirectUrl;
            } catch (err) {
                toast(err.message, 'error');
            }
        };

        document.getElementById('tcGoogleClose').addEventListener('click', () => wrap.remove());
        wrap.addEventListener('click', e => { if (e.target === wrap) wrap.remove(); });
        document.getElementById('tcSaveGoogleClientIdForm').addEventListener('submit', async e => {
            e.preventDefault();
            const cid = document.getElementById('gClientIdInput').value.trim();
            if (!cid) return;
            localStorage.setItem('tc_google_client_id', cid);
            wrap.remove();
            try {
                await loadGoogleGsiScript();
                const tokenClient = window.google.accounts.oauth2.initTokenClient({
                    client_id: cid,
                    scope: 'openid email profile',
                    prompt: 'select_account',
                    callback: async (tokenResponse) => {
                        if (tokenResponse && tokenResponse.access_token) {
                            const authData = await api('/auth/google', { method: 'POST', body: { credential: tokenResponse.access_token } });
                            setAuthSession(authData);
                            toast('Signed in with Google as ' + authData.user.email);
                            window.location.href = redirectUrl;
                        }
                    }
                });
                tokenClient.requestAccessToken();
            } catch (err) {
                toast(err.message, 'error');
            }
        });
        document.getElementById('tcGoogleCustomForm').addEventListener('submit', e => {
            e.preventDefault();
            completeGoogleSignIn(
                document.getElementById('gCustomEmail').value,
                document.getElementById('gCustomFirst').value,
                document.getElementById('gCustomLast').value
            );
        });
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
        drawerUpdateQty,
        drawerRemoveItem,
        recordRecentlyViewed,
        getRecentlyViewed,
        quickAddToCart,
        quickAddToWishlist,
        renderWatchCard,
        updateHeaderBadges,
        mountChrome,
        initScrollAnimations,
        mountGoogleAuthButton
    };
})();
