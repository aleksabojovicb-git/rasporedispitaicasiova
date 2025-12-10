document.addEventListener('DOMContentLoaded', function () {

    /** =========================
     * TAB SWITCHING
     * ========================= */
    const tabs = document.getElementById("tabs");
    const stacks = document.querySelector(".stacks");
    const tabButtons = document.querySelectorAll(".tab-button");

    if (tabs && stacks && tabButtons.length > 0) {
        tabButtons.forEach(btn => {
            btn.addEventListener("click", () => {
                const target = btn.getAttribute("data-target");

                tabs.setAttribute("data-active", target);
                stacks.setAttribute("data-active", target);
            });
        });
    }

    /** =========================
     * FORM REFERENCES
     * ========================= */
    const signinForm = document.getElementById('signin');
    const signupForm = document.getElementById('signup');

    /** =========================
     * FORM ERROR HELPERS
     * ========================= */
    function clearErrors(form) {
        const errors = form.querySelectorAll('.error, .server-error');
        errors.forEach(err => {
            err.textContent = '';
            err.style.display = 'none';
        });
    }

    function showError(form, message) {
        let box = form.querySelector('.server-error');
        if (!box) {
            box = document.createElement('div');
            box.className = 'server-error error';
            form.prepend(box);
        }
        box.textContent = message;
        box.style.display = 'block';
    }

    /** =========================
     * SIGN-IN VALIDATION
     * ========================= */
    if (signinForm) {
        signinForm.addEventListener('submit', function (e) {
            clearErrors(signinForm);

            const email = signinForm.querySelector('input[name="email"]').value.trim();
            const password = signinForm.querySelector('input[name="password"]').value.trim();

            if (!email || !password) {
                e.preventDefault();
                showError(signinForm, 'Email i lozinka su obavezni.');
            }
        });
    }

    /** =========================
     * SIGN-UP VALIDATION
     * ========================= */
    if (signupForm) {
        signupForm.addEventListener('submit', function (e) {
            clearErrors(signupForm);

            const email = signupForm.querySelector('input[name="email"]').value.trim();
            const pw = signupForm.querySelector('input[name="password"]').value.trim();
            const confirm = signupForm.querySelector('input[name="confirm"]').value.trim();

            if (!email || !pw || !confirm) {
                e.preventDefault();
                showError(signupForm, 'Sva polja su obavezna.');
                return;
            }

            if (pw.length < 8) {
                e.preventDefault();
                showError(signupForm, 'Lozinka mora imati najmanje 8 karaktera.');
                return;
            }

            if (pw !== confirm) {
                e.preventDefault();
                showError(signupForm, 'Lozinke se ne poklapaju.');
                return;
            }
        });
    }

});
