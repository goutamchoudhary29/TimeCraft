/**
 * TimeCraft Luxury Order Concierge & Visual Journey Controller
 * Powers /pages/order-details.html and /pages/order-confirmation.html
 * Preserves immutable backend order snapshots, Razorpay/PayU/COD recovery,
 * PDF invoice generation, shipment telemetry, and validated cancellation rules.
 */
(function () {
    const FALLBACK_WATCH_IMG = 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&q=80';

    const exactInr = new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });

    const regionNames = (typeof Intl !== 'undefined' && typeof Intl.DisplayNames === 'function')
        ? new Intl.DisplayNames(['en'], { type: 'region' })
        : null;

    const ICONS = {
        sealCheck: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M20 6.5L9.5 17 4 11.5" stroke-linecap="round" stroke-linejoin="round"/></svg>`,
        stepCheck: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M19.5 7L9.5 17L4.5 12" stroke-linecap="round" stroke-linejoin="round"/></svg>`,
        invoice: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M12 11v6M9.5 14.5L12 17l2.5-2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>`,
        track: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><circle cx="12" cy="12" r="8.5"/><path d="M12 7.5v4.8l3.2 1.9" stroke-linecap="round" stroke-linejoin="round"/></svg>`,
        cancel: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><circle cx="12" cy="12" r="8.5"/><path d="M15 9l-6 6M9 9l6 6" stroke-linecap="round"/></svg>`,
        returnIcon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M9 14L4 9l5-5" stroke-linecap="round" stroke-linejoin="round"/><path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11" stroke-linecap="round"/></svg>`,
        location: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M12 21s7-6.2 7-11.5A7 7 0 0 0 5 9.5C5 14.8 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.4"/></svg>`,
        alert: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 8v4.5M12 16h.01" stroke-linecap="round"/></svg>`
    };

    function formatMoney(val) {
        if (val === null || val === undefined || val === '') return '₹0.00';
        const n = Number(val);
        if (Number.isNaN(n)) return '₹' + TC.escapeHtml(String(val));
        return exactInr.format(n);
    }

    function formatDateTime(iso) {
        if (!iso) return '';
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return String(iso);
        return d.toLocaleString('en-IN', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function formatShortStepTime(iso) {
        if (!iso) return '';
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return '';
        return d.toLocaleString('en-IN', {
            day: '2-digit',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function formatLocalDate(dateStr) {
        if (!dateStr) return '';
        const parts = String(dateStr).split('-');
        if (parts.length === 3) {
            const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
            if (!Number.isNaN(d.getTime())) {
                return d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' });
            }
        }
        return String(dateStr);
    }

    function formatCountryName(code) {
        if (!code) return 'India';
        const clean = String(code).trim().toUpperCase();
        if (clean.length === 2 && regionNames) {
            try {
                return regionNames.of(clean) || clean;
            } catch {
                return clean === 'IN' ? 'India' : clean;
            }
        }
        return clean;
    }

    function humanizeStatus(raw) {
        if (!raw) return '—';
        const map = {
            PENDING: 'Order Placed · Pending Payment',
            CONFIRMED: 'Confirmed',
            PROCESSING: 'Order Processing',
            PACKED: 'Dispatched · Packed in Vault',
            SHIPPED: 'Shipped',
            OUT_FOR_DELIVERY: 'Out for Delivery',
            DELIVERED: 'Delivered',
            CANCELLED: 'Cancelled',
            RETURN_REQUESTED: 'Return Requested',
            RETURNED: 'Returned',
            REFUNDED: 'Refunded',
            FAILED: 'Payment Failed'
        };
        return map[raw] || String(raw).replace(/_/g, ' ');
    }

    function humanizePaymentStatus(raw) {
        if (!raw) return '—';
        const map = {
            PENDING: 'Pending Verification',
            PENDING_COD: 'Payable on Delivery (COD)',
            PAID: 'Paid & Verified',
            FAILED: 'Failed',
            PARTIALLY_REFUNDED: 'Partially Refunded',
            REFUNDED: 'Refunded'
        };
        return map[raw] || String(raw).replace(/_/g, ' ');
    }

    function humanizePaymentMethod(raw) {
        if (!raw) return 'Online Payment';
        const map = {
            RAZORPAY: 'Razorpay (UPI / Card / Netbanking)',
            PAYU: 'PayU Secure Gateway',
            COD: 'Cash on Delivery (COD)'
        };
        return map[raw] || String(raw);
    }

    function getStatusTone(orderStatus, paymentStatus) {
        if (orderStatus === 'DELIVERED') return 'tone-success';
        if (['CANCELLED', 'FAILED'].includes(orderStatus) || paymentStatus === 'FAILED') return 'tone-danger';
        if (['CONFIRMED', 'PROCESSING', 'PACKED', 'SHIPPED', 'OUT_FOR_DELIVERY', 'REFUNDED'].includes(orderStatus)) return 'tone-gold';
        return '';
    }

    function getPaymentTone(paymentStatus) {
        if (paymentStatus === 'PAID') return 'tone-success';
        if (paymentStatus === 'FAILED') return 'tone-danger';
        if (paymentStatus === 'PENDING_COD' || paymentStatus === 'REFUNDED' || paymentStatus === 'PARTIALLY_REFUNDED') return 'tone-gold';
        return '';
    }

    /**
     * Computes the truthful 7-stage horological tracking model strictly from backend
     * OrderStatus, PaymentStatus, chronological statusHistory, and Shipment events.
     * Never falsely marks future unreached stages as completed.
     */
    function buildSevenStageModel(order, shipment) {
        const sortedHistory = Array.isArray(order.statusHistory)
            ? [...order.statusHistory].sort((a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0))
            : [];
        const sortedShipEvents = (shipment && Array.isArray(shipment.events))
            ? [...shipment.events].sort((a, b) => new Date(a.eventTime || 0) - new Date(b.eventTime || 0))
            : [];

        function firstHistoryTime(statuses) {
            const set = new Set(statuses);
            const hit = sortedHistory.find(h => set.has(h.toStatus));
            return hit ? hit.createdAt : null;
        }

        function firstShipmentTime(statuses) {
            const set = new Set(statuses);
            const hit = sortedShipEvents.find(e => set.has(e.status));
            return hit ? hit.eventTime : null;
        }

        const isCod = order.paymentMethod === 'COD';
        const isTerminalAbort = ['CANCELLED', 'FAILED'].includes(order.status);
        const isRefundedAfterCancel = order.status === 'REFUNDED' && !order.deliveredAt && !firstHistoryTime(['DELIVERED']);

        // Determine highest canonical stage index (0..6) legitimately reached
        const STAGE_RANK = {
            PENDING: 0,
            CONFIRMED: 1,
            PROCESSING: 2,
            PACKED: 3,
            SHIPPED: 4,
            OUT_FOR_DELIVERY: 5,
            DELIVERED: 6,
            RETURN_REQUESTED: 6,
            RETURNED: 6
        };

        let reachedRank = STAGE_RANK[order.status] !== undefined ? STAGE_RANK[order.status] : 0;

        if (isTerminalAbort || isRefundedAfterCancel) {
            // Reconstruct exact highest stage reached prior to cancellation/failure
            reachedRank = 0;
            sortedHistory.forEach(h => {
                if (STAGE_RANK[h.toStatus] !== undefined && !['CANCELLED', 'FAILED', 'REFUNDED'].includes(h.toStatus)) {
                    reachedRank = Math.max(reachedRank, STAGE_RANK[h.toStatus]);
                }
            });
            if (order.paymentStatus === 'PAID' || order.confirmedAt) {
                reachedRank = Math.max(reachedRank, 1);
            }
        } else if (order.status === 'REFUNDED' && (order.deliveredAt || firstHistoryTime(['DELIVERED']))) {
            reachedRank = 6;
        }

        const stepDefs = [
            {
                key: 'PLACED',
                label: 'Order Placed',
                desc: 'Immutable order dossier recorded in vault.',
                timestamp: order.placedAt || firstHistoryTime(['PENDING', 'CONFIRMED'])
            },
            {
                key: 'PAYMENT',
                label: isCod && order.paymentStatus !== 'PAID' ? 'COD Confirmed' : 'Payment Confirmed',
                desc: isCod && order.paymentStatus !== 'PAID'
                    ? 'Verified for Cash on Delivery settlement.'
                    : 'Payment cryptographically verified.',
                timestamp: order.confirmedAt || firstHistoryTime(['CONFIRMED'])
            },
            {
                key: 'PROCESSING',
                label: 'Order Processing',
                desc: '5-position chronometer & gasket inspection.',
                timestamp: firstHistoryTime(['PROCESSING'])
            },
            {
                key: 'DISPATCHED',
                label: 'Dispatched',
                desc: 'Sealed in tamper-evident armoured parcel.',
                timestamp: firstHistoryTime(['PACKED']) || firstShipmentTime(['LABEL_CREATED'])
            },
            {
                key: 'SHIPPED',
                label: 'Shipped',
                desc: shipment && shipment.courierName
                    ? `In insured transit via ${shipment.courierName}.`
                    : 'Handed over to insured vault courier.',
                timestamp: (shipment && shipment.shippedAt) || firstHistoryTime(['SHIPPED']) || firstShipmentTime(['IN_TRANSIT'])
            },
            {
                key: 'OUT_FOR_DELIVERY',
                label: 'Out for Delivery',
                desc: 'With local courier specialist for handoff.',
                timestamp: firstHistoryTime(['OUT_FOR_DELIVERY']) || firstShipmentTime(['OUT_FOR_DELIVERY'])
            },
            {
                key: 'DELIVERED',
                label: 'Delivered',
                desc: 'Handed over to collector.',
                timestamp: order.deliveredAt || firstHistoryTime(['DELIVERED']) || firstShipmentTime(['DELIVERED'])
            }
        ];

        let activeIndex = reachedRank;
        // Special case: when order is PENDING awaiting online payment, Step 0 (Placed) is completed and Step 1 (Payment) is current/awaiting
        if (order.status === 'PENDING' && order.paymentStatus === 'PENDING') {
            activeIndex = 1;
        }

        const steps = stepDefs.map((def, idx) => {
            let state = 'pending';
            if (isTerminalAbort || isRefundedAfterCancel) {
                if (idx <= reachedRank) {
                    state = 'completed';
                } else if (idx === reachedRank + 1) {
                    state = 'halted';
                } else {
                    state = 'pending';
                }
            } else if (order.status === 'PENDING' && order.paymentStatus === 'PENDING') {
                if (idx === 0) state = 'completed';
                else if (idx === 1) state = 'current';
                else state = 'pending';
            } else if (reachedRank >= 6) {
                state = 'completed';
            } else {
                if (idx < reachedRank) state = 'completed';
                else if (idx === reachedRank) state = 'current';
                else state = 'pending';
            }

            return {
                index: idx + 1,
                label: def.label,
                desc: state === 'halted'
                    ? (order.status === 'FAILED' ? 'Halted — payment unsuccessful.' : 'Halted — order cancelled.')
                    : def.desc,
                timestamp: (state === 'completed' || (state === 'current' && def.timestamp)) ? def.timestamp : null,
                state
            };
        });

        // Progress percentage across the 6 segments connecting 7 nodes (0% to 100%)
        let progressStop = 0;
        if (isTerminalAbort || isRefundedAfterCancel) {
            progressStop = Math.min(6, Math.max(0, reachedRank));
        } else if (order.status === 'PENDING' && order.paymentStatus === 'PENDING') {
            progressStop = 0.5;
        } else {
            progressStop = Math.min(6, Math.max(0, activeIndex));
        }
        const progressPercent = Math.round((progressStop / 6) * 100);

        // Build unified chronological event feed from backend statusHistory + courier events
        const unifiedEvents = [];
        sortedHistory.forEach(h => {
            unifiedEvents.push({
                time: h.createdAt,
                title: humanizeStatus(h.toStatus),
                subtitle: h.note || '',
                actor: h.changedByType || 'SYSTEM'
            });
        });
        sortedShipEvents.forEach(ev => {
            unifiedEvents.push({
                time: ev.eventTime,
                title: `Courier · ${String(ev.status || '').replace(/_/g, ' ')}`,
                subtitle: [ev.location, ev.description].filter(Boolean).join(' — '),
                actor: (shipment && shipment.courierName) || 'COURIER'
            });
        });
        unifiedEvents.sort((a, b) => new Date(b.time || 0) - new Date(a.time || 0)); // Latest clearly at top, chronological progression clear

        return { steps, progressPercent, unifiedEvents };
    }

    function extractSpecsForWatch(item) {
        let dial = item.dialColor || '';
        let strap = [item.strapColor, item.strapMaterial].filter(Boolean).join(' ');
        if ((!dial || !strap) && item.variantName) {
            const parts = String(item.variantName).split(/[·/|-]/).map(s => s.trim()).filter(Boolean);
            if (!dial && parts[0]) dial = parts[0];
            if (!strap && parts[1]) strap = parts[1];
        }
        return {
            dial: dial || item.variantName || 'Factory Standard Dial',
            strap: strap || item.variantName || 'Factory Bracelet / Strap'
        };
    }

    function openCancelOrderModal(order, onConfirmed) {
        const existing = document.getElementById('tcCancelOrderModal');
        if (existing) existing.remove();

        const backdrop = document.createElement('div');
        backdrop.id = 'tcCancelOrderModal';
        backdrop.className = 'tc-google-modal-backdrop';
        backdrop.addEventListener('click', ev => {
            if (ev.target === backdrop) backdrop.remove();
        });

        backdrop.innerHTML = `
          <div class="tc-google-modal" style="max-width:480px;border-radius:1px;">
            <div class="d-flex justify-content-between align-items-start pb-3 mb-3" style="border-bottom:1px solid var(--tc-order-border);">
              <div>
                <div class="tc-eyebrow" style="color:var(--tc-order-accent);">Order Concierge</div>
                <h2 class="tc-serif fs-3 mb-0">Cancel Order ${TC.escapeHtml(order.orderNumber)}</h2>
              </div>
              <button type="button" class="btn-close" aria-label="Close" id="tcCancelModalClose"></button>
            </div>
            <p class="small text-muted mb-3">
              ${order.paymentStatus === 'PAID'
                  ? 'Cancelling this order will immediately release your timepiece allocation and initiate refund review for your verified payment.'
                  : 'Cancelling this order will release your reserved timepiece stock immediately.'}
            </p>
            <form id="tcCancelOrderForm" class="vstack gap-3">
              <div>
                <label for="tcCancelReasonSelect" class="form-label small text-uppercase" style="font-size:10.5px;letter-spacing:0.14em;">Select Reason</label>
                <select id="tcCancelReasonSelect" class="form-select form-select-sm">
                  <option value="Changed timepiece preference">Changed timepiece preference</option>
                  <option value="Updating delivery address or payment method">Updating delivery address or payment method</option>
                  <option value="Delivery timeline requirement changed">Delivery timeline requirement changed</option>
                  <option value="Ordered another reference">Ordered another reference</option>
                  <option value="OTHER">Other reason</option>
                </select>
              </div>
              <div id="tcCancelCustomWrap" class="d-none">
                <label for="tcCancelCustomInput" class="form-label small text-uppercase" style="font-size:10.5px;letter-spacing:0.14em;">Additional Note</label>
                <input type="text" id="tcCancelCustomInput" maxlength="240" class="form-control form-control-sm" placeholder="Share brief cancellation reason…">
              </div>
              <div class="d-flex justify-content-end gap-2 pt-2">
                <button type="button" class="tc-order-action-btn" id="tcCancelKeepBtn">Keep Order</button>
                <button type="submit" class="tc-order-action-btn is-danger" id="tcCancelConfirmBtn">Confirm Cancellation</button>
              </div>
            </form>
          </div>`;

        document.body.appendChild(backdrop);

        const sel = document.getElementById('tcCancelReasonSelect');
        const customWrap = document.getElementById('tcCancelCustomWrap');
        sel.addEventListener('change', () => {
            customWrap.classList.toggle('d-none', sel.value !== 'OTHER');
        });
        document.getElementById('tcCancelModalClose').addEventListener('click', () => backdrop.remove());
        document.getElementById('tcCancelKeepBtn').addEventListener('click', () => backdrop.remove());

        document.getElementById('tcCancelOrderForm').addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const submitBtn = document.getElementById('tcCancelConfirmBtn');
            const reason = sel.value === 'OTHER'
                ? (document.getElementById('tcCancelCustomInput').value.trim() || 'Cancelled by collector')
                : sel.value;

            submitBtn.disabled = true;
            submitBtn.textContent = 'Cancelling…';
            try {
                await TC.api(`/orders/${order.id}/cancel`, {
                    method: 'POST',
                    body: { reason }
                });
                backdrop.remove();
                TC.toast('Order ' + order.orderNumber + ' has been cancelled');
                if (typeof onConfirmed === 'function') await onConfirmed();
            } catch (err) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Confirm Cancellation';
                TC.toast(err.message || 'Unable to cancel order', 'error');
            }
        });
    }

    async function downloadOrderInvoice(orderNumber, btnEl) {
        const prevHtml = btnEl ? btnEl.innerHTML : '';
        if (btnEl) {
            btnEl.disabled = true;
            btnEl.innerHTML = `${ICONS.invoice}<span>Generating PDF…</span>`;
        }
        try {
            const token = localStorage.getItem('tc_access_token');
            const res = await fetch(`/api/v1/orders/${encodeURIComponent(orderNumber)}/invoice`, {
                headers: token ? { 'Authorization': 'Bearer ' + token } : {}
            });
            if (!res.ok) throw new Error('Unable to generate tax invoice right now');
            const blob = await res.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `TimeCraft-Invoice-${orderNumber}.pdf`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(url);
            TC.toast('Downloaded TimeCraft-Invoice-' + orderNumber + '.pdf');
        } catch (err) {
            TC.toast(err.message || 'Invoice download failed', 'error');
        } finally {
            if (btnEl) {
                btnEl.disabled = false;
                btnEl.innerHTML = prevHtml;
            }
        }
    }

    async function init(options = {}) {
        const mode = options.mode === 'confirmation' ? 'confirmation' : 'details';
        const hostId = options.hostId || 'orderConciergeHost';
        const host = document.getElementById(hostId);
        if (!host) return;

        await TC.mountChrome('orders');

        const params = new URLSearchParams(window.location.search);
        const idOrNo = (params.get('order') || params.get('id') || '').trim();

        if (!TC.getUser()) {
            window.location.href = '/pages/login.html';
            return;
        }

        if (!idOrNo) {
            host.innerHTML = `
              <div class="tc-order-surface text-center py-5">
                <div class="tc-eyebrow mb-2" style="color:var(--tc-order-accent);">TimeCraft Order Concierge</div>
                <h1 class="tc-serif display-6 mb-2">Order Reference Not Specified</h1>
                <p class="text-muted small mx-auto mb-4" style="max-width:460px;">
                  Select an order from your collector archive to inspect horological tracking, tax invoices, and delivery details.
                </p>
                <div class="d-flex flex-wrap justify-content-center gap-3">
                  <a href="/pages/orders.html" class="tc-order-action-btn is-primary">View Order Archive</a>
                  <a href="/pages/shop.html" class="tc-order-action-btn">Explore Timepieces</a>
                </div>
              </div>`;
            return;
        }

        async function loadAndRender() {
            try {
                const order = await TC.api('/orders/' + encodeURIComponent(idOrNo));
                let shipment = null;
                if (order && order.orderNumber && !['PENDING', 'FAILED'].includes(order.status)) {
                    try {
                        shipment = await TC.api('/shipments/track/' + encodeURIComponent(order.orderNumber));
                    } catch {
                        shipment = null;
                    }
                }
                const deliveryMap = new Map();
                const shouldEstimate = order
                    && !['DELIVERED', 'CANCELLED', 'FAILED', 'REFUNDED', 'RETURNED'].includes(order.status)
                    && Array.isArray(order.items) && order.items.length > 0;
                if (shouldEstimate) {
                    const cleanPin = String(order.shipPostalCode || '').replace(/\D/g, '').slice(0, 6);
                    const uniqueProductIds = Array.from(new Set(order.items.map(i => i.productId).filter(Boolean)));
                    await Promise.all(uniqueProductIds.map(async pid => {
                        try {
                            const qs = new URLSearchParams({ productId: String(pid) });
                            if (cleanPin.length === 6) qs.set('pinCode', cleanPin);
                            const est = await TC.api('/delivery/estimate?' + qs.toString());
                            if (est) deliveryMap.set(Number(pid), est);
                        } catch {}
                    }));
                }
                renderOrderDossier(order, shipment, deliveryMap);
            } catch (err) {
                host.innerHTML = `
                  <div class="tc-order-surface text-center py-5">
                    <div class="tc-eyebrow mb-2 text-danger">Order Lookup</div>
                    <h1 class="tc-serif fs-2 mb-2">Unable to Retrieve Order Dossier</h1>
                    <p class="text-muted small mx-auto mb-4" style="max-width:480px;">${TC.escapeHtml(err.message || 'The requested order could not be found.')}</p>
                    <div class="d-flex flex-wrap justify-content-center gap-3">
                      <a href="/pages/orders.html" class="tc-order-action-btn is-primary">Return to My Orders</a>
                      <a href="/pages/shop.html" class="tc-order-action-btn">Explore Timepieces</a>
                    </div>
                  </div>`;
            }
        }

        function renderOrderDossier(o, shipment, deliveryMap = new Map()) {
            document.title = `${mode === 'confirmation' ? 'Order Confirmation' : 'Order Details'} · ${o.orderNumber} · TimeCraft`;

            const isPendingUnpaid = o.status === 'PENDING' && o.paymentStatus === 'PENDING';
            const isConfirmedAndPaid = o.paymentStatus === 'PAID' && !['PENDING', 'CANCELLED', 'FAILED', 'REFUNDED'].includes(o.status);
            const isCodConfirmed = o.paymentMethod === 'COD' && o.paymentStatus === 'PENDING_COD' && !['PENDING', 'CANCELLED', 'FAILED', 'REFUNDED'].includes(o.status);
            const maxCodRaw = TC.getMaxCodAmount();
            const codAllowed = isPendingUnpaid && TC.isOrderCodEligible(o.grandTotal, maxCodRaw);

            // Business rules for header action buttons
            const canDownloadInvoice = !['PENDING', 'FAILED'].includes(o.status) && !(o.status === 'CANCELLED' && o.paymentStatus === 'PENDING');
            const canTrackShipment = !['PENDING', 'FAILED', 'CANCELLED'].includes(o.status);
            const canCancelOrder = Boolean(o.cancellable);
            const canRequestReturn = o.status === 'DELIVERED' && (o.items || []).some(i => Number(i.returnableQuantity || 0) > 0);

            // Dynamic Estimated Delivery Summary (from Shipment or live Delivery Estimation API)
            const estimatedList = Array.from(deliveryMap.values()).filter(e => e && e.serviceable && e.estimatedDeliveryStartDate);
            let dynamicOrderDeliveryText = '';
            if (estimatedList.length > 0) {
                const startIso = estimatedList.map(e => e.estimatedDeliveryStartDate).sort()[0];
                const endIso = estimatedList.map(e => e.estimatedDeliveryEndDate || e.estimatedDeliveryStartDate).sort().slice(-1)[0];
                const rangeStr = TC.formatDeliveryDateRange(startIso, endIso);
                const courier = estimatedList[0].courierName;
                dynamicOrderDeliveryText = `Estimated Delivery: ${rangeStr}${courier ? ' via ' + courier : ''}`;
            }

            let deliveryEstimateText = '';
            if (o.status === 'DELIVERED' && o.deliveredAt) {
                deliveryEstimateText = `Delivered on ${formatDateTime(o.deliveredAt)}`;
            } else if (shipment && shipment.estimatedDeliveryDate) {
                deliveryEstimateText = `Estimated Delivery: ${formatLocalDate(shipment.estimatedDeliveryDate)}${shipment.courierName ? ' via ' + shipment.courierName : ''}`;
            } else if (dynamicOrderDeliveryText) {
                deliveryEstimateText = dynamicOrderDeliveryText;
            } else if (isConfirmedAndPaid || isCodConfirmed) {
                deliveryEstimateText = 'Insured Vault Dispatch · Estimated 3–7 business days';
            }

            // 1. Top Experience Banner (Strictly gated by payment & order status!)
            let topBannerHtml = '';
            if (isConfirmedAndPaid) {
                topBannerHtml = `
                  <section class="tc-order-journey-banner" aria-label="Order Confirmation">
                    <div class="d-flex align-items-start gap-3 gap-md-4" style="max-width:740px;">
                      <div class="tc-order-seal" aria-hidden="true">${ICONS.sealCheck}</div>
                      <div>
                        <div class="tc-eyebrow mb-1" style="color:var(--tc-order-accent);">Order &amp; Payment Confirmed &middot; Ref. <span class="tc-num">${TC.escapeHtml(o.orderNumber)}</span></div>
                        <h2 class="tc-serif fs-2 mb-1">Your timepiece journey begins here.</h2>
                        <p class="small text-muted mb-0">
                          Your payment of <strong class="tc-price text-dark">${formatMoney(o.grandTotal)}</strong> has been verified. Our master watchmakers are preparing your timepiece for insured vault dispatch, and your official tax invoice has been dispatched to <strong>${TC.escapeHtml(o.customerEmail)}</strong>.
                          ${deliveryEstimateText ? `<span class="d-block mt-1 text-dark fw-medium">${TC.escapeHtml(deliveryEstimateText)}</span>` : ''}
                        </p>
                      </div>
                    </div>
                    <div class="d-flex flex-wrap gap-2">
                      <a href="#orderJourneySection" class="tc-order-action-btn is-primary">${ICONS.track}<span>Track Order</span></a>
                    </div>
                  </section>`;
            } else if (isCodConfirmed) {
                topBannerHtml = `
                  <section class="tc-order-journey-banner" aria-label="Cash on Delivery Order Confirmation">
                    <div class="d-flex align-items-start gap-3 gap-md-4" style="max-width:740px;">
                      <div class="tc-order-seal" aria-hidden="true">${ICONS.sealCheck}</div>
                      <div>
                        <div class="tc-eyebrow mb-1" style="color:var(--tc-order-accent);">Cash on Delivery Confirmed &middot; Ref. <span class="tc-num">${TC.escapeHtml(o.orderNumber)}</span></div>
                        <h2 class="tc-serif fs-2 mb-1">Your timepiece reservation is confirmed.</h2>
                        <p class="small text-muted mb-0">
                          Your order is confirmed for Cash on Delivery (<strong class="tc-price text-dark">${formatMoney(o.grandTotal)}</strong> payable upon insured courier handoff).
                          ${deliveryEstimateText ? `<span class="d-block mt-1 text-dark fw-medium">${TC.escapeHtml(deliveryEstimateText)}</span>` : ''}
                        </p>
                      </div>
                    </div>
                    <div class="d-flex flex-wrap gap-2">
                      <a href="#orderJourneySection" class="tc-order-action-btn is-primary">${ICONS.track}<span>Track Order</span></a>
                    </div>
                  </section>`;
            } else if (isPendingUnpaid) {
                topBannerHtml = `
                  <section class="tc-payment-recovery-banner is-cancelled mb-4" aria-label="Complete Pending Payment">
                    <div class="tc-eyebrow mb-1">Payment Verification Pending</div>
                    <h2 class="h4 tc-serif mb-2">Complete Settlement for Your Reserved Timepiece</h2>
                    <p class="small text-muted mb-3">
                      Order <strong class="tc-num text-dark">${TC.escapeHtml(o.orderNumber)}</strong> is reserved in our vault, awaiting payment authorization.
                      ${o.paymentExpiresAt ? `Reservation window active until <strong class="tc-num text-dark">${TC.escapeHtml(formatDateTime(o.paymentExpiresAt))}</strong>.` : ''}
                      Complete payment online${codAllowed ? ' or switch this order to Cash on Delivery' : ''} without creating a duplicate order.
                    </p>
                    <div class="tc-recovery-actions">
                      <button type="button" id="odRetryPayBtn" class="tc-order-action-btn is-primary">Retry via Razorpay &middot; ${formatMoney(o.grandTotal)}</button>
                      <button type="button" id="odPayuBtn" class="tc-order-action-btn">Pay with PayU</button>
                      ${codAllowed ? `<button type="button" id="odSwitchCodBtn" class="tc-order-action-btn">Switch to Cash on Delivery (COD)</button>` : ''}
                    </div>
                    ${!codAllowed ? `
                      <div class="small text-muted mt-2">
                        Cash on Delivery is available up to <strong class="tc-price">${TC.formatInr(maxCodRaw)}</strong>. For higher-value acquisitions, please complete online settlement or <a href="/pages/contact.html?order=${encodeURIComponent(o.orderNumber)}" class="fw-semibold">contact Concierge support</a>.
                      </div>` : ''}
                  </section>`;
            } else if (o.status === 'CANCELLED') {
                topBannerHtml = `
                  <section class="tc-order-state-banner is-danger" aria-label="Order Cancelled Notice">
                    <div class="d-flex align-items-start gap-3">
                      <div style="color:var(--tc-danger);margin-top:2px;">${ICONS.alert}</div>
                      <div>
                        <div class="tc-eyebrow text-danger mb-1">Order Cancelled</div>
                        <div class="fw-medium text-dark mb-1">This order was cancelled${o.cancelledAt ? ' on ' + TC.escapeHtml(formatDateTime(o.cancelledAt)) : ''}.</div>
                        ${o.cancelReason ? `<div class="small text-muted">Recorded reason: ${TC.escapeHtml(o.cancelReason)}</div>` : ''}
                      </div>
                    </div>
                  </section>`;
            } else if (o.status === 'FAILED' || o.paymentStatus === 'FAILED') {
                topBannerHtml = `
                  <section class="tc-order-state-banner is-danger" aria-label="Payment Failed Notice">
                    <div class="d-flex align-items-start gap-3">
                      <div style="color:var(--tc-danger);margin-top:2px;">${ICONS.alert}</div>
                      <div>
                        <div class="tc-eyebrow text-danger mb-1">Payment Unsuccessful</div>
                        <div class="fw-medium text-dark mb-1">Payment authorization was not completed before the reservation window closed.</div>
                        <div class="small text-muted">No charges were captured by TimeCraft. Explore our archive to place a fresh reservation.</div>
                      </div>
                    </div>
                  </section>`;
            } else if (o.status === 'REFUNDED' || o.paymentStatus === 'REFUNDED' || o.paymentStatus === 'PARTIALLY_REFUNDED') {
                topBannerHtml = `
                  <section class="tc-order-state-banner is-refunded" aria-label="Refund Status Notice">
                    <div class="tc-eyebrow mb-1" style="color:var(--tc-order-accent);">Settlement Refunded</div>
                    <div class="fw-medium text-dark mb-1">Refund has been processed for Order ${TC.escapeHtml(o.orderNumber)}.</div>
                    <div class="small text-muted">Funds are credited back to your original payment instrument per issuing bank timelines.</div>
                  </section>`;
            } else if (o.status === 'RETURN_REQUESTED' || o.status === 'RETURNED') {
                topBannerHtml = `
                  <section class="tc-order-state-banner is-refunded" aria-label="Return Status Notice">
                    <div class="tc-eyebrow mb-1" style="color:var(--tc-order-accent);">7-Day Collector Inspection Return</div>
                    <div class="fw-medium text-dark mb-1">${o.status === 'RETURNED' ? 'Returned timepiece received at the TimeCraft Vault.' : 'Your return inspection request is under concierge review.'}</div>
                  </section>`;
            }

            // 2. Build 7-Stage Visual Journey Model
            const journey = buildSevenStageModel(o, shipment);
            const totalUnits = (o.items || []).reduce((acc, it) => acc + Number(it.quantity || 0), 0);

            // 3. Financial Summary Calculation strictly from immutable saved Order fields
            const hasCoupon = Boolean(o.couponCode && String(o.couponCode).trim());
            const savedDiscount = Number(o.discountTotal || 0);
            const productDiscountVal = (!hasCoupon && savedDiscount > 0) ? savedDiscount : 0;
            const couponDiscountVal = hasCoupon ? savedDiscount : 0;
            const uniformTaxRate = (o.items && o.items.length && o.items[0].taxRate !== undefined && o.items[0].taxRate !== null)
                ? Number(o.items[0].taxRate)
                : null;
            const gstLabel = (o.taxInclusive ? 'Included GST' : 'GST') + (uniformTaxRate ? ` (${uniformTaxRate}%)` : '');

            // Check if any historical adjustment exists beyond subtotal - discount + shipping (+ exclusive tax)
            const expectedGrand = Number(o.subtotal || 0) - savedDiscount + Number(o.shippingTotal || 0) + (o.taxInclusive ? 0 : Number(o.taxTotal || 0));
            const adjustmentDiff = Math.round((Number(o.grandTotal || 0) - expectedGrand) * 100) / 100;

            host.innerHTML = `
              <nav class="tc-order-breadcrumb" aria-label="Breadcrumb">
                <a href="/index.html">Home</a>
                <span>/</span>
                <a href="/pages/orders.html">Collector Orders</a>
                <span>/</span>
                <span class="text-dark tc-num">${TC.escapeHtml(o.orderNumber)}</span>
              </nav>

              ${topBannerHtml}

              <!-- PREMIUM ORDER HEADER -->
              <header class="tc-order-header-card">
                <div class="tc-order-header-top">
                  <div>
                    <div class="tc-eyebrow" style="color:var(--tc-order-accent);">
                      ${mode === 'confirmation' && (isConfirmedAndPaid || isCodConfirmed) ? 'Order Confirmation' : 'Order Details'}
                    </div>
                    <h1 class="tc-order-id-heading tc-num">Order ${TC.escapeHtml(o.orderNumber)}</h1>
                  </div>
                  <div class="tc-order-actions-bar">
                    ${canDownloadInvoice ? `
                      <button type="button" class="tc-order-action-btn" id="odDownloadInvoiceBtn">
                        ${ICONS.invoice}<span>Download Invoice</span>
                      </button>` : ''}
                    ${canTrackShipment ? `
                      <a href="/pages/order-tracking.html?order=${encodeURIComponent(o.orderNumber)}" class="tc-order-action-btn">
                        ${ICONS.track}<span>Track Shipment</span>
                      </a>` : ''}
                    ${canRequestReturn ? `
                      <a href="/pages/return-request.html?orderId=${encodeURIComponent(o.id)}" class="tc-order-action-btn">
                        ${ICONS.returnIcon}<span>Request Return</span>
                      </a>` : ''}
                    ${canCancelOrder ? `
                      <button type="button" class="tc-order-action-btn is-danger" id="odCancelOrderBtn">
                        ${ICONS.cancel}<span>Cancel Order</span>
                      </button>` : ''}
                  </div>
                </div>

                <div class="tc-order-meta-strip">
                  <div class="tc-order-meta-item">
                    <div class="tc-order-meta-label">Order ID</div>
                    <div class="tc-order-meta-val tc-num">${TC.escapeHtml(o.orderNumber)}</div>
                  </div>
                  <div class="tc-order-meta-item">
                    <div class="tc-order-meta-label">Order Date</div>
                    <div class="tc-order-meta-val tc-num">${TC.escapeHtml(formatDateTime(o.placedAt))}</div>
                  </div>
                  <div class="tc-order-meta-item">
                    <div class="tc-order-meta-label">Payment Method</div>
                    <div class="tc-order-meta-val">${TC.escapeHtml(humanizePaymentMethod(o.paymentMethod))}</div>
                  </div>
                  <div class="tc-order-meta-item">
                    <div class="tc-order-meta-label">Payment Status</div>
                    <div>
                      <span class="tc-status-pill ${getPaymentTone(o.paymentStatus)}">
                        <span class="tc-status-pill-dot"></span>
                        <span>${TC.escapeHtml(humanizePaymentStatus(o.paymentStatus))}</span>
                      </span>
                    </div>
                  </div>
                  <div class="tc-order-meta-item">
                    <div class="tc-order-meta-label">Order Status</div>
                    <div>
                      <span class="tc-status-pill ${getStatusTone(o.status, o.paymentStatus)}">
                        <span class="tc-status-pill-dot"></span>
                        <span>${TC.escapeHtml(humanizeStatus(o.status))}</span>
                      </span>
                    </div>
                  </div>
                </div>
              </header>

              <!-- 2-COLUMN LUXURY CONCIERGE GRID -->
              <div class="tc-order-grid">
                <!-- LEFT MAIN COLUMN: TIMELINE + ORDERED TIMEPIECES -->
                <div class="d-flex flex-column gap-4">

                  <!-- 1. VISUAL ORDER JOURNEY TIMELINE -->
                  <section class="tc-order-surface" id="orderJourneySection" aria-label="Order Status Timeline">
                    <div class="tc-order-section-head">
                      <div>
                        <div class="tc-eyebrow mb-1" style="color:var(--tc-order-accent);">Provenance &amp; Custody Telemetry</div>
                        <h2 class="tc-order-section-title">Order Journey</h2>
                      </div>
                      ${shipment && shipment.trackingNumber ? `
                        <div class="small text-muted tc-num">
                          ${TC.escapeHtml(shipment.courierName || 'Courier')} &middot; Tracking <strong>${TC.escapeHtml(shipment.trackingNumber)}</strong>
                          ${shipment.trackingUrl ? ` &middot; <a href="${TC.escapeHtml(shipment.trackingUrl)}" target="_blank" rel="noopener noreferrer" class="text-decoration-underline">Carrier Link</a>` : ''}
                        </div>` : (deliveryEstimateText ? `<div class="small text-muted">${TC.escapeHtml(deliveryEstimateText)}</div>` : '')}
                    </div>

                    <div class="tc-journey-track-wrap">
                      <div class="tc-journey-rail" aria-hidden="true">
                        <div class="tc-journey-rail-fill" id="tcJourneyRailFill" data-progress="${journey.progressPercent}"></div>
                      </div>
                      <ol class="tc-journey-steps">
                        ${journey.steps.map(st => {
                            const cls = st.state === 'completed'
                                ? 'is-completed'
                                : (st.state === 'current' ? 'is-current' : (st.state === 'halted' ? 'is-halted' : ''));
                            const nodeInner = st.state === 'completed'
                                ? ICONS.stepCheck
                                : (st.state === 'halted' ? '&times;' : `<span class="tc-num">${st.index}</span>`);
                            const timeLabel = st.timestamp
                                ? formatShortStepTime(st.timestamp)
                                : (st.state === 'current' ? 'In Progress' : (st.state === 'halted' ? 'Stopped' : 'Pending'));
                            return `
                              <li class="tc-journey-step ${cls}">
                                <span class="tc-journey-node" aria-hidden="true">${nodeInner}</span>
                                <div class="min-w-0">
                                  <div class="tc-journey-label">${TC.escapeHtml(st.label)}</div>
                                  <div class="tc-journey-time">${TC.escapeHtml(timeLabel)}</div>
                                  <div class="tc-journey-desc">${TC.escapeHtml(st.desc)}</div>
                                </div>
                              </li>`;
                        }).join('')}
                      </ol>
                    </div>

                    ${journey.unifiedEvents.length ? `
                      <div class="tc-event-feed">
                        <div class="tc-eyebrow mb-2" style="color:var(--tc-muted);font-size:9.5px;">Chronological Dispatch &amp; Status Ledger</div>
                        <div>
                          ${journey.unifiedEvents.map((ev, idx) => `
                            <div class="tc-event-row ${idx === 0 ? 'is-latest' : ''}">
                              <div>
                                <div class="fw-semibold text-dark">
                                  ${idx === 0 ? '<span class="tc-eyebrow me-2" style="color:var(--tc-order-accent);font-size:9px;">Latest</span>' : ''}
                                  ${TC.escapeHtml(ev.title)}
                                </div>
                                ${ev.subtitle ? `<div class="small text-muted mt-1">${TC.escapeHtml(ev.subtitle)}</div>` : ''}
                              </div>
                              <div class="small text-muted tc-num text-nowrap">${TC.escapeHtml(formatDateTime(ev.time))}</div>
                            </div>`).join('')}
                        </div>
                      </div>` : ''}
                  </section>

                  <!-- 2. ORDERED TIMEPIECES SHOWCASE -->
                  <section class="tc-order-surface" aria-label="Ordered Timepieces">
                    <div class="tc-order-section-head">
                      <div>
                        <div class="tc-eyebrow mb-1" style="color:var(--tc-order-accent);">Acquired References</div>
                        <h2 class="tc-order-section-title">Ordered Timepieces (${totalUnits})</h2>
                      </div>
                      <span class="small text-muted">Saved Purchase Snapshot</span>
                    </div>

                    <div>
                      ${(o.items || []).map(item => {
                          const specs = extractSpecsForWatch(item);
                          const watchHref = item.productSlug
                              ? `/pages/product-details.html?slug=${encodeURIComponent(item.productSlug)}`
                              : (item.productId ? `/pages/product-details.html?id=${encodeURIComponent(item.productId)}` : null);
                          const titleHtml = watchHref
                              ? `<a href="${watchHref}">${TC.escapeHtml(item.productName)}</a>`
                              : TC.escapeHtml(item.productName);
                          const imgTag = `<img src="${TC.escapeHtml(item.imageUrl || FALLBACK_WATCH_IMG)}"
                                               alt="${TC.escapeHtml(item.brandName + ' ' + item.productName)}"
                                               loading="lazy"
                                               onerror="this.onerror=null;this.src='${FALLBACK_WATCH_IMG}';">`;
                          return `
                            <article class="tc-ordered-watch-item">
                              ${watchHref
                                  ? `<a href="${watchHref}" class="tc-ordered-watch-portrait">${imgTag}</a>`
                                  : `<div class="tc-ordered-watch-portrait">${imgTag}</div>`}
                              <div class="min-w-0">
                                <div class="tc-ordered-watch-brand">${TC.escapeHtml(item.brandName)}</div>
                                <h3 class="tc-ordered-watch-name">${titleHtml}</h3>
                                <div class="tc-ordered-spec-grid">
                                  ${item.modelName ? `<div class="tc-ordered-spec-item"><span>Model</span><strong class="tc-num fw-medium">${TC.escapeHtml(item.modelName)}</strong></div>` : ''}
                                  <div class="tc-ordered-spec-item"><span>SKU</span><strong class="tc-num fw-medium">${TC.escapeHtml(item.sku)}</strong></div>
                                  <div class="tc-ordered-spec-item"><span>Dial</span>${TC.escapeHtml(specs.dial)}</div>
                                  <div class="tc-ordered-spec-item"><span>Strap</span>${TC.escapeHtml(specs.strap)}</div>
                                  ${item.movementType ? `<div class="tc-ordered-spec-item"><span>Calibre</span>${TC.escapeHtml(item.movementType)}${item.caseDiameterMm ? ' · ' + TC.escapeHtml(item.caseDiameterMm) + ' mm' : ''}</div>` : ''}
                                  <div class="tc-ordered-spec-item"><span>Qty</span><strong class="tc-num fw-semibold">${item.quantity}</strong></div>
                                </div>
                                ${(() => {
                                    const itemEst = item.productId ? deliveryMap.get(Number(item.productId)) : null;
                                    if (!itemEst || o.status === 'DELIVERED' || ['CANCELLED', 'FAILED', 'REFUNDED', 'RETURNED'].includes(o.status)) return '';
                                    return TC.renderDeliveryLine(itemEst, { className: 'tc-bag-delivery-line', showDates: Boolean(itemEst.pinCode) });
                                })()}
                                ${Number(item.returnedQuantity || 0) > 0 ? `<div class="small text-muted mt-2">Returned quantity: <span class="tc-num">${item.returnedQuantity}</span></div>` : ''}
                              </div>
                              <div class="tc-ordered-watch-pricing tc-price">
                                <div>
                                  <div class="tc-order-meta-label mb-1">Line Total</div>
                                  <div class="fs-5 fw-semibold text-dark">${formatMoney(item.lineTotal)}</div>
                                </div>
                                <div class="small text-muted mt-1">
                                  Unit: ${formatMoney(item.unitPrice)}${item.quantity > 1 ? ` &times; ${item.quantity}` : ''}
                                </div>
                              </div>
                            </article>`;
                      }).join('')}
                    </div>
                  </section>
                </div>

                <!-- RIGHT SIDEBAR: DELIVERY DESTINATION + FINANCIAL SUMMARY -->
                <aside class="tc-order-sidebar">

                  <!-- 3. DELIVERY ADDRESS SECTION -->
                  <section class="tc-order-surface" aria-label="Delivery Information">
                    <div class="tc-order-section-head mb-3 pb-3">
                      <div>
                        <div class="tc-eyebrow mb-1" style="color:var(--tc-order-accent);">Insured Vault Destination</div>
                        <h2 class="tc-order-section-title" style="font-size:21px;">Delivery Address</h2>
                      </div>
                    </div>
                    <div class="tc-delivery-block">
                      <div class="tc-delivery-icon" aria-hidden="true">${ICONS.location}</div>
                      <address class="mb-0 small" style="font-style:normal;line-height:1.7;color:var(--tc-ink-soft);">
                        <div class="fw-semibold text-dark" style="font-size:14px;">${TC.escapeHtml(o.shipFullName)}</div>
                        <div>${TC.escapeHtml(o.shipLine1)}${o.shipLine2 ? ', ' + TC.escapeHtml(o.shipLine2) : ''}</div>
                        ${o.shipLandmark ? `<div class="text-muted">Landmark: ${TC.escapeHtml(o.shipLandmark)}</div>` : ''}
                        <div>${TC.escapeHtml(o.shipCity)}, ${TC.escapeHtml(o.shipState)} <span class="tc-num">${TC.escapeHtml(o.shipPostalCode)}</span></div>
                        <div>${TC.escapeHtml(formatCountryName(o.shipCountry))}</div>
                        <div class="pt-2 mt-2 text-dark tc-num" style="border-top:1px solid var(--tc-order-border);">
                          <span class="text-muted" style="font-size:11px;letter-spacing:0.1em;text-transform:uppercase;">Contact:</span>
                          ${TC.escapeHtml(o.shipPhone)}
                        </div>
                      </address>
                    </div>
                    ${o.customerNote ? `
                      <div class="mt-3 pt-3 small text-muted" style="border-top:1px solid var(--tc-order-border);">
                        <span class="tc-order-meta-label d-block mb-1">Collector Delivery Note</span>
                        ${TC.escapeHtml(o.customerNote)}
                      </div>` : ''}
                  </section>

                  <!-- 4. PAYMENT & ORDER SUMMARY (IMMUTABLE SNAPSHOT) -->
                  <section class="tc-order-surface" aria-label="Payment and Financial Summary">
                    <div class="tc-order-section-head mb-3 pb-3">
                      <div>
                        <div class="tc-eyebrow mb-1" style="color:var(--tc-order-accent);">Financial Breakdown</div>
                        <h2 class="tc-order-section-title" style="font-size:21px;">Order Summary</h2>
                      </div>
                    </div>

                    <div class="tc-price">
                      <div class="tc-finance-row">
                        <span>Subtotal (${totalUnits} ${totalUnits === 1 ? 'Timepiece' : 'Timepieces'})</span>
                        <span class="fw-medium text-dark">${formatMoney(o.subtotal)}</span>
                      </div>
                      <div class="tc-finance-row ${productDiscountVal > 0 ? 'is-discount' : ''}">
                        <span>Product Discount</span>
                        <span>${productDiscountVal > 0 ? '&minus; ' + formatMoney(productDiscountVal) : formatMoney(0)}</span>
                      </div>
                      ${hasCoupon ? `
                      <div class="tc-finance-row is-discount">
                        <span>Coupon Discount (<span class="tc-num">${TC.escapeHtml(o.couponCode)}</span>)</span>
                        <span class="fw-medium">&minus; ${formatMoney(couponDiscountVal)}</span>
                      </div>` : ''}
                      <div class="tc-finance-row">
                        <span>${TC.escapeHtml(gstLabel)}</span>
                        <span>${formatMoney(o.taxTotal)}</span>
                      </div>
                      <div class="tc-finance-row">
                        <span>Insured Shipping</span>
                        <span class="${Number(o.shippingTotal || 0) === 0 ? 'fw-medium text-dark' : ''}">
                          ${Number(o.shippingTotal || 0) === 0 ? 'Complimentary' : formatMoney(o.shippingTotal)}
                        </span>
                      </div>
                      ${Math.abs(adjustmentDiff) >= 0.01 ? `
                      <div class="tc-finance-row">
                        <span>Other Applicable Charges</span>
                        <span>${adjustmentDiff > 0 ? formatMoney(adjustmentDiff) : '&minus; ' + formatMoney(Math.abs(adjustmentDiff))}</span>
                      </div>` : ''}

                      <div class="tc-finance-total">
                        <div>
                          <div class="text-uppercase fw-semibold text-dark" style="font-size:11px;letter-spacing:0.16em;">Grand Total</div>
                          <div class="small text-muted" style="font-size:11px;">
                            ${o.paymentStatus === 'PAID'
                                ? 'Paid in INR · Immutable Order Snapshot'
                                : (o.paymentMethod === 'COD' ? 'Payable on Delivery (INR)' : 'Total Payable (INR)')}
                          </div>
                        </div>
                        <div class="tc-serif fs-2 fw-semibold text-dark">${formatMoney(o.grandTotal)}</div>
                      </div>
                    </div>
                  </section>

                  <!-- 5. PRIVATE CONCIERGE DESK -->
                  <section class="tc-order-surface" style="background:var(--tc-alabaster);" aria-label="Concierge Support">
                    <div class="tc-eyebrow mb-1" style="color:var(--tc-order-accent);">Private Client Desk</div>
                    <div class="tc-serif fs-4 mb-1">Need Assistance With Your Order?</div>
                    <p class="small text-muted mb-3">
                      Our horological concierge specialists are available for insured transit updates, bracelet sizing guidance, and documentation requests.
                    </p>
                    <div class="d-flex flex-wrap gap-2">
                      <a href="/pages/contact.html?order=${encodeURIComponent(o.orderNumber)}" class="tc-order-action-btn w-100">Contact Concierge</a>
                      <a href="/pages/orders.html" class="tc-order-action-btn w-100" style="background:transparent;">All Collector Orders</a>
                    </div>
                  </section>
                </aside>
              </div>`;

            // Animate horizontal / vertical timeline rail smoothly after paint
            requestAnimationFrame(() => {
                const railFill = document.getElementById('tcJourneyRailFill');
                if (railFill) {
                    const pct = Number(railFill.getAttribute('data-progress') || 0);
                    railFill.style.width = pct + '%';
                    railFill.style.height = pct + '%';
                }
            });

            // Bind Header Action Buttons
            document.getElementById('odDownloadInvoiceBtn')?.addEventListener('click', (ev) => {
                downloadOrderInvoice(o.orderNumber, ev.currentTarget);
            });

            document.getElementById('odCancelOrderBtn')?.addEventListener('click', () => {
                openCancelOrderModal(o, loadAndRender);
            });

            // Bind Pending Order Payment Recovery Actions (Razorpay, PayU, COD Fallback)
            document.getElementById('odRetryPayBtn')?.addEventListener('click', async (ev) => {
                const btn = ev.currentTarget;
                btn.disabled = true;
                btn.textContent = 'Opening Razorpay…';
                try {
                    await TC.launchOrderRazorpayPayment(o, {
                        retry: true,
                        onSuccess: (confirmed) => {
                            window.location.href = `/pages/order-confirmation.html?order=${encodeURIComponent((confirmed || o).orderNumber)}`;
                        },
                        onFailure: async (failCtx) => {
                            TC.toast(failCtx.reason || 'Payment incomplete — your order reservation remains active', 'error');
                            await loadAndRender();
                        }
                    });
                } catch (err) {
                    btn.disabled = false;
                    btn.textContent = 'Retry via Razorpay · ' + formatMoney(o.grandTotal);
                    TC.toast(err.message, 'error');
                }
            });

            document.getElementById('odPayuBtn')?.addEventListener('click', async (ev) => {
                const btn = ev.currentTarget;
                btn.disabled = true;
                btn.textContent = 'Redirecting to PayU…';
                try {
                    await TC.launchOrderPayuPayment(o, { retry: true });
                } catch (err) {
                    btn.disabled = false;
                    btn.textContent = 'Pay with PayU';
                    TC.toast(err.message, 'error');
                }
            });

            document.getElementById('odSwitchCodBtn')?.addEventListener('click', async (ev) => {
                const btn = ev.currentTarget;
                btn.disabled = true;
                btn.textContent = 'Confirming COD…';
                try {
                    const updated = await TC.switchOrderToCod(o.orderNumber);
                    window.location.href = `/pages/order-confirmation.html?order=${encodeURIComponent(updated.orderNumber)}&method=COD`;
                } catch (err) {
                    btn.disabled = false;
                    btn.textContent = 'Switch to Cash on Delivery (COD)';
                    TC.toast(err.message, 'error');
                }
            });
        }

        await loadAndRender();
    }

    window.TCOrderConcierge = { init };
})();
