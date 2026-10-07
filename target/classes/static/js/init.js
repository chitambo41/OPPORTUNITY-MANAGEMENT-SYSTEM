/* ==========================================================================
   App init — wires login form, logout, sidebar toggle; starts the router.
   ========================================================================== */
(function () {
    document.addEventListener('DOMContentLoaded', () => {
        // Logout
        document.getElementById('logout-btn').addEventListener('click', () => Auth.logout());
        document.getElementById('account-btn').addEventListener('click', openCredentialsDialog);

        // Sidebar toggle (mobile)
        document.getElementById('sidebar-toggle').addEventListener('click', () => {
            document.getElementById('sidebar').classList.toggle('open');
        });
        document.getElementById('sidebar-nav').addEventListener('click', () => {
            document.getElementById('sidebar').classList.remove('open');
        });

        Router.start();
    });

    function openCredentialsDialog() {
        UI.formModal({
            title: 'Change account credentials',
            submitText: 'Update',
            fields: [
                { name: 'currentPassword', label: 'Current password', type: 'password', required: true },
                { name: 'newEmail', label: 'New email', type: 'email' },
                { name: 'newPassword', label: 'New password', type: 'password' },
                { name: 'confirmPassword', label: 'Confirm new password', type: 'password' },
            ],
            onSubmit: async (data, close) => {
                if (data.newPassword && data.newPassword !== data.confirmPassword) {
                    throw { message: 'Passwords do not match' };
                }
                if (!data.newEmail && !data.newPassword) {
                    throw { message: 'Enter a new email or password' };
                }

                const result = await API.put('/api/account/credentials', {
                    currentPassword: data.currentPassword,
                    newEmail: data.newEmail || null,
                    newPassword: data.newPassword || null,
                    confirmPassword: data.confirmPassword || null,
                });
                API.setToken(result.token);
                API.setUser({
                    userId: result.userId,
                    email: result.email,
                    role: result.role,
                    teacherId: result.teacherId,
                    teacherName: result.teacherName,
                });
                Auth.renderTopbar();
                close();
                UI.toast('Account credentials updated');
            },
        });
    }
})();
