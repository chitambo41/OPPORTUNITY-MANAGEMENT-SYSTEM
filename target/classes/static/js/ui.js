/* ==========================================================================
   UI helpers — toasts, modals, confirm dialogs, tables, badges, formatting.
   ========================================================================== */
(function () {
    // ---------------- Toasts ----------------
    function toast(message, type = 'success', ms = 3500) {
        const container = document.getElementById('toast-container');
        const el = document.createElement('div');
        el.className = 'toast ' + type;
        el.innerHTML = `<span>${type === 'error' ? '⚠️' : type === 'warn' ? '🟡' : '✅'}</span><span>${esc(message)}</span>`;
        container.appendChild(el);
        setTimeout(() => el.remove(), ms);
    }

    // ---------------- Modals ----------------
    function modal({ title, body, footer, large }) {
        const root = document.getElementById('modal-root');
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.innerHTML = `
            <div class="modal ${large ? 'modal-lg' : ''}" role="dialog" aria-modal="true">
                <div class="modal-header">
                    <h3>${title}</h3>
                    <button class="modal-close" aria-label="Close">&times;</button>
                </div>
                <div class="modal-body">${body}</div>
                ${footer ? `<div class="modal-footer">${footer}</div>` : ''}
            </div>`;
        function close() { overlay.remove(); }
        overlay.querySelector('.modal-close').addEventListener('click', close);
        overlay.addEventListener('click', e => { if (e.target === overlay) close(); });
        root.appendChild(overlay);
        return { overlay, close };
    }

    function profilePhotoMarkup(name) {
        const initials = String(name || '?').trim().split(/\s+/).slice(0, 2).map(part => part[0] || '').join('').toUpperCase();
        return `
            <div class="profile-photo-editor">
                <div class="profile-photo-frame">
                    <img data-profile-picture hidden alt="${esc(name)}">
                    <span data-profile-placeholder>${esc(initials)}</span>
                </div>
                <div class="profile-photo-controls">
                    <label class="btn btn-outline btn-sm profile-photo-select">
                        <span data-profile-picture-label>Add profile picture</span>
                        <input type="file" data-profile-picture-file accept="image/jpeg,image/png">
                    </label>
                    <small>JPEG or PNG, up to 5 MB</small>
                </div>
            </div>`;
    }

    async function bindProfilePhoto(root, type, id) {
        const endpoint = `/api/admin/${type}/${id}/profile-picture`;
        const image = root.querySelector('[data-profile-picture]');
        const placeholder = root.querySelector('[data-profile-placeholder]');
        const label = root.querySelector('[data-profile-picture-label]');
        const input = root.querySelector('[data-profile-picture-file]');
        let imageUrl = null;

        const showPicture = blob => {
            if (imageUrl) URL.revokeObjectURL(imageUrl);
            imageUrl = blob ? URL.createObjectURL(blob) : null;
            image.hidden = !imageUrl;
            placeholder.hidden = !!imageUrl;
            label.textContent = imageUrl ? 'Change profile picture' : 'Add profile picture';
            if (imageUrl) image.src = imageUrl;
        };

        input.addEventListener('change', async () => {
            const file = input.files && input.files[0];
            if (!file) return;
            if (!['image/jpeg', 'image/png'].includes(file.type)) {
                toast('Choose a JPEG or PNG image', 'error');
                input.value = '';
                return;
            }
            if (file.size > 5 * 1024 * 1024) {
                toast('Profile pictures must be 5 MB or smaller', 'error');
                input.value = '';
                return;
            }

            input.disabled = true;
            try {
                const form = new FormData();
                form.append('file', file);
                await API.post(endpoint, form);
                showPicture(await API.getBlob(endpoint));
                toast('Profile picture updated');
            } catch (error) {
                toast(error.message || 'Could not upload profile picture', 'error');
            } finally {
                input.disabled = false;
                input.value = '';
            }
        });

        try {
            showPicture(await API.getBlob(endpoint));
        } catch (error) {
            toast(error.message || 'Could not load profile picture', 'error');
        }
    }

    function confirmDialog({ title = 'Are you sure?', message, confirmText = 'Confirm', danger = true }) {
        return new Promise(resolve => {
            const { overlay, close } = modal({
                title,
                body: `<p style="margin:0">${message}</p>`,
                footer: `
                    <button class="btn btn-outline" data-act="cancel">Cancel</button>
                    <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" data-act="ok">${esc(confirmText)}</button>`,
            });
            overlay.querySelector('[data-act="cancel"]').addEventListener('click', () => { close(); resolve(false); });
            overlay.querySelector('[data-act="ok"]').addEventListener('click', () => { close(); resolve(true); });
        });
    }

    function formModal({ title, fields, submitText = 'Save', large, onSubmit }) {
        const body = formHtml(fields);
        const { overlay, close } = modal({
            title,
            body,
            footer: `
                <button class="btn btn-outline" data-act="cancel">Cancel</button>
                <button class="btn btn-primary" data-act="submit">${esc(submitText)}</button>`,
            large,
        });
        const form = overlay.querySelector('form');
        overlay.querySelector('[data-act="cancel"]').addEventListener('click', close);
        overlay.querySelector('[data-act="submit"]').addEventListener('click', async (e) => {
            const data = readForm(form);
            clearFormErrors(form);
            e.target.disabled = true;
            try {
                await onSubmit(data, close, form);
            } catch (err) {
                showFormError(form, err.message || 'Something went wrong');
            } finally {
                e.target.disabled = false;
            }
        });
        form.addEventListener('submit', e => e.preventDefault());
        return { overlay, close, form };
    }

    // ---------------- Forms ----------------
    function formHtml(fields) {
        return `<form novalidate><div class="form-grid">${fields.map(f => {
            if (f.type === 'select') {
                return `
                <div class="form-group" ${f.full ? 'style="grid-column:1/-1"' : ''}>
                    <label>${esc(f.label)}${f.required ? ' *' : ''}</label>
                    <select name="${f.name}" ${f.required ? 'required' : ''}>
                        ${f.placeholder ? `<option value="">${esc(f.placeholder)}</option>` : ''}
                        ${f.options.map(o => `<option value="${esc(o.value)}" ${String(o.value) === String(f.value) ? 'selected' : ''}>${esc(o.label)}</option>`).join('')}
                    </select>
                    <div class="form-error" hidden></div>
                </div>`;
            }
            if (f.type === 'textarea') {
                return `
                <div class="form-group" ${f.full ? 'style="grid-column:1/-1"' : ''}>
                    <label>${esc(f.label)}${f.required ? ' *' : ''}</label>
                    <textarea name="${f.name}" rows="3" ${f.required ? 'required' : ''} placeholder="${esc(f.placeholder || '')}">${esc(f.value || '')}</textarea>
                    <div class="form-error" hidden></div>
                </div>`;
            }
            if (f.type === 'static') {
                return `<div class="form-group" ${f.full ? 'style="grid-column:1/-1"' : ''}>
                    <label>${esc(f.label)}</label><div style="padding:6px 0">${f.html}</div>
                </div>`;
            }
            return `
            <div class="form-group" ${f.full ? 'style="grid-column:1/-1"' : ''}>
                <label>${esc(f.label)}${f.required ? ' *' : ''}</label>
                <input type="${f.type || 'text'}" name="${f.name}" value="${esc(f.value !== undefined && f.value !== null ? f.value : '')}"
                    ${f.required ? 'required' : ''} ${f.min !== undefined ? `min="${f.min}"` : ''} ${f.max !== undefined ? `max="${f.max}"` : ''}
                    placeholder="${esc(f.placeholder || '')}" ${f.step ? `step="${f.step}"` : ''}>
                <div class="form-error" hidden></div>
            </div>`;
        }).join('')}</div></form>`;
    }

    function readForm(form) {
        const data = {};
        new FormData(form).forEach((v, k) => { data[k] = typeof v === 'string' ? v.trim() : v; });
        return data;
    }

    function requireFields(data, fields) {
        for (const f of fields) {
            if (data[f] === undefined || data[f] === null || data[f] === '') {
                throw { message: 'Please fill in all required fields' };
            }
        }
    }

    function showFormError(form, message) {
        const box = form.querySelector('.form-error:not([hidden])') || form.querySelector('.form-error');
        if (box) { box.textContent = message; box.hidden = false; }
        else UI.toast(message, 'error');
    }

    function clearFormErrors(form) {
        form.querySelectorAll('.form-error').forEach(el => { el.hidden = true; el.textContent = ''; });
    }

    // ---------------- Tables ----------------
    function table({ columns, rows, empty = 'Nothing here yet' }) {
        if (!rows || rows.length === 0) {
            return `<div class="empty-state"><div class="icon">🗂️</div>${esc(empty)}</div>`;
        }
        const head = columns.map(c => `<th>${esc(c.label)}</th>`).join('');
        const body = rows.map(r => `<tr>${columns.map(c =>
            `<td class="${c.tdClass || ''}">${c.render ? c.render(r) : esc(r[c.key] !== undefined && r[c.key] !== null ? r[c.key] : '')}</td>`).join('')}</tr>`).join('');
        return `<div class="table-wrap"><table class="data">
            <thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`;
    }

    // ---------------- Badges & formatting ----------------
    function badge(text, color) {
        return `<span class="badge ${color}">${esc(text)}</span>`;
    }
    const statusBadge = {
        ACTIVE: () => badge('ACTIVE', 'green'),
        COMPLETE: () => badge('COMPLETE', 'blue'),
        REMOVED: () => badge('REMOVED', 'red'),
        PAID: () => badge('PAID', 'green'),
        PARTIAL: () => badge('PARTIAL', 'orange'),
        NOT_PAID: () => badge('NOT PAID', 'red'),
        PENDING: () => badge('PENDING REVIEW', 'orange'),
        APPROVED: () => badge('APPROVED', 'green'),
        RETURNED: () => badge('RETURNED', 'red'),
        DRAFT: () => badge('DRAFT', 'gray'),
        OPEN: () => badge('OPEN', 'blue'),
        SUBMITTED: () => badge('SUBMITTED', 'orange'),
        PRESENT: () => badge('PRESENT', 'green'),
        ABSENT: () => badge('ABSENT', 'red'),
        LATE: () => badge('LATE', 'orange'),
    };

    function money(n) {
        const v = Number(n || 0);
        return v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function fmtDate(d) {
        if (!d) return '—';
        return new Date(d).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
    }

    function today() {
        return new Date().toISOString().slice(0, 10);
    }

    function esc(s) {
        if (s === undefined || s === null) return '';
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function debounce(fn, ms) {
        let t;
        return function (...args) { clearTimeout(t); t = setTimeout(() => fn.apply(this, args), ms); };
    }

    window.UI = {
        toast, modal, profilePhotoMarkup, bindProfilePhoto, confirmDialog, formModal,
        formHtml, readForm, requireFields, showFormError, clearFormErrors,
        table, badge, statusBadge, money, fmtDate, today, esc, debounce,
    };
})();
