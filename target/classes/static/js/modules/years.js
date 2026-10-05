/* ==========================================================================
   Academic Years & Terms (admin).
   ========================================================================== */
(function () {
    Router.register('/years', {
        title: 'Academic Years & Terms',
        roles: ['ADMIN'],
        render,
    });

    async function render(el) {
        const years = await API.admin.years.list();
        el.innerHTML = `
            <div class="card">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
                    <h3 style="margin:0">Academic years</h3>
                    <button class="btn btn-primary" id="new-year">＋ New year</button>
                </div>
                <div id="years-list"></div>
            </div>`;

        renderList(document.getElementById('years-list'), years);
        el.querySelector('#new-year').addEventListener('click', () => createYearDialog(render.bind(null, el)));
    }

    function renderList(container, years) {
        if (!years.length) {
            container.innerHTML = `<div class="empty-state"><div class="icon">🗓️</div>
                No academic years yet. Create the first one to get started.</div>`;
            return;
        }
        container.innerHTML = years.map(y => `
            <div class="card" style="box-shadow:none;border:1px solid var(--border)">
                <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
                    <h3 style="margin:0">📅 ${y.year} ${y.current ? UI.badge('CURRENT', 'green') : ''}</h3>
                    <div>
                        ${y.current ? '' : `<button class="btn btn-outline btn-sm" data-set-current="${y.id}">Set as current</button>`}
                    </div>
                </div>
                <div class="table-wrap" style="margin-top:10px">
                    <table class="data">
                        <thead><tr><th>Term</th><th>Start</th><th>End</th><th>Current</th><th></th></tr></thead>
                        <tbody>
                            ${(y.terms || []).map(t => `
                                <tr>
                                    <td>${UI.esc(t.number.replace('_', ' '))}</td>
                                    <td>${UI.fmtDate(t.startDate)}</td>
                                    <td>${UI.fmtDate(t.endDate)}</td>
                                    <td>${t.current ? UI.badge('CURRENT', 'green') : ''}</td>
                                    <td class="actions"><button class="btn btn-outline btn-sm" data-edit-term="${t.id}"
                                        data-year="${y.id}">Edit dates</button></td>
                                </tr>`).join('')}
                        </tbody>
                    </table>
                </div>
            </div>`).join('');

        container.querySelectorAll('[data-set-current]').forEach(btn =>
            btn.addEventListener('click', async () => {
                await API.admin.years.setCurrent({ academicYearId: btn.dataset.setCurrent, termNumber: 'TERM_1' });
                UI.toast('Current year updated');
                Router.handle();
            }));

        container.querySelectorAll('[data-edit-term]').forEach(btn =>
            btn.addEventListener('click', () => {
                const year = years.find(y => String(y.id) === btn.dataset.year);
                const term = year.terms.find(t => String(t.id) === btn.dataset.editTerm);
                editTermDialog(term, year, () => Router.handle());
            }));
    }

    function createYearDialog(done) {
        UI.formModal({
            title: 'New academic year',
            submitText: 'Create year',
            large: true,
            fields: [
                { label: 'Year (e.g. 2027)', name: 'year', type: 'number', required: true, min: 2000, max: 2100 },
                { label: 'Term 1 start', name: 'term1Start', type: 'date', required: true },
                { label: 'Term 1 end', name: 'term1End', type: 'date', required: true },
                { label: 'Term 2 start', name: 'term2Start', type: 'date', required: true },
                { label: 'Term 2 end', name: 'term2End', type: 'date', required: true },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['year', 'term1Start', 'term1End', 'term2Start', 'term2End']);
                await API.admin.years.create({
                    year: Number(data.year),
                    term1Start: data.term1Start, term1End: data.term1End,
                    term2Start: data.term2Start, term2End: data.term2End,
                });
                UI.toast('Academic year created with Term 1 & Term 2');
                close();
                done();
            },
        });
    }

    function editTermDialog(term, year, done) {
        UI.formModal({
            title: `Edit ${term.number.replace('_', ' ')} dates — ${year.year}`,
            submitText: 'Save dates',
            fields: [
                { label: 'Start date', name: 'startDate', type: 'date', required: true, value: term.startDate },
                { label: 'End date', name: 'endDate', type: 'date', required: true, value: term.endDate },
            ],
            onSubmit: async (data, close) => {
                UI.requireFields(data, ['startDate', 'endDate']);
                await API.admin.years.updateTerm(term.id, data);
                UI.toast('Term dates updated');
                close();
                done();
            },
        });
    }
})();
