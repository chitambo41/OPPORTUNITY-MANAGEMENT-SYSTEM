(function () {
    document.addEventListener('DOMContentLoaded', () => {
        if (Auth.isLoggedIn()) {
            window.location.replace('./#/dashboard');
            return;
        }

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
                window.location.assign('./#/dashboard');
            } catch (error) {
                errorBox.textContent = error.message || 'Login failed';
                errorBox.hidden = false;
            } finally {
                button.disabled = false;
                button.textContent = 'Sign in';
            }
        });
    });
})();