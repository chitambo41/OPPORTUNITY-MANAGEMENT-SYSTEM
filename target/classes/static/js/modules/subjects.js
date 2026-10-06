/* ==========================================================================
   Subjects (admin) — simple CRUD.
   ========================================================================== */
(function () {
    Router.register('/subjects', {
        title: 'Subjects',
        roles: ['ADMIN'],
        render,
    });

    async function render(el) {
        el.innerHTML = `
            <div class="card">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
                    <h3 style="margin:0">Subjects</h3>
                    <button class="btn btn-primary" id="add-subject">＋ Add subject</button>
                </div>
                <div id="subjects-table"></div>
            </div>`;
        el.querySelector('#add-subject').addEventListener('click', () => addDialog(el));
        await load(el);
    }

    async function load(el) {
        const box = el.querySelector('#subjects-table');
        const subjects = await API.admin.subjects.list();
        box.innerHTML = UI.table({
            columns: [
                { label: 'Name', key: 'name' },
                { label: 'Description', key: 'description' },
                { label: '', tdClass: 'actions', render: r => `
                    <button class="btn btn-outline btn-sm" data-edit="${r.id}">Edit</button>
                    <button class="btn btn-danger btn-sm" data-del="${r.id}" data-name="${UI.esc(r.name)}">Delete</button>` },
            ],
            rows: subjects,
            empty: 'No subjects yet',
        });

        box.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () => {
            const s = subjects.find(x => String(x.id) === b.dataset.edit);
            UI.formModal({
                title: 'Edit subject',
                submitText: 'Save',
                fields: [
                    { label: 'Name', name: 'name', required: true, value: s.name },
                    { label: 'Description', name: 'description', value: s.description },
                ],
                onSubmit: async (data, close) => {
                    UI.requireFields(data, ['name']);
                    await API.admin.subjects.update(s.id, data);
                    UI.toast('Subject updated');
                    close(); load(el);
                },
            });
        }));

        box.querySelectorAll('[data-del]').forEach(b => b.addEventListener('click', async () => {
            const ok = await UI.confirmDialog({
                title: 'Delete subject',
                message: `Delete <b>${b.dataset.name}</b>? This cannot be undone.`,
                confirmText: 'Delete',
            });
            if (!ok) return;
            try {
                await API.admin.subjects.remove(b.dataset.del);
                UI.toast('Subject deleted');
                load(el);
            } catch (err) {
                UI.toast(err.message, 'error');
            }
        }));
    }

    function addDialog(el) {
        UI.formModal({
            title: 'Add subject',
            submitText: 'Create subject',
            fields: [
                { label: 'Name', name: 'name', required: true, placeholder: 'e.g. Letters' },
                { label: 'Description', name: 'description' },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['name']);
                await API.admin.subjects.create(data);
                UI.toast('Subject created');
                close(); load(el);
            },
        });
    }
})();
