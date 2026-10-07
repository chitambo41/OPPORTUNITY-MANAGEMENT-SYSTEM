(function () {
    const backgroundImages = ['background.jpg', 'first.jpg', 'second.jpg'];
    const backgroundIndexKey = 'oes.login.background.index';

    document.addEventListener('DOMContentLoaded', () => {
        if (Auth.isLoggedIn()) {
            window.location.replace('./#/dashboard');
            return;
        }

        const savedBackgroundIndex = Number.parseInt(localStorage.getItem(backgroundIndexKey), 10);
        const backgroundIndex = Number.isInteger(savedBackgroundIndex) &&
            savedBackgroundIndex >= 0 && savedBackgroundIndex < backgroundImages.length
            ? savedBackgroundIndex
            : 0;
        document.querySelector('.login-view').style.setProperty(
            '--login-background-image',
            `url("../images/${backgroundImages[backgroundIndex]}")`
        );

        const panels = ['login-panel', 'register-panel', 'forgot-panel', 'reset-panel'];
        const status = document.getElementById('login-status');
        const resetToken = new URLSearchParams(window.location.search).get('resetToken');
        const showPanel = (panelId, message = '') => {
            panels.forEach(id => { document.getElementById(id).hidden = id !== panelId; });
            status.textContent = message;
            status.hidden = !message;
        };
        const showError = (id, message) => {
            const error = document.getElementById(id);
            error.textContent = message;
            error.hidden = false;
        };

        showPanel(resetToken ? 'reset-panel' : 'login-panel');
        document.getElementById('show-forgot').addEventListener('click', () => showPanel('forgot-panel'));
        document.getElementById('show-register').addEventListener('click', () => showPanel('register-panel'));
        document.querySelectorAll('[data-show-login]').forEach(button =>
            button.addEventListener('click', () => showPanel('login-panel')));

        const form = document.getElementById('login-form');
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const email = document.getElementById('login-email').value.trim();
            const password = document.getElementById('login-password').value;
            const errorBox = document.getElementById('login-error');
            const button = document.getElementById('login-btn');
            errorBox.hidden = true;

            if (!email || !password) {
                errorBox.textContent = 'Please enter your email and password';
                errorBox.hidden = false;
                return;
            }

            button.disabled = true;
            button.textContent = 'Signing in…';
            try {
                await Auth.login(email, password);
                localStorage.setItem(
                    backgroundIndexKey,
                    String((backgroundIndex + 1) % backgroundImages.length)
                );
                window.location.assign('./#/dashboard');
            } catch (error) {
                errorBox.textContent = error.message || 'Login failed';
                errorBox.hidden = false;
            } finally {
                button.disabled = false;
                button.textContent = 'Sign in';
            }
        });

        document.getElementById('register-form').addEventListener('submit', async event => {
            event.preventDefault();
            const button = document.getElementById('register-btn');
            const password = document.getElementById('register-password').value;
            const confirmPassword = document.getElementById('register-confirm').value;
            document.getElementById('register-error').hidden = true;
            if (password !== confirmPassword) {
                showError('register-error', 'Passwords do not match');
                return;
            }

            button.disabled = true;
            button.textContent = 'Creating account…';
            try {
                const email = document.getElementById('register-email').value.trim();
                await API.post('/api/auth/register', {
                    fullName: document.getElementById('register-name').value.trim(),
                    phone: document.getElementById('register-phone').value.trim(),
                    address: document.getElementById('register-address').value.trim() || null,
                    email,
                    password,
                    confirmPassword,
                }, { noRedirect: true });
                document.getElementById('login-email').value = email;
                showPanel('login-panel', 'Teacher account created. Sign in with your new credentials.');
                event.target.reset();
            } catch (error) {
                showError('register-error', error.message || 'Registration failed');
            } finally {
                button.disabled = false;
                button.textContent = 'Create account';
            }
        });

        document.getElementById('forgot-form').addEventListener('submit', async event => {
            event.preventDefault();
            const button = document.getElementById('forgot-btn');
            document.getElementById('forgot-error').hidden = true;
            button.disabled = true;
            button.textContent = 'Sending…';
            try {
                await API.post('/api/auth/forgot-password', {
                    email: document.getElementById('forgot-email').value.trim(),
                }, { noRedirect: true });
                showPanel('login-panel', 'If an account exists for that email, a reset link has been sent.');
                event.target.reset();
            } catch (error) {
                showError('forgot-error', error.message || 'Unable to request a password reset');
            } finally {
                button.disabled = false;
                button.textContent = 'Send reset link';
            }
        });

        document.getElementById('reset-form').addEventListener('submit', async event => {
            event.preventDefault();
            const button = document.getElementById('reset-btn');
            const password = document.getElementById('reset-password').value;
            const confirmPassword = document.getElementById('reset-confirm').value;
            document.getElementById('reset-error').hidden = true;
            if (password !== confirmPassword) {
                showError('reset-error', 'Passwords do not match');
                return;
            }

            button.disabled = true;
            button.textContent = 'Updating…';
            try {
                await API.post('/api/auth/reset-password', {
                    token: resetToken,
                    password,
                    confirmPassword,
                }, { noRedirect: true });
                history.replaceState(null, '', window.location.pathname);
                showPanel('login-panel', 'Password updated. Sign in with your new password.');
                event.target.reset();
            } catch (error) {
                showError('reset-error', error.message || 'Unable to reset password');
            } finally {
                button.disabled = false;
                button.textContent = 'Update password';
            }
        });
    });
})();