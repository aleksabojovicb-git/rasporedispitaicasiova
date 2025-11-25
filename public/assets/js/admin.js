/**
 * Admin panel JavaScript functions
 */

// Toggle form visibility
function toggleForm(formId) {
    console.log('Toggling form:', formId);
    const form = document.getElementById(formId);
    if (form) {
        if (form.style.display === 'block') {
            form.style.display = 'none';
        } else {
            form.style.display = 'block';
        }
    } else {
        console.error('Form not found:', formId);
    }
}

// Confirm deletion of an entity (professor, course, room)
function confirmDelete(formId, entityName) {
    console.log('Confirming deletion of', entityName, 'with form ID:', formId);
    if (confirm(`Da li ste sigurni da želite deaktivirati ovaj ${entityName}?`)) {
        const form = document.getElementById(formId);
        if (form) {
            console.log('Submitting form:', formId);

            // Debug form properties
            console.log('Form action:', form.action);
            console.log('Form method:', form.method);
            console.log('Form elements:', form.elements.length);

            // Force the form to submit directly to the current page
            if (!form.action) {
                form.action = window.location.href;
            }

            form.submit();
        } else {
            console.error('Form not found:', formId);
            alert('Greška: Forma nije pronađena!');
        }
    }
    return false;
}

// Confirm deletion of an event
function confirmDeleteEvent(formId) {
    console.log('Confirming event deletion with form ID:', formId);
    if (confirm("Da li ste sigurni da želite obrisati ovaj događaj?")) {
        const form = document.getElementById(formId);
        if (form) {
            console.log('Submitting event delete form:', formId);

            // Debug form properties
            console.log('Form action:', form.action);
            console.log('Form method:', form.method);
            console.log('Form elements:', form.elements.length);

            // Force the form to submit directly to the current page
            if (!form.action) {
                form.action = window.location.href;
            }

            form.submit();
        } else {
            console.error('Form not found:', formId);
            alert('Greška: Forma nije pronađena!');
        }
    }
    return false;
}

// Manual form submission helper
function submitDeleteForm(id, action, entityType) {
    console.log('Manual submission for', entityType, 'ID:', id);

    // Create a form dynamically
    const form = document.createElement('form');
    form.method = 'post';
    form.action = window.location.href; // Submit to current URL

    // Add action field
    const actionField = document.createElement('input');
    actionField.type = 'hidden';
    actionField.name = 'action';
    actionField.value = action;
    form.appendChild(actionField);

    // Add ID field
    const idField = document.createElement('input');
    idField.type = 'hidden';
    idField.name = 'id';
    idField.value = id;
    form.appendChild(idField);

    // Add form to document, submit it, and remove it
    document.body.appendChild(form);
    form.submit();
    document.body.removeChild(form);
}

// Handle online event toggle
document.addEventListener('DOMContentLoaded', function() {
    console.log('Admin JS loaded');

    // Check forms on page load
    const deleteForms = document.querySelectorAll('form[id^="delete-"]');
    console.log('Found', deleteForms.length, 'delete forms');

    // Add direct submit handler for all delete forms
    deleteForms.forEach(form => {
        console.log('Found delete form:', form.id);

        // Debug each form
        const actionInput = form.querySelector('input[name="action"]');
        const idInput = form.querySelector('input[name="id"]');

        if (actionInput && idInput) {
            console.log('Form action value:', actionInput.value, 'ID:', idInput.value);
        }

        // Ensure the form has the proper action
        if (!form.action) {
            form.action = window.location.href;
        }
    });

    // Check if is_online checkbox exists on the page
    const isOnlineCheckbox = document.getElementById('is_online');
    if (isOnlineCheckbox) {
        console.log('Found online event checkbox');
        isOnlineCheckbox.addEventListener('change', function() {
            const roomSelection = document.getElementById('room_selection');
            if (roomSelection) {
                roomSelection.style.display = this.checked ? 'none' : 'block';
            }
        });
    }


    let editModal = document.getElementById('admin-edit-modal');
    if (!editModal) {
        editModal = document.createElement('div');
        editModal.id = 'admin-edit-modal';
        editModal.style.position = 'fixed';
        editModal.style.left = '0';
        editModal.style.top = '0';
        editModal.style.width = '100%';
        editModal.style.height = '100%';
        editModal.style.display = 'none';
        editModal.style.background = 'rgba(0,0,0,0.5)';
        editModal.style.alignItems = 'center';
        editModal.style.justifyContent = 'center';

        editModal.innerHTML = `
            <div id="admin-edit-modal-inner" style="background:#fff;padding:18px;border-radius:8px;max-width:720px;width:min(720px,90%);margin:auto;color:#000;box-shadow:0 10px 30px rgba(0,0,0,0.6);max-height:84vh;overflow:auto;">
                <style>
                    #admin-edit-modal-inner { font-family: Arial, Helvetica, sans-serif; font-size:14px; }
                    #admin-edit-modal-inner label { display:block; margin:8px 0 6px; color:#071422; font-weight:700; font-size:13px; }
                    #admin-edit-modal-inner input, #admin-edit-modal-inner select, #admin-edit-modal-inner textarea {
                        background: #ffffff !important;
                        color: #071422 !important;
                        border: 1px solid #c8d0d8 !important;
                        padding: 8px 10px !important;
                        border-radius: 6px !important;
                        box-sizing: border-box !important;
                        box-shadow: none !important;
                    }
                    #admin-edit-modal-inner textarea { min-height:100px !important; }
                    #admin-edit-modal-inner .modal-actions { margin-top:14px; text-align:right; }
                    #admin-edit-modal-inner .action-button { margin-left:8px; }
                        #admin-edit-modal-inner, #admin-edit-modal-inner * {
                            color: #071422 !important;
                        }

                        #admin-edit-modal-inner input[type="checkbox"] {
                            width: 18px; height: 18px; vertical-align: middle; appearance: auto; accent-color: #0b2130 !important; background: #fff !important;
                        }

                        #admin-edit-modal-inner input, #admin-edit-modal-inner select, #admin-edit-modal-inner textarea {
                            background: #ffffff !important;
                            color: #071422 !important;
                            border: 1px solid #c8d0d8 !important;
                            padding: 8px 10px !important;
                            border-radius: 6px !important;
                            box-sizing: border-box !important;
                            box-shadow: none !important;
                        }
                    #admin-edit-modal-inner input[type="text"], #admin-edit-modal-inner input[type="email"], #admin-edit-modal-inner input[type="number"], #admin-edit-modal-inner input[type="datetime-local"], #admin-edit-modal-inner select, #admin-edit-modal-inner textarea { width:100% !important; }
                    @media (max-width:420px) { #admin-edit-modal-inner { padding:12px; } }
                </style>
                <h3 id="admin-edit-title" style="margin:0 0 8px;">Uredi</h3>
                <div id="admin-edit-body"></div>
                <div class="modal-actions">
                    <button id="admin-edit-cancel" type="button" class="action-button">Otkaži</button>
                    <button id="admin-edit-save" type="button" class="action-button">Sačuvaj</button>
                </div>
            </div>
        `;
        document.body.appendChild(editModal);

        document.getElementById('admin-edit-cancel').addEventListener('click', () => {
            editModal.style.display = 'none';
        });
        // close when clicking outside inner modal
        editModal.addEventListener('click', (ev) => {
            if (ev.target === editModal) editModal.style.display = 'none';
        });
        // ensure modal overlay is on top
        editModal.style.zIndex = '9999';
    }

    // Helper to build and submit a POST form
    function submitUpdateForm(payload) {
        const form = document.createElement('form');
        form.method = 'post';
        form.action = window.location.href;
        for (const key in payload) {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = key;
            input.value = payload[key];
            form.appendChild(input);
        }
        document.body.appendChild(form);
        form.submit();
    }

    // Auto-hide success/error flash messages after a delay
    setTimeout(() => {
        const flashes = document.querySelectorAll('.success, .error');
        flashes.forEach(el => {
            // add a helper class so CSS transitions apply
            el.classList.add('flash');
            // trigger hide
            setTimeout(() => el.classList.add('hide'), 50);
            // remove from DOM after transition
            setTimeout(() => { if (el && el.parentNode) el.parentNode.removeChild(el); }, 500);
        });
    }, 4000);

    // Click handler for edit buttons
    document.querySelectorAll('.edit-button').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const b = e.currentTarget;
            const entity = b.getAttribute('data-entity');
            const id = b.getAttribute('data-id');
            const body = document.getElementById('admin-edit-body');
            const title = document.getElementById('admin-edit-title');
            body.innerHTML = '';

            if (!entity || !id) {
                alert('Nedostaju podaci za uređivanje.');
                return;
            }

            if (entity === 'profesor') {
                title.textContent = 'Uredi profesora';
                const full_name = b.getAttribute('data-full_name') || '';
                const email = b.getAttribute('data-email') || '';
                body.innerHTML = `
                    <label>Ime i prezime:</label>
                    <input type="text" id="edit-full_name" value="${full_name}" style="width:100%;" />
                    <label style="margin-top:8px;display:block;">Email:</label>
                    <input type="email" id="edit-email" value="${email}" style="width:100%;" />
                `;
                editModal.style.display = 'flex';
                document.getElementById('admin-edit-save').onclick = function() {
                    submitUpdateForm({ action: 'update_profesor', profesor_id: id, full_name: document.getElementById('edit-full_name').value, email: document.getElementById('edit-email').value });
                };
            } else if (entity === 'predmet') {
                title.textContent = 'Uredi predmet';
                const name = b.getAttribute('data-name') || '';
                const code = b.getAttribute('data-code') || '';
                const semester = b.getAttribute('data-semester') || '';
                const is_optional = b.getAttribute('data-is_optional') === '1';
                // Build basic fields
                body.innerHTML = `
                    <label>Naziv:</label>
                    <input type="text" id="edit-name" value="${name}" style="width:100%;" />
                    <label style="margin-top:8px;display:block;">Šifra:</label>
                    <input type="text" id="edit-code" value="${code}" style="width:100%;" />
                    <label style="margin-top:8px;display:block;">Semestar:</label>
                    <input type="number" id="edit-semester" value="${semester}" min="1" max="6" style="width:100%;" />
                    <label style="margin-top:8px;display:block;"><input type="checkbox" id="edit-is_optional" ${is_optional ? 'checked' : ''} /> Izborni predmet</label>
                    <div id="edit-professors-container" style="margin-top:10px;"></div>
                `;

                // Populate professor change controls based on data-professors payload
                try {
                    const profsPayloadRaw = b.getAttribute('data-professors') || '[]';
                    const assigned = JSON.parse(profsPayloadRaw);
                    const allProfessors = (window.adminData && window.adminData.professors) ? window.adminData.professors : [];

                    function buildOptions(selectedId) {
                        return allProfessors.map(p => `<option value="${p.id}" ${p.id == selectedId ? 'selected' : ''}>${p.full_name} (${p.email})</option>`).join('');
                    }

                    const container = document.getElementById('edit-professors-container');

                    if (assigned.length === 0) {
                        // No professor assigned -> show nothing (as requested)
                        container.innerHTML = '';
                    } else if (assigned.length === 1) {
                        const a = assigned[0];
                        const label = a.is_assistant ? 'Promijeni asistenta:' : 'Promijeni profesora:';
                        container.innerHTML = `
                            <label style="display:block; margin-top:8px;">${label}</label>
                            <select id="edit-prof-1" style="width:100%;">${buildOptions(a.id)}</select>
                        `;
                    } else {
                        // two assigned: find professor (is_assistant=0) and assistant (is_assistant=1)
                        let prof = assigned.find(x => x.is_assistant == 0) || assigned[0];
                        let asst = assigned.find(x => x.is_assistant == 1) || assigned[1] || assigned[0];
                        container.innerHTML = `
                            <label style="display:block; margin-top:8px;">Promijeni profesora:</label>
                            <select id="edit-prof-1" style="width:100%;">${buildOptions(prof.id)}</select>
                            <label style="display:block; margin-top:8px;">Promijeni asistenta:</label>
                            <select id="edit-prof-2" style="width:100%;">${buildOptions(asst.id)}</select>
                        `;

                        // Prevent selecting same person in both selects (basic client-side guard)
                        setTimeout(() => {
                            const s1 = document.getElementById('edit-prof-1');
                            const s2 = document.getElementById('edit-prof-2');
                            if (s1 && s2) {
                                function syncDisable() {
                                    const v1 = s1.value;
                                    const v2 = s2.value;
                                    // no disabling needed; we'll validate on save
                                }
                                s1.addEventListener('change', syncDisable);
                                s2.addEventListener('change', syncDisable);
                            }
                        }, 0);
                    }
                } catch (err) {
                    console.error('Ne mogu parsirati podatke o profesorima za predmet', err);
                }

                editModal.style.display = 'flex';
                document.getElementById('admin-edit-save').onclick = function() {
                    // Collect basic fields
                    const payload = {
                        action: 'update_predmet',
                        course_id: id,
                        name: document.getElementById('edit-name').value,
                        code: document.getElementById('edit-code').value,
                        semester: document.getElementById('edit-semester').value,
                        is_optional: document.getElementById('edit-is_optional').checked ? '1' : '0'
                    };

                    // Collect professor assignments if present in modal
                    const profContainer = document.getElementById('edit-professors-container');
                    if (profContainer) {
                        const sel1 = document.getElementById('edit-prof-1');
                        const sel2 = document.getElementById('edit-prof-2');
                        const assignments = [];
                        if (sel1 && !sel2) {
                            // single assignment - keep original role if possible; determine role by label
                            const label = profContainer.querySelector('label');
                            const isAssistant = (label && label.textContent && label.textContent.toLowerCase().includes('asistent')) ? 1 : 0;
                            if (!sel1.value) { alert('Izaberite profesora'); return; }
                            assignments.push({ professor_id: sel1.value, is_assistant: isAssistant });
                        } else if (sel1 && sel2) {
                            if (!sel1.value || !sel2.value) { alert('Oba polja za profesora i asistenta moraju biti popunjena'); return; }
                            if (sel1.value === sel2.value) { alert('Profesor i asistent ne mogu biti ista osoba'); return; }
                            assignments.push({ professor_id: sel1.value, is_assistant: 0 });
                            assignments.push({ professor_id: sel2.value, is_assistant: 1 });
                        }

                        if (assignments.length > 0) {
                            payload.prof_assignments = JSON.stringify(assignments);
                        }
                    }

                    submitUpdateForm(payload);
                };
            } else if (entity === 'sala') {
                title.textContent = 'Uredi salu';
                const code = b.getAttribute('data-code') || '';
                const capacity = b.getAttribute('data-capacity') || '';
                const is_computer_lab = b.getAttribute('data-is_computer_lab') === '1';
                body.innerHTML = `
                    <label>Oznaka:</label>
                    <input type="text" id="edit-code" value="${code}" style="width:100%;" />
                    <label style="margin-top:8px;display:block;">Kapacitet:</label>
                    <input type="number" id="edit-capacity" value="${capacity}" min="1" style="width:100%;" />
                    <label style="margin-top:8px;display:block;"><input type="checkbox" id="edit-is_computer_lab" ${is_computer_lab ? 'checked' : ''} /> Računarska sala</label>
                `;
                editModal.style.display = 'flex';
                document.getElementById('admin-edit-save').onclick = function() {
                    submitUpdateForm({ action: 'update_sala', sala_id: id, code: document.getElementById('edit-code').value, capacity: document.getElementById('edit-capacity').value, is_computer_lab: document.getElementById('edit-is_computer_lab').checked ? '1' : '0' });
                };
            } else {
                
                if (entity === 'dogadjaj') {
                    title.textContent = 'Uredi događaj';
                    const payloadRaw = b.getAttribute('data-payload') || '{}';
                    let payload = {};
                    try { payload = JSON.parse(payloadRaw); } catch (err) { console.error('Invalid payload', err); }

                    const courses = window.adminData ? window.adminData.courses : [];
                    const professors = window.adminData ? window.adminData.professors : [];
                    const rooms = window.adminData ? window.adminData.rooms : [];

                    const isOnline = payload.is_online == 1 || payload.is_online === '1' || payload.is_online === true;

                    const courseOptions = courses.map(c => `<option value="${c.id}" ${c.id == payload.course_id ? 'selected' : ''}>${c.name} (${c.code})</option>`).join('');
                    const profOptions = professors.map(p => `<option value="${p.id}" ${p.id == payload.professor_id ? 'selected' : ''}>${p.full_name} (${p.email})</option>`).join('');
                    const roomOptions = rooms.map(r => `<option value="${r.id}" ${r.id == payload.room_id ? 'selected' : ''}>${r.code} (kap: ${r.capacity})</option>`).join('');

                    body.innerHTML = `
                        <label>Predmet:</label>
                        <select id="edit-course_id" style="width:100%;">${courseOptions}</select>
                        <label style="margin-top:8px;display:block;">Profesor:</label>
                        <select id="edit-professor_id" style="width:100%;">${profOptions}</select>
                        <label style="margin-top:8px;display:block;">Tip:</label>
                        <select id="edit-type" style="width:100%;">
                            <option value="EXAM" ${payload.type === 'EXAM' ? 'selected' : ''}>Ispit</option>
                            <option value="COLLOQUIUM" ${payload.type === 'COLLOQUIUM' ? 'selected' : ''}>Kolokvijum</option>
                        </select>
                        <label style="margin-top:8px;display:block;">Početak:</label>
                        <input type="datetime-local" id="edit-starts_at" value="${payload.starts_at ? payload.starts_at.replace(' ', 'T') : ''}" style="width:100%;" />
                        <label style="margin-top:8px;display:block;">Kraj:</label>
                        <input type="datetime-local" id="edit-ends_at" value="${payload.ends_at ? payload.ends_at.replace(' ', 'T') : ''}" style="width:100%;" />
                        <label style="margin-top:8px;display:block;"><input type="checkbox" id="edit-is_online" ${isOnline ? 'checked' : ''} /> Online događaj</label>
                        <div id="edit-room-selection" style="display:${isOnline ? 'none' : 'block'}; margin-top:8px;">
                            <label>Sala:</label>
                            <select id="edit-room_id" style="width:100%;">${roomOptions}</select>
                        </div>
                        <label style="margin-top:8px;display:block;">Napomene:</label>
                        <textarea id="edit-notes" rows="3" style="width:100%;">${payload.notes || ''}</textarea>
                        <label style="margin-top:8px;display:block;"><input type="checkbox" id="edit-is_published" ${payload.is_published ? 'checked' : ''} /> Objavljeno</label>
                    `;

                    // toggle room selection on change
                    setTimeout(() => {
                        const onlineChk = document.getElementById('edit-is_online');
                        const roomSel = document.getElementById('edit-room-selection');
                        if (onlineChk && roomSel) {
                            onlineChk.addEventListener('change', () => { roomSel.style.display = onlineChk.checked ? 'none' : 'block'; });
                        }
                    }, 0);

                    editModal.style.display = 'flex';
                    document.getElementById('admin-edit-save').onclick = function() {
                        const d = {
                            action: 'update_dogadjaj',
                            dogadjaj_id: payload.id,
                            event_professor_id: payload.event_professor_id || '',
                            course_id: document.getElementById('edit-course_id').value,
                            professor_id: document.getElementById('edit-professor_id').value,
                            type: document.getElementById('edit-type').value,
                            starts_at: document.getElementById('edit-starts_at').value,
                            ends_at: document.getElementById('edit-ends_at').value,
                            is_online: document.getElementById('edit-is_online').checked ? '1' : '0',
                            room_id: document.getElementById('edit-room_id') ? document.getElementById('edit-room_id').value : '',
                            notes: document.getElementById('edit-notes').value,
                            is_published: document.getElementById('edit-is_published').checked ? '1' : '0'
                        };
                        submitUpdateForm(d);
                    };
                    return;
                }
                alert('Nepodržan entitet za uređivanje: ' + entity);
            }
        });
    });
});