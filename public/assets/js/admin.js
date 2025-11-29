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

    // Create edit modal container once
    if (!document.getElementById('admin-edit-modal')) {
        const modal = document.createElement('div');
        modal.id = 'admin-edit-modal';
        modal.style.display = 'none';
        modal.innerHTML = '<div class="aem-overlay" style="position:fixed;inset:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:9999;">' +
            '<div class="aem-dialog" style="background:#0b1220;padding:20px;border-radius:8px;max-width:600px;width:90%;color:#fff;">' +
            '<button id="aem-close" style="float:right;background:transparent;border:none;color:#fff;font-size:18px;cursor:pointer;">&times;</button>' +
            '<div id="aem-content"></div>' +
            '</div></div>';
        document.body.appendChild(modal);

        // Close handler
        modal.querySelector('#aem-close').addEventListener('click', () => {
            modal.style.display = 'none';
        });
        // close when clicking overlay outside dialog
        modal.querySelector('.aem-overlay').addEventListener('click', (ev) => {
            if (ev.target.classList.contains('aem-overlay')) modal.style.display = 'none';
        });
    }

    // Edit button click delegation
    document.body.addEventListener('click', function(e) {
        // Only handle elements that have data-entity attribute
        const btn = e.target.closest('.edit-button[data-entity]');
        if (!btn) return; // ignore other buttons that share the class but aren't editor triggers
        e.preventDefault();

        const entity = btn.dataset.entity;
        // get raw dataset attributes
        const attrs = {};
        for (let i = 0; i < btn.attributes.length; i++) {
            const at = btn.attributes[i];
            if (at.name.startsWith('data-')) {
                const key = at.name.slice(5); // remove data-
                attrs[key] = at.value;
            }
        }

        openEditModal(entity, attrs);
    });
});

/**
 * Build and open edit modal for supported entities
 * Supported: profesor, predmet, sala
 */
function openEditModal(entity, data) {
    const modal = document.getElementById('admin-edit-modal');
    const content = modal.querySelector('#aem-content');
    // map entity to server action and id field
    const map = {
        'profesor': { action: 'update_profesor', idField: 'profesor_id', title: 'Uredi profesora' },
        'predmet': { action: 'update_predmet', idField: 'course_id', title: 'Uredi predmet' },
        'sala': { action: 'update_sala', idField: 'sala_id', title: 'Uredi salu' },
        'dogadjaj': { action: 'update_dogadjaj', idField: 'dogadjaj_id', title: 'Uredi događaj' }
    };

    // If button provided a JSON payload in data-payload, parse it into data object
    if (data.payload) {
        try {
            const parsed = JSON.parse(data.payload);
            // merge parsed fields into data (do not overwrite existing simple attrs)
            for (const k in parsed) {
                if (!(k in data)) data[k] = parsed[k];
                else data[k] = parsed[k]; // prefer parsed value
            }
        } catch (e) {
            console.warn('Failed to parse data-payload JSON', e);
        }
    }

    // allow editing user accounts
    map['account'] = { action: 'update_account', idField: 'account_id', title: 'Uredi nalog' };

    if (!map[entity]) {
        alert('Editing for "' + entity + '" is not implemented in this editor.');
        return;
    }

    const cfg = map[entity];

    // build form element
    const form = document.createElement('form');
    form.method = 'post';
    form.action = window.location.href;

    // action hidden
    const actionInput = document.createElement('input');
    actionInput.type = 'hidden';
    actionInput.name = 'action';
    actionInput.value = cfg.action;
    form.appendChild(actionInput);

    // id hidden
    const idInput = document.createElement('input');
    idInput.type = 'hidden';
    idInput.name = cfg.idField;
    // robust id lookup: allow id, snake_case, camelCase and common variants
    let idVal = '';
    if (data.id) idVal = data.id;
    else if (data[cfg.idField]) idVal = data[cfg.idField];
    else {
        // try camelCase variant of cfg.idField
        const camel = cfg.idField.replace(/_([a-z])/g, (m,p)=>p.toUpperCase());
        if (data[camel]) idVal = data[camel];
        // common fallbacks
        else if (data.course_id) idVal = data.course_id;
        else if (data.courseId) idVal = data.courseId;
    }
    idInput.value = idVal || '';
    form.appendChild(idInput);

    // title
    const h = document.createElement('h3');
    h.textContent = cfg.title;
    form.appendChild(h);

    // build fields per entity
    if (entity === 'profesor') {
        // full_name
        const lblName = document.createElement('label'); lblName.textContent = 'Ime i prezime:';
        const inpName = document.createElement('input'); inpName.type = 'text'; inpName.name = 'full_name'; inpName.required = true; inpName.value = data.full_name || '';
        form.appendChild(lblName); form.appendChild(inpName);

        // email
        const lblEmail = document.createElement('label'); lblEmail.textContent = 'Email:';
        const inpEmail = document.createElement('input'); inpEmail.type = 'email'; inpEmail.name = 'email'; inpEmail.required = true; inpEmail.value = data.email || '';
        form.appendChild(lblEmail); form.appendChild(inpEmail);
    }

    if (entity === 'predmet') {
        const lblName = document.createElement('label'); lblName.textContent = 'Naziv predmeta:';
        const inpName = document.createElement('input'); inpName.type = 'text'; inpName.name = 'name'; inpName.required = true; inpName.value = data.name || '';
        form.appendChild(lblName); form.appendChild(inpName);

        const lblCode = document.createElement('label'); lblCode.textContent = 'Šifra predmeta:';
        const inpCode = document.createElement('input'); inpCode.type = 'text'; inpCode.name = 'code'; inpCode.required = true; inpCode.value = data.code || '';
        form.appendChild(lblCode); form.appendChild(inpCode);

        const lblSemester = document.createElement('label'); lblSemester.textContent = 'Semestar:';
        const inpSem = document.createElement('input'); inpSem.type = 'number'; inpSem.name = 'semester'; inpSem.min = 1; inpSem.max = 12; inpSem.required = true; inpSem.value = data.semester || '';
        form.appendChild(lblSemester); form.appendChild(inpSem);

        // checkbox row for proper alignment
        const cbRow = document.createElement('div'); cbRow.className = 'checkbox-row';
        const inpOptional = document.createElement('input'); inpOptional.type = 'checkbox'; inpOptional.name = 'is_optional'; inpOptional.checked = (data.is_optional === '1' || data.is_optional === 'true' || data.is_optional === 'on');
        const txt = document.createTextNode(' Izborni predmet');
        cbRow.appendChild(inpOptional); cbRow.appendChild(txt);
        form.appendChild(cbRow);

        // prof_assignments hidden (JSON)
        const pa = document.createElement('input'); pa.type = 'hidden'; pa.name = 'prof_assignments';
        // raw data-professors attribute may contain JSON string
        pa.value = (data.professors !== undefined) ? data.professors : (data.professors_list || '[]');
        form.appendChild(pa);

        // Try to provide server-required professor_id (single) — extract from data or prof_assignments
        const profIdInput = document.createElement('input');
        profIdInput.name = 'professor_id';
        let profId = data.professor_id || data.professorId || data.professor || '';
        try {
            const paVal = pa.value;
            if (!profId && paVal) {
                const parsed = JSON.parse(paVal);
                if (Array.isArray(parsed) && parsed.length > 0) {
                    const first = parsed[0];
                    if (typeof first === 'object') {
                        // common shapes: {id:123} or {professor_id:123}
                        profId = first.id || first.professor_id || first.professorId || '';
                    } else {
                        profId = first; // assume it's an id
                    }
                }
            }
        } catch (e) {
            // ignore JSON parse errors
        }
        // If we have a professor id, keep field hidden; otherwise make it visible and required
        if (profId) {
            profIdInput.type = 'hidden';
            profIdInput.value = profId;
            form.appendChild(profIdInput);
        } else {
            // visible numeric input to avoid server fatal error when missing
            const lblProfManual = document.createElement('label'); lblProfManual.textContent = 'Profesor (ID) (required):';
            profIdInput.type = 'number';
            profIdInput.required = true;
            profIdInput.placeholder = 'Enter professor id';
            profIdInput.style.marginBottom = '8px';
            form.appendChild(lblProfManual);
            form.appendChild(profIdInput);
        }

        // helper note
        const note = document.createElement('p'); note.style.fontSize = '12px'; note.style.opacity = '0.8'; note.textContent = 'If needed, adjust professors using assign dialog after saving.';
        form.appendChild(note);
    }

    if (entity === 'dogadjaj') {
        // course_id
        const lblCourse = document.createElement('label'); lblCourse.textContent = 'Predmet (ID):';
        const inpCourse = document.createElement('input'); inpCourse.type = 'number'; inpCourse.name = 'course_id'; inpCourse.required = true; inpCourse.value = data.course_id || data.courseId || '';
        form.appendChild(lblCourse); form.appendChild(inpCourse);

        // professor id
        const lblProf = document.createElement('label'); lblProf.textContent = 'Profesor (ID):';
        const inpProf = document.createElement('input'); inpProf.type = 'number'; inpProf.name = 'professor_id'; inpProf.required = true; inpProf.value = data.professor_id || data.professorId || '';
        form.appendChild(lblProf); form.appendChild(inpProf);

        // type select
        const lblType = document.createElement('label'); lblType.textContent = 'Tip događaja:';
        const selType = document.createElement('select'); selType.name = 'type';
        const optExam = document.createElement('option'); optExam.value = 'EXAM'; optExam.textContent = 'Ispit';
        const optCol = document.createElement('option'); optCol.value = 'COLLOQUIUM'; optCol.textContent = 'Kolokvijum';
        selType.appendChild(optExam); selType.appendChild(optCol);
        if ((data.type || data.type_enum) === 'COLLOQUIUM') selType.value = 'COLLOQUIUM'; else selType.value = 'EXAM';
        form.appendChild(lblType); form.appendChild(selType);

        // starts_at / ends_at
        const lblStart = document.createElement('label'); lblStart.textContent = 'Početak:';
        const inpStart = document.createElement('input'); inpStart.type = 'datetime-local'; inpStart.name = 'starts_at'; inpStart.value = toDatetimeLocal(data.starts_at || data.startsAt || '');
        form.appendChild(lblStart); form.appendChild(inpStart);

        const lblEnd = document.createElement('label'); lblEnd.textContent = 'Kraj:';
        const inpEnd = document.createElement('input'); inpEnd.type = 'datetime-local'; inpEnd.name = 'ends_at'; inpEnd.value = toDatetimeLocal(data.ends_at || data.endsAt || '');
        form.appendChild(lblEnd); form.appendChild(inpEnd);

        // is_online
        const cbOnline = document.createElement('div'); cbOnline.className = 'checkbox-row';
        const inpOnline = document.createElement('input'); inpOnline.type = 'checkbox'; inpOnline.name = 'is_online'; inpOnline.checked = (data.is_online === '1' || data.is_online === 'true' || data.is_online === 'on');
        cbOnline.appendChild(inpOnline); cbOnline.appendChild(document.createTextNode(' Online događaj'));
        form.appendChild(cbOnline);

        // room_id
        const lblRoom = document.createElement('label'); lblRoom.textContent = 'Sala (ID):';
        const inpRoom = document.createElement('input'); inpRoom.type = 'number'; inpRoom.name = 'room_id'; inpRoom.value = data.room_id || data.roomId || '';
        form.appendChild(lblRoom); form.appendChild(inpRoom);

        // notes
        const lblNotes = document.createElement('label'); lblNotes.textContent = 'Napomene:';
        const taNotes = document.createElement('textarea'); taNotes.name = 'notes'; taNotes.rows = 3; taNotes.value = data.notes || '';
        form.appendChild(lblNotes); form.appendChild(taNotes);

        // is_published
        const cbPub = document.createElement('div'); cbPub.className = 'checkbox-row';
        const inpPub = document.createElement('input'); inpPub.type = 'checkbox'; inpPub.name = 'is_published'; inpPub.checked = (data.is_published === '1' || data.is_published === 'true' || data.is_published === 'on');
        cbPub.appendChild(inpPub); cbPub.appendChild(document.createTextNode(' Objavljeno'));
        form.appendChild(cbPub);

        // include event_professor_id if provided by data
        if (data.event_professor_id) {
            const ep = document.createElement('input'); ep.type = 'hidden'; ep.name = 'event_professor_id'; ep.value = data.event_professor_id; form.appendChild(ep);
        }
    }

    if (entity === 'sala') {
        const lblCode = document.createElement('label'); lblCode.textContent = 'Oznaka sale:';
        const inpCode = document.createElement('input'); inpCode.type = 'text'; inpCode.name = 'code'; inpCode.required = true; inpCode.value = data.code || '';
        form.appendChild(lblCode); form.appendChild(inpCode);

        const lblCap = document.createElement('label'); lblCap.textContent = 'Kapacitet:';
        const inpCap = document.createElement('input'); inpCap.type = 'number'; inpCap.name = 'capacity'; inpCap.min = 1; inpCap.required = true; inpCap.value = data.capacity || '';
        form.appendChild(lblCap); form.appendChild(inpCap);

        const cbLab = document.createElement('div'); cbLab.className = 'checkbox-row';
        const inpLab = document.createElement('input'); inpLab.type = 'checkbox'; inpLab.name = 'is_computer_lab'; inpLab.checked = (data.is_computer_lab === '1' || data.is_computer_lab === 'true' || data.is_computer_lab === 'on');
        cbLab.appendChild(inpLab); cbLab.appendChild(document.createTextNode(' Računarska sala'));
        form.appendChild(cbLab);
    }

    // account editing form
    if (entity === 'account') {
        // username
        const lblUser = document.createElement('label'); lblUser.textContent = 'Korisničko ime:';
        const inpUser = document.createElement('input'); inpUser.type = 'text'; inpUser.name = 'username'; inpUser.required = true; inpUser.value = data.username || '';
        form.appendChild(lblUser); form.appendChild(inpUser);

        // password (optional)
        const lblPass = document.createElement('label'); lblPass.textContent = 'Lozinka (ostavite prazno da ne mijenjate):';
        const inpPass = document.createElement('input'); inpPass.type = 'password'; inpPass.name = 'password'; inpPass.value = '';
        form.appendChild(lblPass); form.appendChild(inpPass);

        // role
        const lblRole = document.createElement('label'); lblRole.textContent = 'Uloga:';
        const selRole = document.createElement('select'); selRole.name = 'role';
        ['ADMIN','PROFESSOR'].forEach(r => { const o = document.createElement('option'); o.value = r; o.textContent = r; if ((data.role || data.role_enum) === r) o.selected = true; selRole.appendChild(o); });
        form.appendChild(lblRole); form.appendChild(selRole);

        // professor link
        const lblProf = document.createElement('label'); lblProf.textContent = 'Povezan profesor (opcionalno):';
        const selProf = document.createElement('select'); selProf.name = 'professor_id';
        const optNone = document.createElement('option'); optNone.value = ''; optNone.textContent = '-- Nema --'; selProf.appendChild(optNone);
        try {
            const profs = window.adminData && window.adminData.professors ? window.adminData.professors : [];
            profs.forEach(p => {
                const o = document.createElement('option'); o.value = p.id; o.textContent = p.full_name + ' (' + p.email + ')';
                if (String(data.professor_id) === String(p.id)) o.selected = true;
                selProf.appendChild(o);
            });
        } catch (e) {
            console.warn('Could not populate professors select', e);
        }
        form.appendChild(lblProf); form.appendChild(selProf);

        // is_active checkbox
        const cbAct = document.createElement('div'); cbAct.className = 'checkbox-row';
        const inpAct = document.createElement('input'); inpAct.type = 'checkbox'; inpAct.name = 'is_active'; inpAct.checked = (data.is_active === '1' || data.is_active === 'true' || data.is_active === 1 || data.is_active === true);
        cbAct.appendChild(inpAct); cbAct.appendChild(document.createTextNode(' Aktivan'));
        form.appendChild(cbAct);
    }

    // submit
    const submit = document.createElement('button'); submit.type = 'submit'; submit.textContent = 'Sačuvaj izmjene'; submit.className = 'action-button save-button';
    form.appendChild(submit);

    // attach form submit: let normal POST happen

    // render
    content.innerHTML = '';
    content.appendChild(form);
    modal.style.display = 'block';
}
