/**
 * Live Interactive Watch Catalog Controller
 * Supports:
 * - Instant live filtering on select/checkbox/input changes
 * - Active filter pills bar with individual removal & Reset All
 * - Mobile slide-out filter drawer toggle
 */
(function () {
    async function initCatalogPage(mode) {
        const params = new URLSearchParams(window.location.search);
        const gridEl = document.getElementById('tcProductGrid');
        const countEl = document.getElementById('tcResultCount');
        const pagerEl = document.getElementById('tcPagination');
        const titleEl = document.getElementById('tcCatalogTitle');
        const subEl = document.getElementById('tcCatalogSubtitle');
        const form = document.getElementById('tcFilterForm');

        // Inject mobile filter drawer button & active filter pills host above the grid
        if (gridEl && !document.getElementById('tcActivePillsBar')) {
            const pillsBar = document.createElement('div');
            pillsBar.id = 'tcActivePillsBar';
            pillsBar.className = 'd-flex flex-wrap align-items-center gap-2 mb-3';
            gridEl.parentElement.insertBefore(pillsBar, gridEl);
        }

        if (form && !document.getElementById('tcMobileFilterToggle')) {
            const toggleWrap = document.createElement('div');
            toggleWrap.className = 'd-lg-none mb-3';
            toggleWrap.innerHTML = `<button type="button" id="tcMobileFilterToggle" class="btn-tc-secondary w-100">☰ Filter &amp; Refine Timepieces</button>`;
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
                    if (subEl) subEl.textContent = (br.countryOfOrigin ? br.countryOfOrigin + (br.foundedYear ? ' · Est. ' + br.foundedYear + ' — ' : ' — ') : '') + (br.description || '');
                } catch {}
            } else if (mode === 'search') {
                const q = params.get('q') || '';
                setVal('filterQuery', q);
                if (titleEl) titleEl.textContent = q ? `Search results for “${q}”` : 'Search Timepieces';
            }
        } catch (err) {
            console.warn('Facet load error', err);
        }

        ['q', 'minPrice', 'maxPrice', 'minDiameter', 'maxDiameter', 'minWaterResistance', 'minDiscount', 'minRating', 'sort'].forEach(k => {
            if (params.get(k)) setVal('filter_' + k, params.get(k));
        });
        if (params.get('q')) setVal('filterQuery', params.get('q'));
        if (params.get('sort')) setVal('filterSort', params.get('sort'));
        if (params.get('inStock') === 'true') {
            const cb = document.getElementById('filterInStock');
            if (cb) cb.checked = true;
        }

        if (form) {
            form.addEventListener('submit', e => {
                e.preventDefault();
                loadPage(0);
            });
            // Live update on select / checkbox change
            form.querySelectorAll('select, input[type="checkbox"]').forEach(el => {
                el.addEventListener('change', async () => {
                    if (el.id === 'filterCategory' && mode === 'category' && el.value) {
                        await syncCategoryHeader(el.value, titleEl, subEl);
                    }
                    loadPage(0);
                });
            });
            let inputTimer = null;
            form.querySelectorAll('input[type="text"], input[type="number"]').forEach(el => {
                el.addEventListener('input', () => {
                    clearTimeout(inputTimer);
                    inputTimer = setTimeout(() => loadPage(0), 260);
                });
            });
        }

        const resetBtn = document.getElementById('tcResetFilters');
        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                if (form) form.reset();
                loadPage(0);
            });
        }
        const sortSel = document.getElementById('filterSort');
        if (sortSel) {
            sortSel.addEventListener('change', () => loadPage(0));
        }

        await loadPage(Number(params.get('page') || 0));

        async function loadPage(pageNumber) {
            if (!gridEl) return;
            gridEl.innerHTML = '<div class="col-12 py-5 text-center text-muted">Refining horological collection…</div>';
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
                    countEl.textContent = `${data.totalElements} timepiece${data.totalElements === 1 ? '' : 's'}`;
                }
                if (!data.content || data.content.length === 0) {
                    gridEl.className = 'row g-4';
                    gridEl.innerHTML = `
                    <div class="col-12">
                      <div class="tc-panel text-center py-5">
                        <h3 class="tc-serif fs-3 mb-2">No matching timepieces found</h3>
                        <p class="text-muted small mb-3">Try broadening your movement, diameter or price filters.</p>
                        <button type="button" class="btn-tc-secondary" onclick="document.getElementById('tcResetFilters')?.click()">Reset All Filters</button>
                      </div>
                    </div>`;
                } else {
                    gridEl.className = 'tc-product-grid tc-catalog-grid';
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
            if (qv) addPill(`Keyword: ${qv}`, () => { setVal('filterQuery', ''); loadPage(0); });

            const bv = getVal('filterBrand');
            if (bv && mode !== 'brand') addPill(`Maison: ${bv}`, () => { setVal('filterBrand', ''); loadPage(0); });

            const cv = getVal('filterCategory');
            if (cv) addPill(`Category: ${cv}`, () => { setVal('filterCategory', ''); loadPage(0); });

            Object.entries(fieldIds).forEach(([k, id]) => {
                const v = getVal(id);
                if (v) addPill(`${k}: ${v}`, () => { setVal(id, ''); loadPage(0); });
            });

            const inStockEl = document.getElementById('filterInStock');
            if (inStockEl && inStockEl.checked) {
                addPill('In Stock Only', () => { inStockEl.checked = false; loadPage(0); });
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
              <button type="button" class="btn-tc-secondary py-1 px-2" style="font-size:11px;" id="tcClearAllPillsBtn">Clear All</button>`;

            bar.querySelectorAll('button[data-pill-idx]').forEach(btn => {
                btn.addEventListener('click', () => pills[Number(btn.getAttribute('data-pill-idx'))].clearFn());
            });
            document.getElementById('tcClearAllPillsBtn')?.addEventListener('click', () => {
                if (form) form.reset();
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
        let html = '<div class="d-flex justify-content-center gap-1 mt-4">';
        for (let i = 0; i < pageData.totalPages; i++) {
            const active = i === pageData.page;
            html += `<button type="button" class="${active ? 'btn-tc-primary' : 'btn-tc-secondary'} py-1 px-3" data-page="${i}">${i + 1}</button>`;
        }
        html += '</div>';
        host.innerHTML = html;
        host.querySelectorAll('button[data-page]').forEach(btn => {
            btn.addEventListener('click', () => onPageClick(Number(btn.getAttribute('data-page'))));
        });
    }

    window.TCCatalog = { initCatalogPage };
})();
