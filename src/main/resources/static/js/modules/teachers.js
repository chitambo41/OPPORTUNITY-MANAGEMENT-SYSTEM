/* ==========================================================================
   Teachers (admin) — active & removed tabs, search, add/edit, remove with reason.
   ========================================================================== */
(function () {
    Router.register('/teachers', {
        title: 'Teachers',
        roles: ['ADMIN'],
        render,
    });

    let state = { tab: 'active', search: '', page: 0 };

    async function render(el) {
        el.innerHTML = `
            <div class="card">
                <div class="tabs">
                    <button class="tab ${state.tab === 'active' ? 'active' : ''}" data-tab="active">Active teachers</button>
                    <button class="tab ${state.tab === 'removed' ? 'active' : ''}" data-tab="removed">Removed teachers</button>
                </div>
                <div class="filters">
                    <div class="form-group">
                        <label>Search by name</label>
                        <input type="text" id="teacher-search" value="${UI.esc(state.search)}" placeholder="Type a name…">
                    </div>
                    <button class="btn btn-primary" id="add-teacher">＋ Add teacher</button>
                </div>
                <div id="teachers-table"></div>
                <div class="pagination" id="teachers-pager"></div>
            </div>`;

        el.querySelectorAll('.tab').forEach(t => t.addEventListener('click', () => {
            state.tab = t.dataset.tab;
            state.page = 0;
            render(el);
        }));
        const searchInput = el.querySelector('#teacher-search');
        searchInput.addEventListener('input', UI.debounce(() => {
            state.search = searchInput.value;
            state.page = 0;
            load(el);
        }, 350));
        el.querySelector('#add-teacher').addEventListener('click', () => addDialog(el));

        await load(el);
    }

    async function load(el) {
        const box = el.querySelector('#teachers-table');
        box.innerHTML = `<div class="loading"><div class="spinner"></div>Loading…</div>`;
        const page = await API.admin.teachers.list({
            removed: state.tab === 'removed',
            search: state.search || undefined,
            page: state.page, size: 10,
        });
        box.innerHTML = UI.table({
            columns: [
                { label: 'Staff #', key: 'staffNumber' },
                { label: 'Name', key: 'fullName' },
                { label: 'Email', key: 'email' },
                { label: 'Phone', key: 'phone' },
                { label: 'Joined', render: r => UI.fmtDate(r.joinDate) },
                { label: 'Status', render: r => r.active ? UI.badge('ACTIVE', 'green') : UI.badge('REMOVED', 'red') },
                { label: '', tdClass: 'actions', render: r => r.active
                    ? `<button class="btn btn-outline btn-sm" data-edit="${r.id}">Edit</button>
                       <button class="btn btn-danger btn-sm" data-remove="${r.id}" data-name="${UI.esc(r.fullName)}">Remove</button>`
                    : UI.badge('LOGIN DISABLED', 'gray') },
            ],
            rows: page.content,
            empty: state.tab === 'removed' ? 'No removed teachers' : 'No teachers yet. Add your first teacher!',
        });

        box.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () => {
            const t = page.content.find(x => String(x.id) === b.dataset.edit);
            editDialog(t, el);
        }));
        box.querySelectorAll('[data-remove]').forEach(b => b.addEventListener('click', () => {
            removeDialog(Number(b.dataset.remove), b.dataset.name, el);
        }));

        // pager
        const pager = el.querySelector('#teachers-pager');
        const totalPages = page.totalPages || 1;
        pager.innerHTML = `
            <span class="info">Page ${state.page + 1} of ${totalPages} — ${page.totalElements} teachers</span>
            <button class="btn btn-outline btn-sm" id="pg-prev" ${state.page === 0 ? 'disabled' : ''}>‹ Prev</button>
            <button class="btn btn-outline btn-sm" id="pg-next" ${state.page + 1 >= totalPages ? 'disabled' : ''}>Next ›</button>`;
        pager.querySelector('#pg-prev').addEventListener('click', () => { state.page--; load(el); });
        pager.querySelector('#pg-next').addEventListener('click', () => { state.page++; load(el); });
    }

    function addDialog(el) {
        UI.formModal({
            title: 'Add teacher',
            submitText: 'Create teacher',
            large: true,
            fields: [
                { label: 'Full name', name: 'fullName', required: true },
                { label: 'Email (login)', name: 'email', type: 'email', required: true, placeholder: 'teacher@example.com' },
                { label: 'Phone', name: 'phone', required: true },
                { label: 'Address', name: 'address' },
                { label: 'Password', name: 'password', type: 'password', placeholder: 'Default: Teacher@2026' },
                { label: 'Join date', name: 'joinDate', type: 'date' },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['fullName', 'email', 'phone']);
                const res = await API.admin.teachers.create(data);
                UI.toast(`Teacher created. Login: ${res.email} / ${data.password || 'Teacher@2026'}`, 'success', 6000);
                close();
                load(el);
            },
        });
    }

    function editDialog(t, el) {
        UI.formModal({
            title: 'Edit teacher',
            submitText: 'Save changes',
            large: true,
            fields: [
                { label: 'Full name', name: 'fullName', required: true, value: t.fullName },
                { label: 'Phone', name: 'phone', value: t.phone },
                { label: 'Address', name: 'address', value: t.address },
                { label: 'Join date', name: 'joinDate', type: 'date', value: t.joinDate },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['fullName']);
                await API.admin.teachers.update(t.id, data);
                UI.toast('Teacher updated');
                close();
                load(el);
            },
        });
    }

    function removeDialog(id, name, el) {
        UI.formModal({
            title: `Remove ${name}`,
            submitText: 'Remove teacher',
            fields: [
                {
                    label: 'Reason (mandatory)', name: 'reason', type: 'select', required: true,
                    placeholder: 'Select a reason…',
                    options: [
                        { value: 'FIRED', label: 'Fired' },
                        { value: 'SHIFT', label: 'Shifted / transferred' },
                        { value: 'DIED', label: 'Deceased' },
                        { value: 'COMPLETE', label: 'Other (contract complete)' },
                    ],
                },
                { label: 'Note', name: 'note', type: 'textarea', required: true, placeholder: 'Mandatory note' },
                { label: 'Removal date', name: 'removalDate', type: 'date', required: true, value: UI.today() },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['reason', 'note', 'removalDate']);
                await API.admin.teachers.remove(id, data);
                UI.toast('Teacher removed; login disabled');
                close();
                state.tab = 'removed';
                load(el);
            },
        });
    }
})();
