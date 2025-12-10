document.addEventListener('DOMContentLoaded', function () {
    // TABOVI
    const tabs = document.querySelectorAll('.tabs .tab');
    const tabContents = document.querySelectorAll('.tab-content');

    function showTab(tab) {
        // Ukloni active sa svih tabova i sadržaja
        tabs.forEach(t => t.classList.remove('active'));
        tabContents.forEach(c => c.classList.remove('active'));

        // Aktiviraj kliknuti tab i njegov content
        tab.classList.add('active');
        const targetId = tab.getAttribute('data-tab');
        const content = document.getElementById(targetId);
        if (content) content.classList.add('active');
    }

    // Prikaži prvi tab ako nijedan nije aktivan
    if (!document.querySelector('.tab.active') && tabs.length > 0) {
        showTab(tabs[0]);
    }

    // Klik na tab
    tabs.forEach(tab => {
        tab.addEventListener('click', () => showTab(tab));
    });

    // EDIT PROFIL TOGGLE
    const toggleEditBtn = document.getElementById('toggleEditBtn');
    const profileEdit = document.getElementById('profileEdit');
    const profileView = document.getElementById('profileView');

    if (toggleEditBtn) {
        toggleEditBtn.addEventListener('click', () => {
            const isHidden = profileEdit.classList.contains('hidden');
            if (isHidden) {
                profileEdit.classList.remove('hidden');
                profileView.classList.add('hidden');
                toggleEditBtn.textContent = 'Zatvori edit';
            } else {
                profileEdit.classList.add('hidden');
                profileView.classList.remove('hidden');
                toggleEditBtn.textContent = 'Edit profil';
            }
        });
    }

    // PASSWORD MODAL
    const modal = document.getElementById('passwordModal');
    const openBtn = document.getElementById('openModalBtn');
    const closeBtn = document.getElementById('closeModal');

    if (openBtn) openBtn.onclick = () => modal.style.display = 'block';
    if (closeBtn) closeBtn.onclick = () => modal.style.display = 'none';

    window.onclick = function(event) {
        if (event.target === modal) modal.style.display = 'none';
    };
});