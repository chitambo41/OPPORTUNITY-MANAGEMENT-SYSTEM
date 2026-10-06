/* ==========================================================================
   Attendance — teacher marking view + admin view-only reports.
   ========================================================================== */
(function () {
    Router.register('/attendance', {
        title: 'Attendance',
        roles: ['TEACHER'],
        render: renderTeacher,
    });

    Router.register('/attendance-admin', {
        title: 'Attendance',
        roles: ['ADMIN'],
        render: renderAdmin,
    });

    // ---------------- Teacher view (class teacher only) ----------------
    async function renderTeacher(el) {
        const classes = await API.teacher.myClasses();
        const led = classes.filter(c => c.isClassTeacher);
        if (!led.length) {
            el.innerHTML = `<div class="empty-state"><div class="icon">🔒</div>
                Only <b>class teachers</b> can mark attendance. You are not currently a class teacher.</div>`;
            return;
        }
        const today = UI.today();

        el.innerHTML = `
        <div class="card">
            <div class="filters">
                <div class="form-group"><label>Class</label>
                    <select id="att-class">${led.map(c => `<option value="${c.id}">${UI.esc(c.name)}</option>`).join('')}</select></div>
                <div class="form-group"><label>Date</label>
                    <input type="date" id="att-date" value="${today}"></div>
                <button class="btn btn-outline" id="att-load">Load</button>
                <button class="btn btn-primary" id="att-save" disabled>Save attendance</button>
            </div>
            <div id="att-summary" class="cards-grid"></div>
            <div id="att-roster"></div>
        </div>`;

        const classSel = el.querySelector('#att-class');
        const dateInput = el.querySelector('#att-date');
        const saveBtn = el.querySelector('#att-save');

        async function loadRoster() {
            const classId = classSel.value;
            const date = dateInput.value || today;
            const roster = await API.teacher.attendanceRoster({ classId, date });
            saveBtn.disabled = date !== today;

            // Daily summary
            let present = 0, absent = 0, late = 0;
            roster.forEach(r => {
                if (r.status === 'PRESENT') present++;
                else if (r.status === 'ABSENT') absent++;
                else if (r.status === 'LATE') late++;
            });
            el.querySelector('#att-summary').innerHTML = `
                <div class="stat-card green"><div class="stat-label">Present</div><div class="stat-value">${present}</div></div>
                <div class="stat-card red"><div class="stat-label">Absent</div><div class="stat-value">${absent}</div></div>
                <div class="stat-card orange"><div class="stat-label">Late</div><div class="stat-value">${late}</div></div>
                <div class="stat-card"><div class="stat-label">Total students</div><div class="stat-value">${roster.length}</div></div>`;

            if (!roster.length) {
                el.querySelector('#att-roster').innerHTML =
                    `<div class="empty-state"><div class="icon">🧒</div>No active students in this class.</div>`;
                return;
            }

            el.querySelector('#att-roster').innerHTML = `
                <div class="table-wrap"><table class="data">
                    <thead><tr><th>Admission #</th><th>Student</th><th>Status</th><th>Note</th></tr></thead>
                    <tbody>
                        ${roster.map(r => `
                            <tr data-student="${r.studentId}">
                                <td>${UI.esc(r.admissionNumber)}</td>
                                <td>${UI.esc(r.studentName)}</td>
                                <td>
                                    <select class="att-status" ${date !== today ? 'disabled' : ''}>
                                        <option value="">— not marked —</option>
                                        <option value="PRESENT" ${r.status === 'PRESENT' ? 'selected' : ''}>Present</option>
                                        <option value="ABSENT" ${r.status === 'ABSENT' ? 'selected' : ''}>Absent</option>
                                        <option value="LATE" ${r.status === 'LATE' ? 'selected' : ''}>Late</option>
                                    </select>
                                </td>
                                <td><input class="att-note" value="${UI.esc(r.note || '')}" ${date !== today ? 'disabled' : ''}></td>
                            </tr>`).join('')}
                    </tbody>
                </table></div>`;
        }

        el.querySelector('#att-load').addEventListener('click', loadRoster);
        classSel.addEventListener('change', loadRoster);
        dateInput.addEventListener('change', loadRoster);

        saveBtn.addEventListener('click', async () => {
            const entries = [...el.querySelectorAll('#att-roster tr[data-student]')]
                .map(tr => ({
                    studentId: Number(tr.dataset.student),
                    status: tr.querySelector('.att-status').value,
                    note: tr.querySelector('.att-note').value.trim() || null,
                }))
                .filter(e => e.status);
            if (!entries.length) {
                UI.toast('Mark at least one student', 'warn');
                return;
            }
            try {
                await API.teacher.saveAttendance({
                    classId: Number(classSel.value),
                    date: dateInput.value || today,
                    entries,
                });
                UI.toast('Attendance saved');
                loadRoster();
            } catch (err) {
                UI.toast(err.message, 'error');
            }
        });

        // "Mark all present" button
        const markAll = document.createElement('button');
        markAll.className = 'btn btn-success';
        markAll.textContent = '✅ Mark all present';
        markAll.addEventListener('click', () => {
            el.querySelectorAll('.att-status').forEach(sel => { if (!sel.disabled) sel.value = 'PRESENT'; });
        });
        el.querySelector('.filters').appendChild(markAll);

        await loadRoster();
    }

    // ---------------- Admin view (read-only) ----------------
    async function renderAdmin(el) {
        const [classes, ctx] = await Promise.all([
            API.admin.classes.list(),
            API.admin.years.context().catch(() => null),
        ]);
        const today = UI.today();

        el.innerHTML = `
        <div class="card">
            <div class="filters">
                <div class="form-group"><label>Class</label>
                    <select id="v-class">${classes.map(c => `<option value="${c.id}">${UI.esc(c.name)} (${c.year})</option>`).join('')}</select></div>
                <div class="form-group"><label>Date</label><input type="date" id="v-date" value="${today}"></div>
                <button class="btn btn-outline" id="v-load">Load day</button>
                <div class="form-group"><label>From</label><input type="date" id="v-start" value="${today}"></div>
                <div class="form-group"><label>To</label><input type="date" id="v-end" value="${today}"></div>
                <button class="btn btn-outline" id="v-report">Per-student report</button>
            </div>
            <div id="v-summary" class="cards-grid"></div>
            <div id="v-table"></div>
        </div>`;

        const classSel = el.querySelector('#v-class');

        async function loadDay() {
            const classId = classSel.value;
            const date = el.querySelector('#v-date').value || today;
            const [rows, summary] = await Promise.all([
                API.admin.attendance.classDay({ classId, date }),
                API.admin.attendance.summary({ classId, date }),
            ]);
            el.querySelector('#v-summary').innerHTML = `
                <div class="stat-card green"><div class="stat-label">Present</div><div class="stat-value">${summary.present}</div></div>
                <div class="stat-card red"><div class="stat-label">Absent</div><div class="stat-value">${summary.absent}</div></div>
                <div class="stat-card orange"><div class="stat-label">Late</div><div class="stat-value">${summary.late}</div></div>
                <div class="stat-card"><div class="stat-label">Unmarked</div><div class="stat-value">${summary.unmarked}</div></div>`;
            el.querySelector('#v-table').innerHTML = UI.table({
                columns: [
                    { label: 'Admission #', key: 'admissionNumber' },
                    { label: 'Student', key: 'studentName' },
                    { label: 'Status', render: r => r.status
                        ? (UI.statusBadge[r.status] ? UI.statusBadge[r.status]() : r.status)
                        : UI.badge('NOT MARKED', 'gray') },
                    { label: 'Note', key: 'note' },
                ],
                rows,
                empty: 'No active students in this class',
            });
        }

        async function loadReport() {
            const classId = classSel.value;
            const start = el.querySelector('#v-start').value || today;
            const end = el.querySelector('#v-end').value || today;
            const rows = await API.admin.attendance.report({ classId, start, end });
            el.querySelector('#v-table').innerHTML = UI.table({
                columns: [
                    { label: 'Admission #', key: 'admissionNumber' },
                    { label: 'Student', key: 'studentName' },
                    { label: 'Present', key: 'present' },
                    { label: 'Absent', key: 'absent' },
                    { label: 'Late', key: 'late' },
                    { label: 'Marked days', key: 'totalMarked' },
                    { label: 'Attendance %', render: r => `<b>${r.percentage}%</b>` },
                ],
                rows,
                empty: 'No data for this range',
            });
        }

        classSel.addEventListener('change', loadDay);
        el.querySelector('#v-load').addEventListener('click', loadDay);
        el.querySelector('#v-report').addEventListener('click', loadReport);
        if (classes.length) await loadDay();
    }
})();
