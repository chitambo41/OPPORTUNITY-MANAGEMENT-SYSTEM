/* ==========================================================================
   Exams & Results (admin) — create exams, open/close, review submissions,
   approve/return, print report cards (single & all).
   ========================================================================== */
(function () {
    Router.register('/exams', {
        title: 'Exams & Results',
        roles: ['ADMIN'],
        render,
    });

    const state = { termId: '', classId: '' };

    async function render(el) {
        const [terms, classes] = await Promise.all([
            API.admin.years.list().then(ys => ys.flatMap(y => (y.terms || []).map(t => ({ ...t, year: y.year })))),
            API.admin.classes.list(),
        ]);
        const currentTerms = terms;

        el.innerHTML = `
        <div class="card">
            <div class="filters">
                <div class="form-group"><label>Term</label>
                    <select id="e-term"><option value="">All terms</option>
                        ${currentTerms.map(t => `<option value="${t.id}" ${String(state.termId) === String(t.id) ? 'selected' : ''}>${t.number.replace('_', ' ')} — ${t.year}</option>`).join('')}
                    </select></div>
                <div class="form-group"><label>Class</label>
                    <select id="e-class"><option value="">All classes</option>
                        ${classes.map(c => `<option value="${c.id}" ${String(state.classId) === String(c.id) ? 'selected' : ''}>${UI.esc(c.name)} (${c.year})</option>`).join('')}
                    </select></div>
                <button class="btn btn-primary" id="e-new">＋ New exam</button>
            </div>
            <div id="exams-table"></div>
        </div>
        <div id="review-panel"></div>`;

        el.querySelector('#e-term').addEventListener('change', e => { state.termId = e.target.value; load(el); });
        el.querySelector('#e-class').addEventListener('change', e => { state.classId = e.target.value; load(el); });
        el.querySelector('#e-new').addEventListener('click', () => createExamDialog(el));
        await load(el);
    }

    async function load(el) {
        const box = el.querySelector('#exams-table');
        const exams = await API.admin.exams.list({
            termId: state.termId || undefined,
            classId: state.classId || undefined,
        });
        box.innerHTML = UI.table({
            columns: [
                { label: 'Exam', key: 'name' },
                { label: 'Class', key: 'className' },
                { label: 'Term', render: r => UI.esc((r.termNumber || '').replace('_', ' ')) },
                { label: 'Year', key: 'year' },
                { label: 'Max', key: 'maxMarks' },
                { label: 'Status', render: r => (UI.statusBadge[r.status] ? UI.statusBadge[r.status]() : r.status) },
                { label: '', tdClass: 'actions', render: r => {
                    const btns = [];
                    if (r.status === 'DRAFT') btns.push(`<button class="btn btn-outline btn-sm" data-open="${r.id}">Open</button>`);
                    if (r.status === 'SUBMITTED') btns.push(`<button class="btn btn-primary btn-sm" data-review="${r.id}">Review results</button>`);
                    if (r.status === 'APPROVED') btns.push(`<button class="btn btn-outline btn-sm" data-cards="${r.id}">Report cards</button>`);
                    if (r.status === 'RETURNED') btns.push(`<button class="btn btn-outline btn-sm" data-review="${r.id}">View results</button>`);
                    if (r.status === 'OPEN') btns.push(`<button class="btn btn-outline btn-sm" data-review="${r.id}">View progress</button>`);
                    return btns.join(' ');
                } },
            ],
            rows: exams,
            empty: 'No exams found. Create one!',
        });

        box.querySelectorAll('[data-open]').forEach(b => b.addEventListener('click', async () => {
            const ok = await UI.confirmDialog({
                title: 'Open exam',
                message: 'Open this exam so class teachers can enter marks?',
                danger: false, confirmText: 'Open exam',
            });
            if (!ok) return;
            try {
                await API.admin.exams.status(b.dataset.open, { status: 'OPEN' });
                UI.toast('Exam is now OPEN for mark entry');
                load(el);
            } catch (err) { UI.toast(err.message, 'error'); }
        }));

        box.querySelectorAll('[data-review]').forEach(b => b.addEventListener('click', () => reviewExam(Number(b.dataset.review), el)));
        box.querySelectorAll('[data-cards]').forEach(b => b.addEventListener('click', () => printAll(Number(b.dataset.cards))));
    }

    async function createExamDialog(el) {
        const [classes, ctx] = await Promise.all([
            API.admin.classes.list(),
            API.admin.years.context().catch(() => null),
        ]);
        const terms = await API.admin.years.list().then(ys => ys.flatMap(y => (y.terms || []).map(t => ({ ...t, year: y.year }))));
        UI.formModal({
            title: 'New exam',
            submitText: 'Create exam',
            large: true,
            fields: [
                { label: 'Exam name', name: 'name', required: true, placeholder: 'e.g. End of Term 1' },
                { label: 'Class', name: 'classId', type: 'select', required: true, placeholder: 'Select class…',
                    options: classes.map(c => ({ value: c.id, label: `${c.name} (${c.year})` })) },
                { label: 'Term', name: 'termId', type: 'select', required: true, placeholder: 'Select term…',
                    options: terms.map(t => ({ value: t.id, label: `${t.number.replace('_', ' ')} — ${t.year}` })) },
                { label: 'Max marks per subject', name: 'maxMarks', type: 'number', required: true, min: 1, max: 1000, value: 100 },
                { label: 'Notes', name: 'notes' },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['name', 'classId', 'termId', 'maxMarks']);
                await API.admin.exams.create({
                    ...data,
                    classId: Number(data.classId),
                    termId: Number(data.termId),
                    maxMarks: Number(data.maxMarks),
                });
                UI.toast('Exam created as DRAFT. Open it to allow mark entry.');
                close(); load(el);
            },
        });
    }

    // ---------------- Review: full students x subjects table ----------------
    async function reviewExam(examId, el) {
        const panel = el.querySelector('#review-panel');
        panel.innerHTML = `<div class="card"><div class="loading"><div class="spinner"></div>Loading results…</div></div>`;
        panel.scrollIntoView({ behavior: 'smooth' });

        const exams = await API.admin.exams.list({});
        const exam = exams.find(x => x.id === examId) || await API.admin.exams.list().then(list => list.find(x => x.id === examId));
        const table = await API.admin.exams.results(examId);

        // Compute totals client-side for display
        const totals = {};
        table.studentIds.forEach(sid => { totals[sid] = 0; });
        table.cells.forEach(c => { if (c.score !== undefined && c.score !== null) totals[c.studentId] += c.score; });

        panel.innerHTML = `
        <div class="card">
            <div style="display:flex;justify-content:space-between;flex-wrap:wrap;gap:8px;align-items:center">
                <h3 style="margin:0">📋 Results review — ${UI.esc(exam ? exam.name : 'Exam #' + examId)}
                    ${exam && exam.submissionStatus ? UI.badge(exam.submissionStatus, exam.submissionStatus === 'PENDING' ? 'orange' : exam.submissionStatus === 'APPROVED' ? 'green' : 'red') : ''}</h3>
                <div>
                    ${exam && exam.submissionStatus === 'PENDING' ? `
                        <button class="btn btn-success" id="approve-btn">✔ Approve</button>
                        <button class="btn btn-danger" id="return-btn">↩ Return to teacher</button>` : ''}
                </div>
            </div>
            ${exam && exam.adminComment ? `<p><b>Admin comment:</b> ${UI.esc(exam.adminComment)}</p>` : ''}
            ${exam && exam.teacherComment ? `<p><b>Teacher comment:</b> ${UI.esc(exam.teacherComment)}</p>` : ''}
            <div class="table-wrap"><table class="data">
                <thead><tr>
                    <th>#</th><th>Student</th>
                    ${table.subjectNames.map(n => `<th>${UI.esc(n)} /${table.maxMarks[0]}</th>`).join('')}
                    <th>Total</th><th>Average</th>
                </tr></thead>
                <tbody>
                    ${table.studentIds.map((sid, i) => {
                        const cells = table.cells.filter(c => c.studentId === sid);
                        const total = totals[sid];
                        const count = cells.filter(c => c.score !== undefined && c.score !== null).length;
                        const avg = count ? (total / count).toFixed(1) : '—';
                        return `<tr>
                            <td>${i + 1}</td>
                            <td>${UI.esc(table.studentNames[i])} <small style="color:var(--text-muted)">${UI.esc(table.admissionNumbers[i])}</small></td>
                            ${cells.map(c => `<td>${c.score !== undefined && c.score !== null ? c.score : '—'}</td>`).join('')}
                            <td><b>${total}</b></td><td>${avg}</td>
                        </tr>`;
                    }).join('')}
                </tbody>
            </table></div>
        </div>`;

        const approveBtn = panel.querySelector('#approve-btn');
        const returnBtn = panel.querySelector('#return-btn');
        if (approveBtn) approveBtn.addEventListener('click', async () => {
            const { value } = await promptModal('Approve results', 'Optional comment for the record:');
            await API.admin.exams.approve(examId, value || null);
            UI.toast('Results approved — report cards can now be printed');
            Router.handle();
        });
        if (returnBtn) returnBtn.addEventListener('click', async () => {
            const { value, cancelled } = await promptModal('Return to teacher', 'A comment is REQUIRED so the teacher knows what to fix:', true);
            if (cancelled || !value) { if (!cancelled) UI.toast('A comment is required to return results', 'warn'); return; }
            await API.admin.exams.return(examId, value);
            UI.toast('Results returned to the class teacher');
            Router.handle();
        });
    }

    function promptModal(title, message, required) {
        return new Promise(resolve => {
            const m = UI.modal({
                title,
                body: `<p>${UI.esc(message)}</p><div class="form-group">
                    <textarea id="prompt-comment" rows="3"></textarea></div>`,
                footer: `<button class="btn btn-outline" data-a="cancel">Cancel</button>
                         <button class="btn btn-primary" data-a="ok">Confirm</button>`,
            });
            m.overlay.querySelector('[data-a="cancel"]').addEventListener('click', () => { m.close(); resolve({ cancelled: true }); });
            m.overlay.querySelector('[data-a="ok"]').addEventListener('click', () => {
                const value = m.overlay.querySelector('#prompt-comment').value.trim();
                if (required && !value) { UI.toast('A comment is required', 'warn'); return; }
                m.close(); resolve({ value });
            });
        });
    }

    // ---------------- Report cards (single / all) ----------------
    async function printAll(examId) {
        const data = await API.admin.exams.reportCards(examId);
        renderPrintSheets(data);
    }

    function renderPrintSheets(data) {
        const root = document.getElementById('print-root');
        root.innerHTML = data.reportCards.map(card => sheetHtml(data.exam, card)).join('');
        setTimeout(() => { window.print(); }, 60);
    }

    function sheetHtml(exam, card) {
        return `
        <div class="report-card-sheet">
            <div class="rc-header">
                <h1>OPPORTUNITY NURSERY SCHOOL</h1>
                <div class="rc-sub">Report Card — ${UI.esc(exam.term.replace('_', ' '))}, ${exam.year}</div>
            </div>
            <div class="rc-meta">
                <div><b>Student:</b> ${UI.esc(card.studentName)}</div>
                <div><b>Admission #:</b> ${UI.esc(card.admissionNumber)}</div>
                <div><b>Class:</b> ${UI.esc(exam.className)}</div>
                <div><b>Exam:</b> ${UI.esc(exam.name)}</div>
                <div><b>Position:</b> ${card.position}</div>
            </div>
            <table class="rc-table">
                <thead><tr><th>Subject</th><th>Score</th><th>Max</th><th>Grade</th></tr></thead>
                <tbody>
                    ${card.subjects.map(s => `<tr>
                        <td>${UI.esc(s.subject)}</td><td>${s.score}</td><td>${s.maxMarks}</td><td>${s.grade}</td>
                    </tr>`).join('')}
                    <tr><td><b>Total</b></td><td colspan="3"><b>${card.total} / ${card.maxTotal}</b></td></tr>
                </tbody>
            </table>
            <div class="rc-summary">
                <div>Average: ${card.average}%</div>
                <div>Overall grade: ${card.grade}</div>
                <div>Position: ${card.position}</div>
            </div>
            <div class="rc-summary" style="font-weight:400">
                <div>Attendance: ${card.attendancePercentage}% (Present ${card.attendancePresent} · Absent ${card.attendanceAbsent} · Late ${card.attendanceLate})</div>
            </div>
            <div class="rc-remarks"><b>Remarks:</b> ${UI.esc(card.remarks || '—')}</div>
            <div class="rc-footer">
                <div class="line">Class Teacher</div>
                <div class="line">Head Teacher</div>
                <div class="line">Date</div>
            </div>
        </div>`;
    }
})();
