/* ==========================================================================
   Marks & Send Results (teacher) — mark entry view + send-to-admin workflow.
   ========================================================================== */
(function () {
    Router.register('/marks', {
        title: 'Enter Marks',
        roles: ['TEACHER'],
        render: renderMarks,
    });

    Router.register('/send-results', {
        title: 'Send Results',
        roles: ['TEACHER'],
        render: renderSend,
    });

    // ---------------- Mark entry ----------------
    async function renderMarks(el) {
        const exams = await API.teacher.myExams();
        const open = exams.filter(e => e.status === 'OPEN' || e.status === 'RETURNED');

        if (!open.length) {
            el.innerHTML = `<div class="empty-state"><div class="icon">📝</div>
                No open exams right now. Exams appear here once the admin opens them for mark entry.</div>`;
            return;
        }

        el.innerHTML = `
        <div class="card">
            <div class="filters">
                <div class="form-group"><label>Exam</label>
                    <select id="m-exam">${open.map(e => `<option value="${e.id}">${UI.esc(e.name)} — ${UI.esc(e.className)} (${(e.termNumber || '').replace('_', ' ')})</option>`).join('')}</select></div>
                <div class="form-group"><label>My subject</label>
                    <select id="m-subject"></select></div>
            </div>
            <div id="m-progress"></div>
            <div id="m-table"></div>
        </div>`;

        const examSel = el.querySelector('#m-exam');
        const subjSel = el.querySelector('#m-subject');

        async function loadSubjects() {
            const examId = examSel.value;
            const detailPromises = [];
            // Find the exam's class detail: fetch progress to know subjects; then use first "my classes" detail
            const progress = await API.teacher.progress(examId);
            const exam = open.find(e => String(e.id) === String(examId));
            const detail = await API.teacher.classDetail(exam.classId);
            const mySubjects = (detail.subjects || []);
            subjSel.innerHTML = mySubjects.map(s =>
                `<option value="${s.subjectId}">${UI.esc(s.subjectName)}</option>`).join('');
            renderProgress(progress, mySubjects);
            if (mySubjects.length) loadRoster();
        }

        async function renderProgress(progress, mySubjects) {
            const my = progress.filter(p => mySubjects.some(s => String(s.subjectId) === String(p.subjectId)));
            el.querySelector('#m-progress').innerHTML = my.map(p => `
                <div style="margin-bottom:8px">
                    <div style="display:flex;justify-content:space-between;font-size:12.5px">
                        <span>${UI.esc(p.subjectName)}</span>
                        <span>${p.studentsWithMarks}/${p.activeStudents} ${p.complete ? '✔' : ''}</span>
                    </div>
                    <div class="progress-track"><div class="progress-fill ${p.complete ? '' : 'warn'}"
                        style="width:${p.activeStudents ? Math.round(100 * p.studentsWithMarks / p.activeStudents) : 0}%"></div></div>
                </div>`).join('');
        }

        async function loadRoster() {
            const examId = examSel.value;
            const subjectId = subjSel.value;
            if (!subjectId) return;
            const exam = open.find(e => String(e.id) === String(examId));
            const table = await API.teacher.results(examId);
            const colIdx = table.subjectIds.findIndex(id => String(id) === String(subjectId));
            const marksByStudent = {};
            table.cells.forEach(c => {
                if (String(c.subjectId) === String(subjectId)) marksByStudent[c.studentId] = c.score;
            });
            const maxMarks = table.maxMarks[colIdx] !== undefined ? table.maxMarks[colIdx] : exam.maxMarks;

            el.querySelector('#m-table').innerHTML = `
                <div class="table-wrap"><table class="data">
                    <thead><tr><th>Student</th><th>Score (0–${maxMarks})</th></tr></thead>
                    <tbody>
                        ${table.studentIds.map((sid, i) => `
                            <tr data-sid="${sid}">
                                <td>${UI.esc(table.studentNames[i])} <small style="color:var(--text-muted)">${UI.esc(table.admissionNumbers[i])}</small></td>
                                <td><input type="number" class="m-score" min="0" max="${maxMarks}"
                                    value="${marksByStudent[sid] !== undefined ? marksByStudent[sid] : ''}" style="width:100px"></td>
                            </tr>`).join('')}
                    </tbody>
                </table></div>
                <button class="btn btn-primary" id="m-save" style="margin-top:12px">Save marks</button>`;

            el.querySelector('#m-save').addEventListener('click', async () => {
                const marks = [...el.querySelectorAll('#m-table tr[data-sid]')]
                    .map(tr => ({ studentId: Number(tr.dataset.sid), score: tr.querySelector('.m-score').value }))
                    .filter(m => m.score !== '' && m.score !== null);
                if (!marks.length) { UI.toast('Enter at least one score', 'warn'); return; }
                try {
                    await API.teacher.saveMarks({
                        examId: Number(examId),
                        subjectId: Number(subjectId),
                        marks: marks.map(m => ({ studentId: m.studentId, score: Number(m.score) })),
                    });
                    UI.toast('Marks saved');
                    loadSubjects();
                } catch (err) { UI.toast(err.message, 'error'); }
            });
        }

        examSel.addEventListener('change', loadSubjects);
        subjSel.addEventListener('change', loadRoster);
        await loadSubjects();
    }

    // ---------------- Send results to admin ----------------
    async function renderSend(el) {
        const exams = await API.teacher.myExams();
        const led = exams.filter(e => e.isClassTeacher || true); // backend enforces class teacher
        el.innerHTML = `
        <div class="card">
            <h3>📤 Send results to admin</h3>
            <p style="color:var(--text-muted)">
                You can send results for exams of classes where you are the <b>class teacher</b>.
                Sending requires <b>every subject</b> to have marks for <b>every active student</b>.
                Marks are locked until the admin approves or returns them.
            </p>
            <div id="send-table"></div>
        </div>`;

        const rows = [];
        for (const exam of led) {
            let progress = [];
            try { progress = await API.teacher.progress(exam.id); } catch (e) { /* skip */ }
            const missing = progress.reduce((acc, p) => acc + p.missing, 0);
            rows.push({ exam, progress, missing });
        }

        el.querySelector('#send-table').innerHTML = UI.table({
            columns: [
                { label: 'Exam', render: r => UI.esc(r.exam.name) },
                { label: 'Class', key: 'className', render: r => UI.esc(r.exam.className) },
                { label: 'Term', render: r => UI.esc((r.exam.termNumber || '').replace('_', ' ')) },
                { label: 'Status', render: r => (UI.statusBadge[r.exam.status] ? UI.statusBadge[r.exam.status]() : r.exam.status) },
                { label: 'Missing marks', render: r => r.missing === 0
                    ? UI.badge('READY TO SEND', 'green')
                    : UI.badge(`${r.missing} missing`, 'orange') },
                { label: '', tdClass: 'actions', render: r => `
                    ${r.missing === 0 && (r.exam.status === 'OPEN' || r.exam.status === 'RETURNED')
                        ? `<button class="btn btn-primary btn-sm" data-send="${r.exam.id}">Send to admin</button>` : ''}
                    ${r.exam.submissionStatus === 'PENDING' ? UI.badge('AWAITING REVIEW', 'blue') : ''}
                    ${r.exam.submissionStatus === 'RETURNED' && r.exam.adminComment
                        ? `<small style="color:var(--danger)">Admin: ${UI.esc(r.exam.adminComment)}</small>` : ''}` },
            ],
            rows,
            empty: 'No exams assigned to you yet',
        });

        el.querySelectorAll('[data-send]').forEach(b => b.addEventListener('click', () => {
            UI.formModal({
                title: 'Send results to admin',
                submitText: 'Send & lock marks',
                fields: [
                    { label: 'Comment (optional)', name: 'teacherComment', type: 'textarea' },
                ],
                onSubmit: async (data, close) => {
                    try {
                        await API.teacher.sendResults(Number(b.dataset.send), data.teacherComment || null);
                        UI.toast('Results sent. Marks are locked until review.');
                        close(); Router.handle();
                    } catch (err) { UI.toast(err.message, 'error'); }
                },
            });
        }));
    }
})();
