/* ==========================================================================
   App init — wires login form, logout, sidebar toggle; starts the router.
   ========================================================================== */
(function () {
    document.addEventListener('DOMContentLoaded', () => {
        // Logout
        document.getElementById('logout-btn').addEventListener('click', () => Auth.logout());

        // Sidebar toggle (mobile)
        document.getElementById('sidebar-toggle').addEventListener('click', () => {
            document.getElementById('sidebar').classList.toggle('open');
        });
        document.getElementById('sidebar-nav').addEventListener('click', () => {
            document.getElementById('sidebar').classList.remove('open');
        });

        Router.start();
    });
})();
