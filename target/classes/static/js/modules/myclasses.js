/* ==========================================================================
   My Classes (teacher) — assigned classes, subjects and active students.
   ========================================================================== */
(function () {
    Router.register('/my-classes', {
        title: 'My Classes',
        roles: ['TEACHER'],
        render,
    });

    async function render(el) {
        const classes = await API.teacher.myClasses();
        if (!classes.length) {
            el.innerHTML = `<div class="empty-state"><div class="icon">🏫</div>
                You have no class assignments yet. Ask the admin to assign you to a class or subject.</div>`;
            return;
        }

        el.innerHTML = `<div class="grid-2" id="mc-list"></div>`;
        const box = el.querySelector('#mc-list');

        for (const c of classes) {
            const card = document.createElement('div');
            card.className = 'card';
            card.style.marginBottom = '0';
            card.innerHTML = `
                <div style="display:flex;justify-content:space-between;align-items:flex-start">
                    <h3 style="margin:0">${UI.esc(c.name)}</h3>
                    ${c.isClassTeacher ? UI.badge('CLASS TEACHER', 'blue') : UI.badge('SUBJECT TEACHER', 'gray')}
                </div>
                <p style="color:var(--text-muted);margin:6px 0 0">Level: ${UI.esc(c.level)} · Year ${c.year}</p>
                <div class="details-slot" style="margin-top:10px">
                    <button class="btn btn-outline btn-sm" data-load="${c.id}">Load details</button>
                </div>`;
            box.appendChild(card);

            card.querySelector('[data-load]').addEventListener('click', async (e) => {
                const slot = card.querySelector('.details-slot');
                if (slot.dataset.loaded) { slot.innerHTML = `<button class="btn btn-outline btn-sm" data-load="${c.id}">Load details</button>`; slot.dataset.loaded = ''; return; }
                const detail = await API.teacher.classDetail(c.id);
                slot.dataset.loaded = '1';
                slot.innerHTML = `
                    <h4 style="margin:8px 0 4px">My subjects</h4>
                    ${UI.table({
                        columns: [{ label: 'Subject', key: 'subjectName' }, { label: 'Teacher', key: 'teacherName' }],
                        rows: detail.subjects,
                        empty: 'No subjects assigned to you here',
                    })}
                    <h4 style="margin:10px 0 4px">Active students (${(detail.students || []).length})</h4>
                    ${UI.table({
                        columns: [
                            { label: 'Admission #', key: 'admissionNumber' },
                            { label: 'Name', key: 'fullName' },
                        ],
                        rows: detail.students,
                        empty: 'No active students',
                    })}`;
            });
        }
    }
})();
