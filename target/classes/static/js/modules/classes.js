/* ==========================================================================
   Classes (admin) — list by year, create, assign class teacher, add/remove subjects, details.
   ========================================================================== */
(function () {
    Router.register('/classes', {
        title: 'Classes',
        roles: ['ADMIN'],
        render,
    });

    const state = { year: '' };

    async function render(el) {
        const years = await API.admin.years.list();
        const currentCtx = await API.admin.years.context().catch(() => null);
        if (!state.year && currentCtx && currentCtx.year) state.year = currentCtx.year;

        el.innerHTML = `
            <div class="card">
                <div class="filters">
                    <div class="form-group">
                        <label>Academic year</label>
                        <select id="year-filter">
                            <option value="">All years</option>
                            ${years.map(y => `<option value="${y.year}" ${String(state.year) === String(y.year) ? 'selected' : ''}>${y.year}${y.current ? ' (current)' : ''}</option>`).join('')}
                        </select>
                    </div>
                    <button class="btn btn-primary" id="add-class">＋ New class</button>
                </div>
                <div id="classes-list" class="grid-2"></div>
            </div>`;

        el.querySelector('#year-filter').addEventListener('change', e => {
            state.year = e.target.value;
            load(el);
        });
        el.querySelector('#add-class').addEventListener('click', () => addDialog(el));
        await load(el);
    }

    async function load(el) {
        const box = el.querySelector('#classes-list');
        const classes = await API.admin.classes.list(state.year || undefined);
        if (!classes.length) {
            box.innerHTML = `<div class="empty-state" style="grid-column:1/-1"><div class="icon">🏫</div>
                No classes for this year yet.</div>`;
            return;
        }
        box.innerHTML = classes.map(c => classCard(c)).join('');
        wireCards(box, el);
    }

    function classCard(c) {
        return `
        <div class="card" style="margin-bottom:0" data-card="${c.id}">
            <div style="display:flex;justify-content:space-between;align-items:flex-start">
                <h3 style="margin:0">${UI.esc(c.name)}</h3>
                ${UI.badge(c.level, 'blue')}
            </div>
            <p style="color:var(--text-muted);margin:6px 0 10px">Year ${c.year} · <span data-active-count>${c.activeStudentCount || 0}</span> active student(s)</p>
            <p style="margin:4px 0"><b>Class teacher:</b> ${c.classTeacherName ? UI.esc(c.classTeacherName) : '<i>Not assigned</i>'}</p>
            <p style="margin:4px 0 10px"><b>Subjects:</b> ${(c.subjects || []).length}</p>
            <div style="display:flex;gap:8px;flex-wrap:wrap">
                <button class="btn btn-outline btn-sm" data-details="${c.id}">View details</button>
                <button class="btn btn-outline btn-sm" data-tch="${c.id}">Class teacher</button>
                <button class="btn btn-outline btn-sm" data-subj="${c.id}">Add subject</button>
            </div>
            <div class="details-slot" style="margin-top:12px"></div>
        </div>`;
    }

    function wireCards(box, el) {
        box.querySelectorAll('[data-details]').forEach(button => button.addEventListener('click', () =>
            toggleDetails(button.dataset.details, box, el)));
        box.querySelectorAll('[data-tch]').forEach(button => button.addEventListener('click', () =>
            assignTeacherDialog(Number(button.dataset.tch), el)));
        box.querySelectorAll('[data-subj]').forEach(button => button.addEventListener('click', () =>
            addSubjectDialog(Number(button.dataset.subj), el)));
        wireSubjectRemoval(box, el);
    }

    function wireSubjectRemoval(root, el) {
        root.querySelectorAll('[data-cs-del]').forEach(button => button.addEventListener('click', async () => {
            const ok = await UI.confirmDialog({ title: 'Remove subject', message: 'Remove this subject from the class?' });
            if (!ok) return;
            await API.admin.classes.removeSubject(Number(button.dataset.cls), Number(button.dataset.csDel));
            UI.toast('Subject removed from class');
            load(el);
        }));
    }

    async function toggleDetails(classId, box, el) {
        const slot = box.querySelector(`[data-card="${classId}"] .details-slot`);
        if (slot.innerHTML) { slot.innerHTML = ''; return; }
        await renderDetails(Number(classId), slot, box, el);
    }

    async function renderDetails(classId, slot, box, el) {
        const [c, students] = await Promise.all([
            API.admin.classes.get(classId),
            API.admin.students.search({ classId, status: 'ACTIVE', page: 0, size: 100 }),
        ]);
        slot.innerHTML = `
            <div class="grid-2" style="margin:10px 0">
                <div><b>Class teacher:</b> ${c.classTeacherName ? UI.esc(c.classTeacherName) : 'Not assigned'}</div>
                <div><b>Total active students:</b> ${c.activeStudentCount || 0}</div>
            </div>
            <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap">
                <h4 style="margin:10px 0 6px">Active students</h4>
                <button class="btn btn-primary btn-sm" data-add-student="${classId}">＋ Add student</button>
            </div>
            ${UI.table({
                columns: [
                    { label: 'Admission #', render: row => UI.esc(row.admissionNumber) },
                    { label: 'Name', render: row => UI.esc(row.fullName) },
                    { label: 'Status', render: () => UI.badge('ACTIVE', 'green') },
                ],
                rows: students.content || [],
                empty: 'No active students in this class',
            })}
            <h4 style="margin:10px 0 6px">Subjects & teachers</h4>
            ${UI.table({
                columns: [
                    { label: 'Subject', key: 'subjectName' },
                    { label: 'Teacher', render: row => UI.esc(row.teacherName || '—') },
                    { label: '', tdClass: 'actions', render: row =>
                        `<button class="btn btn-danger btn-sm" data-cs-del="${row.id}" data-cls="${classId}">Remove</button>` },
                ],
                rows: c.subjects || [],
                empty: 'No subjects added yet',
            })}`;

        const classCard = box.querySelector(`[data-card="${classId}"]`);
        classCard.querySelector('[data-active-count]').textContent = c.activeStudentCount || 0;
        slot.querySelector('[data-add-student]').addEventListener('click', () =>
            addStudentDialog(classId, c.name, el, box));
        wireSubjectRemoval(slot, el);
    }

    function addStudentDialog(classId, className, el, box) {
        UI.formModal({
            title: `Add student to ${className}`,
            submitText: 'Enroll student',
            large: true,
            fields: [
                { label: 'Full name', name: 'fullName', required: true },
                { label: 'Date of birth', name: 'dateOfBirth', type: 'date' },
                { label: 'Gender', name: 'gender', type: 'select', options: [
                    { value: 'M', label: 'Male' }, { value: 'F', label: 'Female' },
                ] },
                { label: 'Guardian name', name: 'guardianName' },
                { label: 'Guardian phone', name: 'guardianPhone' },
                { label: 'Guardian email', name: 'guardianEmail', type: 'email' },
                { label: 'Guardian address', name: 'guardianAddress', type: 'textarea', full: true },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['fullName']);
                await API.admin.students.create({ ...data, classId });
                UI.toast(`Student enrolled in ${className}`);
                close();
                await load(el);
                await toggleDetails(classId, box, el);
            },
        });
    }

    async function addDialog(el) {
        const years = await API.admin.years.list();
        UI.formModal({
            title: 'New class',
            submitText: 'Create class',
            fields: [
                {
                    label: 'Level', name: 'level', type: 'select', required: true, placeholder: 'Select level…',
                    options: [
                        { value: 'BABY_CLASS', label: 'Baby Class' },
                        { value: 'KG1', label: 'KG1' },
                        { value: 'KG2', label: 'KG2' },
                        { value: 'KG3', label: 'KG3' },
                    ],
                },
                { label: 'Class name', name: 'name', required: true, placeholder: 'e.g. KG2 - Eagle' },
                {
                    label: 'Academic year', name: 'academicYearId', type: 'select', required: true, placeholder: 'Select year…',
                    options: years.map(year => ({ value: year.id, label: year.year + (year.current ? ' (current)' : '') })),
                },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['level', 'name', 'academicYearId']);
                await API.admin.classes.create({ ...data, academicYearId: Number(data.academicYearId) });
                UI.toast('Class created');
                close();
                Router.handle();
            },
        });
    }

    async function assignTeacherDialog(classId, el) {
        const teachers = await API.admin.teachers.list({ page: 0, size: 100 });
        const c = await API.admin.classes.get(classId);
        UI.formModal({
            title: 'Assign class teacher',
            submitText: 'Assign',
            fields: [
                {
                    label: 'Active teacher (one class per teacher per year)', name: 'teacherId', type: 'select',
                    required: true, placeholder: 'Select teacher…',
                    options: teachers.content.filter(t => t.active).map(t => ({ value: t.id, label: t.fullName })),
                },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['teacherId']);
                await API.admin.classes.assignTeacher(classId, { teacherId: Number(data.teacherId) });
                UI.toast('Class teacher assigned');
                close(); Router.handle();
            },
        });
    }

    async function addSubjectDialog(classId, el) {
        const [subjects, teachers, c] = await Promise.all([
            API.admin.subjects.list(),
            API.admin.teachers.list({ page: 0, size: 100 }),
            API.admin.classes.get(classId),
        ]);
        UI.formModal({
            title: `Add subject to ${c.name}`,
            submitText: 'Add subject',
            fields: [
                {
                    label: 'Subject', name: 'subjectId', type: 'select', required: true, placeholder: 'Select subject…',
                    options: subjects.map(s => ({ value: s.id, label: s.name })),
                },
                {
                    label: 'Teacher for this subject', name: 'teacherId', type: 'select', required: true, placeholder: 'Select teacher…',
                    options: teachers.content.filter(t => t.active).map(t => ({ value: t.id, label: t.fullName })),
                },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['subjectId', 'teacherId']);
                await API.admin.classes.addSubject(classId, {
                    subjectId: Number(data.subjectId), teacherId: Number(data.teacherId),
                });
                UI.toast('Subject added to class');
                close(); Router.handle();
            },
        });
    }
})();
