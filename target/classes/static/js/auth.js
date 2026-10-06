/* ==========================================================================
   Auth — login, session, role-based sidebar menus.
   ========================================================================== */
(function () {
    const MENUS = {
        ADMIN: [
            { section: 'Overview' },
            { hash: '#/dashboard', icon: '📊', label: 'Dashboard' },
            { section: 'Academics' },
            { hash: '#/years', icon: '🗓️', label: 'Years & Terms' },
            { hash: '#/classes', icon: '🏫', label: 'Classes' },
            { hash: '#/subjects', icon: '📚', label: 'Subjects' },
            { section: 'People' },
            { hash: '#/teachers', icon: '👩\u200d🏫', label: 'Teachers' },
            { hash: '#/students', icon: '🧒', label: 'Students' },
            { section: 'Operations' },
            { hash: '#/attendance-admin', icon: '✅', label: 'Attendance' },
            { hash: '#/exams', icon: '📝', label: 'Exams & Results' },
            { hash: '#/fees', icon: '💰', label: 'School Fees' },
            { hash: '#/promotion', icon: '⬆️', label: 'Promotion' },
        ],
        TEACHER: [
            { section: 'Overview' },
            { hash: '#/dashboard', icon: '📊', label: 'My Dashboard' },
            { hash: '#/my-classes', icon: '🏫', label: 'My Classes' },
            { section: 'Daily work' },
            { hash: '#/attendance', icon: '✅', label: 'Attendance' },
            { hash: '#/marks', icon: '📝', label: 'Enter Marks' },
            { hash: '#/send-results', icon: '📤', label: 'Send Results' },
        ],
    };

    function user() { return API.getUser(); }

    function role() {
        const u = user();
        return u ? u.role : null;
    }

    function isLoggedIn() { return !!API.getToken(); }

    function isAdmin() { return role() === 'ADMIN'; }
    function isTeacher() { return role() === 'TEACHER'; }

    async function login(email, password) {
        const res = await API.post('/api/auth/login', { email, password }, { noRedirect: true });
        API.setToken(res.token);
        API.setUser({
            userId: res.userId, email: res.email, role: res.role,
            teacherId: res.teacherId, teacherName: res.teacherName,
        });
        return res;
    }

    function logout() {
        API.clearSession();
        location.assign('login.html');
    }

    function renderSidebar(activeHash) {
        const nav = document.getElementById('sidebar-nav');
        const r = role();
        const menu = MENUS[r] || [];
        nav.innerHTML = menu.map(item => {
            if (item.section) {
                return `<div class="nav-section">${UI.esc(item.section)}</div>`;
            }
            const active = activeHash === item.hash ||
                (item.hash === '#/dashboard' && activeHash === '#/');
            return `<a class="nav-link ${active ? 'active' : ''}" href="${item.hash}">
                <span>${item.icon}</span><span>${UI.esc(item.label)}</span></a>`;
        }).join('');
    }

    function renderTopbar() {
        const u = user();
        const chip = document.getElementById('topbar-user');
        const ctx = document.getElementById('topbar-context');
        if (u) {
            const name = u.teacherName || u.email;
            chip.textContent = `${u.role === 'ADMIN' ? '🛡️' : '👩\u200d🏫'} ${name}`;
            ctx.textContent = '';
        }
    }

    Auth = {
        MENUS, user, role, isLoggedIn, isAdmin, isTeacher,
        login, logout, renderSidebar, renderTopbar,
    };
    window.Auth = Auth;
})();
