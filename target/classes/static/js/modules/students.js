/* ==========================================================================
   Students (admin) — search, filters, pagination, add/edit, move, remove, profile.
   ========================================================================== */
(function () {
    Router.register('/students', {
        title: 'Students',
        roles: ['ADMIN'],
        render,
    });

    const state = { name: '', classId: '', year: '', status: 'ACTIVE', page: 0 };

    async function render(el) {
        const [classes, years] = await Promise.all([
            API.admin.classes.list(),
            API.admin.years.list(),
        ]);

        el.innerHTML = `
        <div class="card">
            <div class="filters">
                <div class="form-group"><label>Name</label>
                    <input id="f-name" value="${UI.esc(state.name)}" placeholder="Search name…"></div>
                <div class="form-group"><label>Class</label>
                    <select id="f-class"><option value="">All classes</option>
                        ${classes.map(c => `<option value="${c.id}" ${String(state.classId) === String(c.id) ? 'selected' : ''}>${UI.esc(c.name)} (${c.year})</option>`).join('')}
                    </select></div>
                <div class="form-group"><label>Year</label>
                    <select id="f-year"><option value="">All years</option>
                        ${years.map(y => `<option value="${y.year}" ${String(state.year) === String(y.year) ? 'selected' : ''}>${y.year}</option>`).join('')}
                    </select></div>
                <div class="form-group"><label>Status</label>
                    <select id="f-status">
                        ${['ACTIVE', 'COMPLETE', 'REMOVED'].map(s => `<option value="${s}" ${state.status === s ? 'selected' : ''}>${s}</option>`).join('')}
                        <option value="" ${!state.status ? 'selected' : ''}>All</option>
                    </select></div>
                <button class="btn btn-primary" id="add-student">＋ Admit student</button>
            </div>
            <div id="students-table"></div>
            <div class="pagination" id="students-pager"></div>
        </div>`;

        const bind = (id, key) => {
            el.querySelector(id).addEventListener('change', e => {
                state[key] = e.target.value; state.page = 0; load(el);
            });
        };
        bind('#f-class', 'classId'); bind('#f-year', 'year'); bind('#f-status', 'status');
        const nameInput = el.querySelector('#f-name');
        nameInput.addEventListener('input', UI.debounce(() => {
            state.name = nameInput.value; state.page = 0; load(el);
        }, 350));
        el.querySelector('#add-student').addEventListener('click', () => addDialog(el));

        await load(el);
    }

    async function load(el) {
        const box = el.querySelector('#students-table');
        box.innerHTML = `<div class="loading"><div class="spinner"></div>Loading…</div>`;
        const page = await API.admin.students.search({
            name: state.name || undefined,
            classId: state.classId || undefined,
            year: state.year || undefined,
            status: state.status || undefined,
            page: state.page, size: 10,
        });

        box.innerHTML = UI.table({
            columns: [
                { label: 'Admission #', key: 'admissionNumber' },
                { label: 'Name', key: 'fullName' },
                { label: 'Class', render: r => UI.esc(r.currentClassName || '—') },
                { label: 'Guardian', key: 'guardianName' },
                { label: 'Phone', key: 'guardianPhone' },
                { label: 'Status', render: r => (UI.statusBadge[r.status] ? UI.statusBadge[r.status]() : UI.esc(r.status)) },
                { label: '', tdClass: 'actions', render: r => `
                    <button class="btn btn-outline btn-sm" data-profile="${r.id}">Profile</button>
                    <button class="btn btn-outline btn-sm" data-edit="${r.id}">Edit</button>
                    <button class="btn btn-outline btn-sm" data-move="${r.id}">Move</button>
                    ${r.status === 'ACTIVE' ? `<button class="btn btn-danger btn-sm" data-remove="${r.id}" data-name="${UI.esc(r.fullName)}">Remove</button>` : ''}` },
            ],
            rows: page.content,
            empty: 'No students match these filters',
        });

        box.querySelectorAll('[data-profile]').forEach(b => b.addEventListener('click', () => profileDialog(Number(b.dataset.profile))));
        box.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () => {
            const s = page.content.find(x => String(x.id) === b.dataset.edit);
            editDialog(s, el);
        }));
        box.querySelectorAll('[data-move]').forEach(b => b.addEventListener('click', () => {
            const s = page.content.find(x => String(x.id) === b.dataset.move);
            moveDialog(s, el);
        }));
        box.querySelectorAll('[data-remove]').forEach(b => b.addEventListener('click', () => {
            removeDialog(Number(b.dataset.remove), b.dataset.name, el);
        }));

        const pager = el.querySelector('#students-pager');
        const totalPages = page.totalPages || 1;
        pager.innerHTML = `
            <span class="info">Page ${state.page + 1} of ${totalPages} — ${page.totalElements} students</span>
            <button class="btn btn-outline btn-sm" id="pg-prev" ${state.page === 0 ? 'disabled' : ''}>‹ Prev</button>
            <button class="btn btn-outline btn-sm" id="pg-next" ${state.page + 1 >= totalPages ? 'disabled' : ''}>Next ›</button>`;
        pager.querySelector('#pg-prev').addEventListener('click', () => { state.page--; load(el); });
        pager.querySelector('#pg-next').addEventListener('click', () => { state.page++; load(el); });
    }

    function studentFields(s = {}) {
        return [
            { label: 'Full name', name: 'fullName', required: true, value: s.fullName },
            { label: 'Date of birth', name: 'dateOfBirth', type: 'date', value: s.dateOfBirth },
            { label: 'Gender', name: 'gender', type: 'select', value: s.gender, options: [
                { value: 'M', label: 'Male' }, { value: 'F', label: 'Female' }] },
            { label: 'Guardian name', name: 'guardianName', value: s.guardianName },
            { label: 'Guardian phone', name: 'guardianPhone', value: s.guardianPhone },
            { label: 'Guardian email', name: 'guardianEmail', value: s.guardianEmail },
            { label: 'Guardian address', name: 'guardianAddress', value: s.guardianAddress, full: true },
            { label: 'Blood group', name: 'bloodGroup', value: s.bloodGroup },
            { label: 'Notes', name: 'notes', value: s.notes },
        ];
    }

    function addDialog(el) {
        API.admin.classes.list().then(classes => {
            const currentYearClasses = classes;
            UI.formModal({
                title: 'Admit new student',
                submitText: 'Admit student',
                large: true,
                fields: [
                    ...studentFields(),
                    {
                        label: 'Initial class', name: 'classId', type: 'select', required: true, placeholder: 'Select class…',
                        options: currentYearClasses.map(c => ({ value: c.id, label: `${c.name} (${c.year})` })),
                    },
                ],
                onSubmit: async (data, close) => {
                    UI.requireFields(data, ['fullName', 'classId']);
                    const res = await API.admin.students.create({
                        ...data,
                        classId: Number(data.classId),
                        admissionNumber: undefined, // auto-generated by backend
                    });
                    UI.toast(`Student admitted. Admission number: ${res.admissionNumber}`, 'success', 6000);
                    close(); load(el);
                },
            });
        });
    }

    function editDialog(s, el) {
        UI.formModal({
            title: `Edit ${s.fullName}`,
            submitText: 'Save changes',
            large: true,
            fields: studentFields(s),
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['fullName']);
                await API.admin.students.update(s.id, data);
                UI.toast('Student updated');
                close(); load(el);
            },
        });
    }

    async function moveDialog(s, el) {
        const classes = await API.admin.classes.list();
        UI.formModal({
            title: `Move ${s.fullName} to another class`,
            submitText: 'Move student',
            fields: [{
                label: 'Target class', name: 'targetClassId', type: 'select', required: true, placeholder: 'Select class…',
                options: classes.map(c => ({ value: c.id, label: `${c.name} (${c.year})` })),
            }],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['targetClassId']);
                await API.admin.students.move(s.id, { targetClassId: Number(data.targetClassId) });
                UI.toast('Student moved');
                close(); load(el);
            },
        });
    }

    function removeDialog(id, name, el) {
        UI.formModal({
            title: `Remove ${name}`,
            submitText: 'Remove student',
            fields: [
                {
                    label: 'Reason (mandatory)', name: 'reason', type: 'select', required: true, placeholder: 'Select a reason…',
                    options: [
                        { value: 'SHIFT', label: 'Shifted to another school' },
                        { value: 'DIED', label: 'Deceased' },
                        { value: 'COMPLETE', label: 'Completed school (after KG3)' },
                    ],
                },
                { label: 'Note', name: 'note', type: 'textarea', required: true, placeholder: 'Mandatory note' },
                { label: 'Removal date', name: 'removalDate', type: 'date', required: true, value: UI.today() },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['reason', 'note', 'removalDate']);
                await API.admin.students.remove(id, data);
                UI.toast('Student removed');
                close(); load(el);
            },
        });
    }

    async function profileDialog(id) {
        const p = await API.admin.students.get(id);
        const s = p.student;
        const { overlay } = UI.modal({
            title: `${s.fullName} — ${s.admissionNumber}`,
            large: true,
            body: `
                <div class="cards-grid">
                    <div class="stat-card"><div class="stat-label">Attendance</div>
                        <div class="stat-value">${p.attendance.percentage}%</div>
                        <small>Present ${p.attendance.present} · Absent ${p.attendance.absent} · Late ${p.attendance.late}</small></div>
                    <div class="stat-card green"><div class="stat-label">Status</div>
                        <div class="stat-value" style="font-size:16px">${UI.statusBadge[s.status] ? UI.statusBadge[s.status]() : s.status}</div>
                        <small>${UI.esc(s.currentClassName || 'No current class')}</small></div>
                </div>
                <h4>Class history</h4>
                ${UI.table({
                    columns: [
                        { label: 'Year', key: 'year' },
                        { label: 'Class', key: 'className' },
                        { label: 'Level', key: 'level' },
                        { label: 'Status', render: r => (UI.statusBadge[r.status] ? UI.statusBadge[r.status]() : r.status) },
                        { label: 'Joined', render: r => UI.fmtDate(r.joinedOn) },
                    ],
                    rows: p.classHistory,
                    empty: 'No enrollments',
                })}
                <h4 style="margin-top:14px">Fees status</h4>
                ${UI.table({
                    columns: [
                        { label: 'Year', key: 'year' },
                        { label: 'Term', render: r => UI.esc((r.termNumber || '').replace('_', ' ')) },
                        { label: 'Expected', render: r => UI.money(r.expected) },
                        { label: 'Paid', render: r => UI.money(r.paid) },
                        { label: 'Balance', render: r => UI.money(r.balance) },
                        { label: 'Status', render: r => (UI.statusBadge[r.status] ? UI.statusBadge[r.status]() : r.status) },
                        { label: '', tdClass: 'actions', render: r => r.status === 'PAID'
                            ? `<button class="btn btn-outline btn-sm" data-print-fee="${r.termId}">Print receipt</button>`
                            : '' },
                    ],
                    rows: p.fees,
                    empty: 'No fee records',
                })}`,
        });
        overlay.querySelectorAll('[data-print-fee]').forEach(button => button.addEventListener('click', () =>
            window.FeeReceipts.printForTerm(s.id, Number(button.dataset.printFee))));
    }
})();
