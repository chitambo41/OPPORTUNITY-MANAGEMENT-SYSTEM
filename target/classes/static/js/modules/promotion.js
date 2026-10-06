/* ==========================================================================
   Yearly Promotion (admin) — preview dry run + execute with summary.
   ========================================================================== */
(function () {
    Router.register('/promotion', {
        title: 'Yearly Promotion',
        roles: ['ADMIN'],
        render,
    });

    async function render(el) {
        const years = await API.admin.years.list();
        el.innerHTML = `
        <div class="card">
            <h3>⬆️ Promote students to the next year</h3>
            <p style="color:var(--text-muted)">
                Baby → KG1 → KG2 → KG3 → <b>COMPLETE</b>. Missing classes for the target year are created automatically.
                Students already placed in the target year and removed students are skipped.
            </p>
            <div class="filters">
                <div class="form-group"><label>Promote into year</label>
                    <select id="promo-year">
                        <option value="">Select year…</option>
                        ${years.map(y => `<option value="${y.year}">${y.year}${y.current ? ' (current)' : ''}</option>`).join('')}
                    </select>
                </div>
                <button class="btn btn-outline" id="promo-preview" disabled>👁 Preview promotion</button>
                <button class="btn btn-primary" id="promo-run" disabled>Run promotion</button>
            </div>
            <div id="promo-result"></div>
        </div>`;

        const yearSel = el.querySelector('#promo-year');
        const previewBtn = el.querySelector('#promo-preview');
        const runBtn = el.querySelector('#promo-run');
        yearSel.addEventListener('change', () => {
            const on = !!yearSel.value;
            previewBtn.disabled = !on;
            runBtn.disabled = !on;
        });

        previewBtn.addEventListener('click', async () => {
            const box = el.querySelector('#promo-result');
            box.innerHTML = `<div class="loading"><div class="spinner"></div>Calculating…</div>`;
            try {
                const res = await API.admin.promotion.preview(Number(yearSel.value));
                showSummary(box, res, true, el);
            } catch (err) {
                box.innerHTML = `<div class="empty-state"><div class="icon">⚠️</div>${UI.esc(err.message)}</div>`;
            }
        });

        runBtn.addEventListener('click', async () => {
            const toYear = Number(yearSel.value);
            const ok = await UI.confirmDialog({
                title: 'Run promotion',
                message: `Promote all eligible students into <b>${toYear}</b>? This updates enrollments and completes KG3 students. This cannot be undone.`,
                confirmText: 'Run promotion',
                danger: false,
            });
            if (!ok) return;
            const box = el.querySelector('#promo-result');
            box.innerHTML = `<div class="loading"><div class="spinner"></div>Promoting…</div>`;
            try {
                const res = await API.admin.promotion.run(toYear);
                showSummary(box, res, false, el);
                UI.toast('Promotion complete');
            } catch (err) {
                box.innerHTML = `<div class="empty-state"><div class="icon">⚠️</div>${UI.esc(err.message)}</div>`;
            }
        });
    }

    function showSummary(box, res, isPreview, el) {
        box.innerHTML = `
        <div class="cards-grid">
            <div class="stat-card"><div class="stat-label">${isPreview ? 'Would promote' : 'Promoted'}</div><div class="stat-value">${res.promotedCount}</div></div>
            <div class="stat-card green"><div class="stat-label">Completed (after KG3)</div><div class="stat-value">${res.completedCount}</div></div>
            <div class="stat-card orange"><div class="stat-label">Skipped</div><div class="stat-value">${res.skippedCount}</div></div>
            <div class="stat-card green"><div class="stat-label">${isPreview ? 'Classes to create' : 'Classes created'}</div><div class="stat-value">${res.createdClasses}</div></div>
        </div>
        ${UI.table({
            columns: [
                { label: 'Student', key: 'studentName' },
                { label: 'Adm #', key: 'admissionNumber' },
                { label: 'From', render: r => UI.esc(`${r.fromLevel} (${r.fromClass || ''})`) },
                { label: 'To', render: r => UI.esc(r.toLevel || '—') },
                { label: 'Action', render: r => {
                    const map = {
                        PROMOTED: ['PROMOTED', 'green'],
                        COMPLETE: ['COMPLETE', 'blue'],
                        SKIPPED_ALREADY_PLACED: ['ALREADY PLACED', 'orange'],
                        SKIPPED_REMOVED: ['REMOVED', 'gray'],
                    }[r.action] || [r.action, 'gray'];
                    return UI.badge(map[0], map[1]);
                } },
            ],
            rows: res.lines,
            empty: 'No students in the source year',
        })}`;
    }
})();
