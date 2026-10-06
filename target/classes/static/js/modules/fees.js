/* ==========================================================================
   School Fees (admin) — fee structures, payments, statuses, summary, receipts, statements.
   ========================================================================== */
(function () {
    Router.register('/fees', {
        title: 'School Fees',
        roles: ['ADMIN'],
        render,
    });

    const state = { termId: '', classId: '', status: '' };

    async function render(el) {
        const [terms, classes] = await Promise.all([
            API.admin.years.list().then(ys => ys.flatMap(y => (y.terms || []).map(t => ({ ...t, year: y.year })))),
            API.admin.classes.list(),
        ]);

        el.innerHTML = `
        <div class="cards-grid" id="fee-cards"></div>
        <div class="card">
            <div class="tabs">
                <button class="tab active" data-tab="status">Fee status</button>
                <button class="tab" data-tab="payments">Record payment</button>
                <button class="tab" data-tab="structures">Fee structures</button>
            </div>
            <div id="fee-panel"></div>
        </div>`;

        const panel = el.querySelector('#fee-panel');
        const defaultTerm = terms.find(t => t.current) || terms[0];
        state.termId = defaultTerm ? defaultTerm.id : '';

        // Tabs
        el.querySelectorAll('.tab').forEach(t => t.addEventListener('click', () => {
            el.querySelectorAll('.tab').forEach(x => x.classList.remove('active'));
            t.classList.add('active');
            if (t.dataset.tab === 'status') statusTab(panel, terms, classes);
            if (t.dataset.tab === 'payments') paymentTab(panel, terms);
            if (t.dataset.tab === 'structures') structuresTab(panel, terms, classes);
        }));

        await statusTab(panel, terms, classes);
        await loadSummary(el);
    }

    async function loadSummary(el) {
        const cards = el.querySelector('#fee-cards');
        if (!state.termId) {
            cards.innerHTML = '';
            return;
        }
        const s = await API.admin.fees.summary(state.termId);
        cards.innerHTML = `
            <div class="stat-card"><div class="stat-label">Expected (term)</div><div class="stat-value">${UI.money(s.expected)}</div></div>
            <div class="stat-card green"><div class="stat-label">Collected</div><div class="stat-value">${UI.money(s.collected)}</div></div>
            <div class="stat-card red"><div class="stat-label">Outstanding</div><div class="stat-value">${UI.money(s.outstanding)}</div></div>`;
    }

    // ---------------- Tab: per-student status ----------------
    function statusTab(panel, terms, classes) {
        panel.innerHTML = `
        <div class="filters">
            <div class="form-group"><label>Term</label>
                <select id="s-term">${terms.map(t => `<option value="${t.id}" ${String(state.termId) === String(t.id) ? 'selected' : ''}>${t.number.replace('_', ' ')} — ${t.year}${t.current ? ' (current)' : ''}</option>`).join('')}</select></div>
            <div class="form-group"><label>Class</label>
                <select id="s-class"><option value="">All classes</option>
                    ${classes.map(c => `<option value="${c.id}">${UI.esc(c.name)}</option>`).join('')}</select></div>
            <div class="form-group"><label>Status</label>
                <select id="s-status">
                    <option value="">All</option>
                    <option value="PAID">PAID</option>
                    <option value="PARTIAL">PARTIAL</option>
                    <option value="NOT_PAID">NOT PAID</option>
                </select></div>
        </div>
        <div id="status-table"></div>`;

        panel.querySelector('#s-term').value = state.termId;
        const load = async () => {
            state.termId = panel.querySelector('#s-term').value;
            state.classId = panel.querySelector('#s-class').value;
            state.status = panel.querySelector('#s-status').value;
            const rows = await API.admin.fees.statuses({
                termId: state.termId,
                classId: state.classId || undefined,
                status: state.status || undefined,
            });
            panel.querySelector('#status-table').innerHTML = UI.table({
                columns: [
                    { label: 'Student', key: 'studentName' },
                    { label: 'Adm #', key: 'admissionNumber' },
                    { label: 'Class', key: 'className' },
                    { label: 'Expected', render: r => UI.money(r.expected) },
                    { label: 'Paid', render: r => UI.money(r.paid) },
                    { label: 'Balance', render: r => UI.money(r.balance) },
                    { label: 'Status', render: r => (UI.statusBadge[r.status] ? UI.statusBadge[r.status]() : r.status) },
                    { label: '', tdClass: 'actions', render: r => `
                        <button class="btn btn-outline btn-sm" data-pay="${r.studentId}" data-name="${UI.esc(r.studentName)}">Payment</button>
                        <button class="btn btn-outline btn-sm" data-stmt="${r.studentId}">Statement</button>` },
                ],
                rows,
                empty: 'No students match these filters',
            });

            panel.querySelectorAll('[data-pay]').forEach(b => b.addEventListener('click', () =>
                paymentDialog(Number(b.dataset.pay), b.dataset.name, () => { statusTabRefresh(panel); window.Router.handle(); })));
            panel.querySelectorAll('[data-stmt]').forEach(b => b.addEventListener('click', () =>
                statementDialog(Number(b.dataset.stmt))));
        };
        panel.querySelector('#s-term').addEventListener('change', load);
        panel.querySelector('#s-class').addEventListener('change', load);
        panel.querySelector('#s-status').addEventListener('change', load);
        load();
    }

    function statusTabRefresh(panel) {
        panel.querySelector('#s-term').dispatchEvent(new Event('change'));
    }

    // ---------------- Tab: record payment ----------------
    function paymentTab(panel, terms) {
        panel.innerHTML = `
        <p style="color:var(--text-muted)">Use the <b>Fee status</b> tab: each row has a "Payment" action so the amount can be checked against the balance. Overpayments are rejected.</p>`;
    }

    async function paymentDialog(studentId, studentName, done) {
        UI.formModal({
            title: `Record payment — ${studentName}`,
            submitText: 'Record payment',
            fields: [
                { label: 'Amount', name: 'amount', type: 'number', required: true, min: 0.01, step: '0.01' },
                { label: 'Payment date', name: 'paymentDate', type: 'date', required: true, value: UI.today() },
                { label: 'Method', name: 'method', type: 'select', required: true, options: [
                    { value: 'CASH', label: 'Cash' },
                    { value: 'BANK', label: 'Bank' },
                    { value: 'MOBILE_MONEY', label: 'Mobile money' },
                ] },
                { label: 'Note', name: 'note' },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['amount', 'paymentDate', 'method']);
                try {
                    const res = await API.admin.fees.pay({
                        studentId,
                        termId: Number(state.termId),
                        amount: Number(data.amount),
                        paymentDate: data.paymentDate,
                        method: data.method,
                        note: data.note || null,
                    });
                    UI.toast(`Payment recorded — receipt ${res.receiptNumber}`, 'success', 6000);
                    close();
                    showReceipt(res.receiptNumber);
                    done();
                } catch (err) {
                    UI.toast(err.message, 'error', 6000);
                }
            },
        });
    }

    async function showReceipt(receiptNumber) {
        const r = await API.admin.fees.receipt(receiptNumber);
        const root = document.getElementById('print-root');
        root.innerHTML = `
        <div class="receipt-sheet">
            <h2>OPPORTUNITY NURSERY SCHOOL</h2>
            <div class="rc-sub">Official Fee Receipt</div>
            <table>
                <tr><td>Receipt #:</td><td class="r">${UI.esc(r.receiptNumber)}</td></tr>
                <tr><td>Date:</td><td class="r">${UI.fmtDate(r.paymentDate)}</td></tr>
                <tr><td>Student:</td><td class="r">${UI.esc(r.studentName)}</td></tr>
                <tr><td>Admission #:</td><td class="r">${UI.esc(r.admissionNumber)}</td></tr>
                <tr><td>Term:</td><td class="r">${UI.esc((r.termNumber || '').replace('_', ' '))} ${r.year}</td></tr>
                <tr><td>Method:</td><td class="r">${UI.esc(r.method)}</td></tr>
                <tr class="total-row"><td><b>Amount paid:</b></td><td class="r">${UI.money(r.amount)}</td></tr>
            </table>
            <div class="thanks">Thank you! — Keep this receipt safe.</div>
        </div>`;
        setTimeout(() => window.print(), 60);
        UI.confirmDialog({
            title: 'Receipt ready',
            message: `Receipt <b>${receiptNumber}</b> was sent to the printer dialog. Close this when done.`,
            danger: false, confirmText: 'Done',
        });
    }

    // ---------------- Tab: fee structures ----------------
    async function structuresTab(panel, terms, classes) {
        panel.innerHTML = `
        <div class="filters">
            <div class="form-group"><label>Term</label>
                <select id="f-term">${terms.map(t => `<option value="${t.id}" ${String(state.termId) === String(t.id) ? 'selected' : ''}>${t.number.replace('_', ' ')} — ${t.year}</option>`).join('')}</select></div>
            <button class="btn btn-primary" id="f-set">Set / update fee</button>
        </div>
        <div id="fee-list"></div>`;

        const termSel = panel.querySelector('#f-term');
        termSel.value = state.termId;

        const load = async () => {
            const fees = await API.admin.fees.listFees(termSel.value);
            panel.querySelector('#fee-list').innerHTML = UI.table({
                columns: [
                    { label: 'Scope', render: r => r.general ? UI.badge('ALL CLASSES', 'blue') : UI.esc(r.className) },
                    { label: 'Amount', render: r => UI.money(r.amount) },
                ],
                rows: fees,
                empty: 'No fee set for this term yet',
            });
        };
        termSel.addEventListener('change', load);
        panel.querySelector('#f-set').addEventListener('click', () => {
            UI.formModal({
                title: 'Set fee amount',
                submitText: 'Save fee',
                fields: [
                    { label: 'Term', name: 'termId', type: 'select', required: true,
                        options: terms.map(t => ({ value: t.id, label: `${t.number.replace('_', ' ')} — ${t.year}` })) },
                    { label: 'Applies to', name: 'classId', type: 'select',
                        options: [
                            { value: '', label: 'All classes (general fee)' },
                            ...classes.map(c => ({ value: c.id, label: `${c.name} (${c.year})` })),
                        ] },
                    { label: 'Amount per term', name: 'amount', type: 'number', required: true, min: 0, step: '0.01' },
                ],
                onSubmit: async (data, close) => {
                    UI.requireFields(data, ['termId', 'amount']);
                    await API.admin.fees.setFee({
                        termId: Number(data.termId),
                        classId: data.classId ? Number(data.classId) : null,
                        allClasses: !data.classId,
                        amount: Number(data.amount),
                    });
                    UI.toast('Fee saved');
                    close(); load();
                },
            });
        });
        await load();
    }

    // ---------------- Statement ----------------
    async function statementDialog(studentId) {
        const stmt = await API.admin.fees.statement(studentId);
        UI.modal({
            title: `Fee statement — ${stmt.student.fullName} (${stmt.student.admissionNumber})`,
            large: true,
            body: stmt.lines.map(line => `
                <h4 style="margin:10px 0 6px">${UI.esc(line.termNumber.replace('_', ' '))} ${line.year} — ${UI.statusBadge[line.status] ? UI.statusBadge[line.status]() : line.status}</h4>
                <p style="margin:4px 0">Expected ${UI.money(line.expected)} · Paid ${UI.money(line.paid)} · Balance <b>${UI.money(line.balance)}</b></p>
                ${line.payments.length ? UI.table({
                    columns: [
                        { label: 'Receipt', key: 'receiptNumber' },
                        { label: 'Date', render: p => UI.fmtDate(p.paymentDate) },
                        { label: 'Method', key: 'method' },
                        { label: 'Amount', render: p => UI.money(p.amount) },
                    ],
                    rows: line.payments,
                }) : '<small style="color:var(--text-muted)">No payments this term</small>'}
            `).join('') || '<div class="empty-state">No fee records</div>',
            footer: `<button class="btn btn-outline" onclick="document.querySelector('.modal-overlay').remove()">Close</button>`,
        });
    }
})();
