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
});