/* ==========================================================================
   Hash router — maps #/route to module renderers, guards by role.
   ========================================================================== */
(function () {
    const ROUTES = {}; // prefix -> { title, roles, render }

    function register(prefix, { title, roles, render }) {
        ROUTES[prefix] = { title, roles, render };
    }

    function currentPath() {
        const h = location.hash || '#/';
        return h.replace(/^#/, '').split('?')[0] || '/';
    }

    function resolve(path) {
        // Longest-prefix match
        const keys = Object.keys(ROUTES).sort((a, b) => b.length - a.length);
        for (const k of keys) {
            if (path === k || path.startsWith(k + '/')) return { prefix: k, route: ROUTES[k] };
        }
        return null;
    }

    async function handle() {
        const path = currentPath();

        // Not logged in -> login view
        if (!Auth.isLoggedIn()) {
            location.replace('login.html');
            return;
        }
        if (path === '/') {
            location.hash = '#/dashboard';
            return;
        }
        if (path === '/login') {
            location.hash = '#/dashboard';
            return;
        }

        const match = resolve(path);
        if (!match) {
            document.getElementById('page-title').textContent = 'Not found';
            document.getElementById('content').innerHTML =
                `<div class="empty-state"><div class="icon">🧭</div>Page not found. <a href="#/dashboard">Go to dashboard</a></div>`;
            return;
        }

        const { prefix, route } = match;
        // Role guard — blocks views the role cannot access
        if (route.roles && !route.roles.includes(Auth.role())) {
            UI.toast('You do not have access to that page', 'warn');
            location.hash = '#/dashboard';
            return;
        }

        Auth.renderSidebar(prefix);
        Auth.renderTopbar();
        document.getElementById('page-title').textContent = route.title;
        const content = document.getElementById('content');
        content.innerHTML = `<div class="loading"><div class="spinner"></div>Loading…</div>`;
        try {
            await route.render(content, path);
        } catch (err) {
            if (err && err.status === 401) return; // already redirected by API layer
            content.innerHTML = `<div class="empty-state"><div class="icon">⚠️</div>
                ${UI.esc(err.message || 'Failed to load this page')}</div>`;
        }
    }

    function start() {
        window.addEventListener('hashchange', handle);
        handle();
    }

    window.Router = { register, start, handle, currentPath };
})();
