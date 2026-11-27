/**
 * TimeCraft Horological Archive & Catalog Controller
 * Supports:
 * - Instant live filtering on select/checkbox/input changes
 * - Curated Collection Tier separation pills (Haute Horlogerie vs Contemporary)
 * - Active filter pills bar with individual removal & Reset All
 * - Mobile collapsible filter drawer toggle
 */
(function () {
    const PILL_LABELS = {
        gender: 'Silhouette',
        movement: 'Calibre',
        strapMaterial: 'Strap',
        dialColor: 'Dial',
        minPrice: 'Min ₹',
        maxPrice: 'Max ₹',
        minDiameter: 'Min mm',
        maxDiameter: 'Max mm',
        minWaterResistance: 'Water Res. (m)',
        minDiscount: 'Min %',
        minRating: 'Rating'
    };

    async function initCatalogPage(mode) {
        const params = new URLSearchParams(window.location.search);
        const gridEl = document.getElementById('tcProductGrid');
        const countEl = document.getElementById('tcResultCount');
        const pagerEl = document.getElementById('tcPagination');
        const titleEl = document.getElementById('tcCatalogTitle');
        const subEl = document.getElementById('tcCatalogSubtitle');
        const form = document.getElementById('tcFilterForm');
        const tierBar = document.getElementById('tcTierQuickBar');

        if (gridEl && !document.getElementById('tcActivePillsBar')) {
            const pillsBar = document.createElement('div');
            pillsBar.id = 'tcActivePillsBar';
            pillsBar.className = 'd-flex flex-wrap align-items-center gap-2 mb-3';
            gridEl.parentElement.insertBefore(pillsBar, gridEl);
        }

        if (form && !document.getElementById('tcMobileFilterToggle')) {
            const toggleWrap = document.createElement('div');
            toggleWrap.className = 'd-lg-none mb-3';
            toggleWrap.innerHTML = `<button type="button" id="tcMobileFilterToggle" class="btn-tc-secondary w-100">Refine Timepieces &amp; Calibres</button>`;
            form.parentElement.insertBefore(toggleWrap, form);
            form.classList.add('d-none', 'd-lg-flex');
            document.getElementById('tcMobileFilterToggle').addEventListener('click', () => {
                form.classList.toggle('d-none');
            });
        }

        try {
            const [brands, categories, facets] = await Promise.all([
                TC.api('/brands'),
                TC.api('/categories'),
                TC.api('/products/facets')
            ]);
            fillSelect('filterBrand', brands.map(b => ({ value: b.slug, label: b.name })), params.get('brand'));
            fillSelect('filterCategory', categories.map(c => ({ value: c.slug, label: c.name })), params.get('category'));
            fillSelect('filterMovement', (facets.movements || []).map(m => ({ value: m, label: m })), params.get('movement'));
            fillSelect('filterStrap', (facets.strapMaterials || []).map(s => ({ value: s, label: s })), params.get('strapMaterial'));
            fillSelect('filterDial', (facets.dialColors || []).map(d => ({ value: d, label: d })), params.get('dialColor'));

            if (mode === 'category') {
                const slug = params.get('slug') || params.get('category') || 'dress';
                setVal('filterCategory', slug);
                await syncCategoryHeader(slug, titleEl, subEl);
            } else if (mode === 'brand') {
                const slug = params.get('slug') || params.get('brand') || 'aurelian';
                setVal('filterBrand', slug);
                try {
                    const br = await TC.api('/brands/' + encodeURIComponent(slug));
                    if (titleEl) titleEl.textContent = br.name;
                    if (subEl) subEl.textContent = br.description || '';
                    const eyebrowEl = document.getElementById('tcBrandEyebrow');
                    if (eyebrowEl) {
                        eyebrowEl.textContent = (br.countryOfOrigin || 'Authorised Maison') + (br.foundedYear ? ' · Est. ' + br.foundedYear : '');
                    }
                    const bannerEl = document.getElementById('tcBrandBannerImg');
                    if (bannerEl && br.bannerUrl) {
                        bannerEl.src = br.bannerUrl;
                        bannerEl.closest('#tcBrandBannerWrap')?.classList.remove('d-none');
                    }
                } catch {}
            } else if (mode === 'search') {
                const q = params.get('q') || '';
                setVal('filterQuery', q);
                if (titleEl) titleEl.textContent = q ? `Archive Results for “${q}”` : 'Search Horological Archive';
            }
        } catch (err) {
            console.warn('Facet load error', err);
        }

        ['q', 'minPrice', 'maxPrice', 'minDiameter', 'maxDiameter', 'minWaterResistance', 'minDiscount', 'minRating', 'sort'].forEach(k => {
            if (params.get(k)) setVal('filter_' + k, params.get(k));
        });
        if (params.get('gender')) {
            setVal('filterGender', params.get('gender').toUpperCase());
        }
        if (params.get('movement')) {
            setVal('filterMovement', params.get('movement').toUpperCase());
        }
        if (params.get('q')) setVal('filterQuery', params.get('q'));
        if (params.get('sort')) setVal('filterSort', params.get('sort'));
        if (params.get('inStock') === 'true') {
            const cb = document.getElementById('filterInStock');
            if (cb) cb.checked = true;
        }

        function syncShopEditorialHeading() {
            if (mode !== 'shop' || !titleEl) return;
            const minP = Number(getVal('filter_minPrice') || 0);
            const maxP = Number(getVal('filter_maxPrice') || 0);
            const gVal = getVal('filterGender');
            const mVal = getVal('filterMovement');

            if (minP >= 80000) {
                titleEl.textContent = 'Haute Horlogerie & Collector Archive';
                if (subEl) subEl.textContent = 'Swiss, European and Japanese mechanical collector timepieces (₹80,000+), regulated across five positions.';
            } else if (maxP > 0 && maxP <= 50000 && gVal === 'WOMEN') {
                titleEl.textContent = "Women's Contemporary Horology";
                if (subEl) subEl.textContent = "Refined contemporary Women's silhouettes priced below ₹50,000.";
            } else if (maxP > 0 && maxP <= 50000) {
                titleEl.textContent = 'Contemporary & Everyday Horology';
                if (subEl) subEl.textContent = 'Precision quartz, solar, ceramic and automatic daily instruments under ₹50,000.';
            } else if (gVal === 'MEN') {
                titleEl.textContent = "Men's Timepieces";
                if (subEl) subEl.textContent = 'Mechanical dress watches, tool chronographs, saturation divers and contemporary icons for men.';
            } else if (gVal === 'WOMEN') {
                titleEl.textContent = "Women's Timepieces";
                if (subEl) subEl.textContent = 'Parisian petite mechanicals, ceramic dress silhouettes and refined contemporary watches.';
            } else if (gVal === 'UNISEX') {
                titleEl.textContent = 'Mid-Size & Unisex Watches';
                if (subEl) subEl.textContent = 'Balanced architectural proportions crafted for every wrist.';
            } else if (mVal === 'AUTOMATIC') {
                titleEl.textContent = 'Self-Winding Automatic Calibres';
                if (subEl) subEl.textContent = 'Perpetual rotor-wound mechanical movements from Geneva, Paris, Tokyo and Bengaluru.';
            } else {
                titleEl.textContent = 'All Timepieces';
                if (subEl) subEl.textContent = 'Mechanical complications, hand-wound dress calibres, titanium GMTs, and contemporary precision instruments.';
            }

            if (tierBar) {
                let activePreset = '';
                if (minP >= 80000 && !maxP && !gVal && !mVal) activePreset = 'HAUTE';
                else if (maxP === 50000 && !minP && !gVal && !mVal) activePreset = 'CONTEMPORARY';
                else if (mVal === 'AUTOMATIC' && !minP && !maxP && !gVal) activePreset = 'AUTOMATIC';
                else if (gVal === 'MEN' && !minP && !maxP && !mVal) activePreset = 'MEN';
                else if (gVal === 'WOMEN' && !minP && !maxP && !mVal) activePreset = 'WOMEN';
                else if (!minP && !maxP && !gVal && !mVal && !getVal('filterBrand') && !getVal('filterCategory') && !getVal('filterQuery')) activePreset = 'ALL';

                tierBar.querySelectorAll('[data-preset]').forEach(btn => {
                    btn.classList.toggle('active', btn.dataset.preset === activePreset);
                });
            }
        }

        if (tierBar) {
            tierBar.querySelectorAll('[data-preset]').forEach(btn => {
                btn.addEventListener('click', () => {
                    const preset = btn.dataset.preset;
                    setVal('filter_minPrice', '');
                    setVal('filter_maxPrice', '');
                    setVal('filterGender', '');
                    setVal('filterMovement', '');
                    if (preset === 'HAUTE') {
                        setVal('filter_minPrice', '80000');
                        setVal('filterSort', 'price_desc');
                    } else if (preset === 'CONTEMPORARY') {
                        setVal('filter_maxPrice', '50000');
                    } else if (preset === 'AUTOMATIC') {
                        setVal('filterMovement', 'AUTOMATIC');
                    } else if (preset === 'MEN') {
                        setVal('filterGender', 'MEN');
                    } else if (preset === 'WOMEN') {
                        setVal('filterGender', 'WOMEN');
                    } else if (preset === 'ALL') {
                        if (form) form.reset();
                    }
                    syncShopEditorialHeading();
                    loadPage(0);
                });
            });
        }

        if (form) {
            form.addEventListener('submit', e => {
                e.preventDefault();
                syncShopEditorialHeading();
                loadPage(0);
            });
            form.querySelectorAll('select, input[type="checkbox"]').forEach(el => {
                el.addEventListener('change', async () => {
                    if (el.id === 'filterCategory' && mode === 'category' && el.value) {
                        await syncCategoryHeader(el.value, titleEl, subEl);
                    }
                    syncShopEditorialHeading();
                    loadPage(0);
                });
            });
            let inputTimer = null;
            form.querySelectorAll('input[type="text"], input[type="number"]').forEach(el => {
                el.addEventListener('input', () => {
                    clearTimeout(inputTimer);
                    inputTimer = setTimeout(() => {
                        syncShopEditorialHeading();
                        loadPage(0);
                    }, 260);
                });
            });
        }

        const resetBtn = document.getElementById('tcResetFilters');
        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                if (form) form.reset();
                syncShopEditorialHeading();
                loadPage(0);
            });
        }
        const sortSel = document.getElementById('filterSort');
        if (sortSel) {
            sortSel.addEventListener('change', () => loadPage(0));
        }

        syncShopEditorialHeading();
        await loadPage(Number(params.get('page') || 0));

        async function loadPage(pageNumber) {
            if (!gridEl) return;
            gridEl.innerHTML = '<div class="col-12 py-5 text-center text-muted small">Consulting horological archive…</div>';
            const q = new URLSearchParams();
            q.set('page', String(pageNumber));
            q.set('size', '12');

            const queryVal = getVal('filterQuery') || (mode === 'search' ? params.get('q') : '');
            if (queryVal) q.set('q', queryVal);

            const brandSel = document.getElementById('filterBrand');
            const brandVal = brandSel ? brandSel.value.trim() : (mode === 'brand' ? (params.get('slug') || params.get('brand') || '') : '');
            if (brandVal) q.set('brand', brandVal);

            const catSel = document.getElementById('filterCategory');
            const catVal = catSel ? catSel.value.trim() : (mode === 'category' ? (params.get('slug') || params.get('category') || '') : '');
            if (catVal) q.set('category', catVal);

            const fieldIds = {
                gender: 'filterGender',
                movement: 'filterMovement',
                strapMaterial: 'filterStrap',
                dialColor: 'filterDial',
                minPrice: 'filter_minPrice',
                maxPrice: 'filter_maxPrice',
                minDiameter: 'filter_minDiameter',
                maxDiameter: 'filter_maxDiameter',
                minWaterResistance: 'filter_minWaterResistance',
                minDiscount: 'filter_minDiscount',
                minRating: 'filter_minRating'
            };
            Object.entries(fieldIds).forEach(([paramName, elId]) => {
                const v = getVal(elId);
                if (v) q.set(paramName, v);
            });

            if (params.get('featured') === 'true') q.set('featured', 'true');
            if (params.get('newArrival') === 'true') q.set('newArrival', 'true');
            if (params.get('bestSeller') === 'true') q.set('bestSeller', 'true');

            const inStockEl = document.getElementById('filterInStock');
            if (inStockEl && inStockEl.checked) q.set('inStock', 'true');

            const sortVal = getVal('filterSort') || 'featured';
            q.set('sort', sortVal);

            renderActivePills(fieldIds);

            try {
                const data = await TC.api('/products?' + q.toString());
                if (countEl) {
                    countEl.textContent = `${data.totalElements} reference${data.totalElements === 1 ? '' : 's'}`;
                }
                if (!data.content || data.content.length === 0) {
                    gridEl.className = 'row g-4';
                    gridEl.innerHTML = `
                    <div class="col-12">
                      <div class="tc-panel text-center py-5">
                        <h3 class="tc-serif fs-3 mb-2">No matching references found</h3>
                        <p class="text-muted small mb-3">Broaden your calibre, case proportion or valuation criteria to explore additional timepieces.</p>
                        <button type="button" class="btn-tc-secondary" onclick="document.getElementById('tcResetFilters')?.click()">Reset Refinements</button>
                      </div>
                    </div>`;
                } else {
                    gridEl.className = (mode === 'brand') ? 'tc-product-grid' : 'tc-product-grid tc-catalog-grid';
                    gridEl.innerHTML = data.content.map(p => TC.renderWatchCard(p, { bare: true })).join('');
                    TC.initScrollAnimations();
                }
                if (pagerEl) renderPagination(pagerEl, data, loadPage);
            } catch (err) {
                gridEl.innerHTML = `<div class="col-12"><div class="alert alert-danger rounded-0">${TC.escapeHtml(err.message)}</div></div>`;
            }
        }

        function renderActivePills(fieldIds) {
            const bar = document.getElementById('tcActivePillsBar');
            if (!bar) return;
            const pills = [];
            const addPill = (label, clearFn) => pills.push({ label, clearFn });

            const qv = getVal('filterQuery');
            if (qv) addPill(`Reference: ${qv}`, () => { setVal('filterQuery', ''); syncShopEditorialHeading(); loadPage(0); });

            const bv = getVal('filterBrand');
            if (bv && mode !== 'brand') addPill(`Maison: ${bv}`, () => { setVal('filterBrand', ''); syncShopEditorialHeading(); loadPage(0); });

            const cv = getVal('filterCategory');
            if (cv && mode !== 'category') addPill(`Category: ${cv}`, () => { setVal('filterCategory', ''); syncShopEditorialHeading(); loadPage(0); });

            Object.entries(fieldIds).forEach(([k, id]) => {
                const v = getVal(id);
                if (v) {
                    const displayVal = (k === 'minPrice' || k === 'maxPrice') ? TC.formatInr(v) : v;
                    addPill(`${PILL_LABELS[k] || k}: ${displayVal}`, () => { setVal(id, ''); syncShopEditorialHeading(); loadPage(0); });
                }
            });

            const inStockEl = document.getElementById('filterInStock');
            if (inStockEl && inStockEl.checked) {
                addPill('In Vault Stock', () => { inStockEl.checked = false; loadPage(0); });
            }

            if (!pills.length) {
                bar.innerHTML = '';
                return;
            }
            bar.innerHTML = pills.map((p, idx) => `
              <span class="tc-filter-pill">
                <span>${TC.escapeHtml(p.label)}</span>
                <button type="button" data-pill-idx="${idx}" aria-label="Remove filter">&times;</button>
              </span>`).join('') + `
              <button type="button" class="btn-tc-secondary py-1 px-2" style="font-size:10.5px;" id="tcClearAllPillsBtn">Clear All</button>`;

            bar.querySelectorAll('button[data-pill-idx]').forEach(btn => {
                btn.addEventListener('click', () => pills[Number(btn.getAttribute('data-pill-idx'))].clearFn());
            });
            document.getElementById('tcClearAllPillsBtn')?.addEventListener('click', () => {
                if (form) form.reset();
                syncShopEditorialHeading();
                loadPage(0);
            });
        }
    }

    async function syncCategoryHeader(slug, titleEl, subEl) {
        try {
            const cat = await TC.api('/categories/' + encodeURIComponent(slug));
            if (titleEl) titleEl.textContent = cat.name;
            if (subEl) subEl.textContent = cat.description || '';
        } catch {}
    }

    function fillSelect(id, options, selectedValue) {
        const el = document.getElementById(id);
        if (!el) return;
        const first = el.options[0] ? el.options[0].outerHTML : '<option value="">All</option>';
        el.innerHTML = first + options.map(o =>
            `<option value="${TC.escapeHtml(o.value)}" ${selectedValue === String(o.value) ? 'selected' : ''}>${TC.escapeHtml(o.label)}</option>`
        ).join('');
    }

    function getVal(id) {
        const el = document.getElementById(id);
        return el && el.value ? el.value.trim() : '';
    }

    function setVal(id, val) {
        const el = document.getElementById(id);
        if (el && val !== null && val !== undefined) el.value = val;
    }

    function renderPagination(host, pageData, onPageClick) {
        if (!pageData || pageData.totalPages <= 1) {
            host.innerHTML = '';
            return;
        }
        let html = '<div class="d-flex justify-content-center gap-1 mt-5">';
        for (let i = 0; i < pageData.totalPages; i++) {
            const active = i === pageData.page;
            html += `<button type="button" class="${active ? 'btn-tc-primary' : 'btn-tc-secondary'} py-1 px-3 tc-num" data-page="${i}">${i + 1}</button>`;
        }
        html += '</div>';
        host.innerHTML = html;
        host.querySelectorAll('button[data-page]').forEach(btn => {
            btn.addEventListener('click', () => {
                onPageClick(Number(btn.getAttribute('data-page')));
                window.scrollTo({ top: 0, behavior: 'smooth' });
            });
        });
    }

    window.TCCatalog = { initCatalogPage };
})();
