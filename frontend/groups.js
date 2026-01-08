// Group management functions

import { groups, showToast, loadData } from './utils.js';

export function openGroupModal(groupId = null) {
    const modal = document.getElementById('group-modal');
    const form = document.getElementById('group-form');
    const title = document.getElementById('group-modal-title');

    if (groupId) {
        const group = groups.find(g => g.id === groupId);
        if (group) {
            title.textContent = 'Редактировать группу';
            document.getElementById('group-id').value = group.id;
            document.getElementById('group-name').value = group.name;
            document.getElementById('group-description').value = group.description || '';
        }
    } else {
        title.textContent = 'Создать группу';
        form.reset();
        document.getElementById('group-id').value = '';
    }

    modal.classList.add('show');
    modal.style.display = 'block';
}

export function closeGroupModal() {
    const modal = document.getElementById('group-modal');
    modal.style.display = 'none';
    modal.classList.remove('show');
    document.getElementById('group-form').reset();
}

export async function handleGroupSubmit(e) {
    e.preventDefault();
    const id = document.getElementById('group-id').value;
    const groupData = {
        name: document.getElementById('group-name').value,
        description: document.getElementById('group-description').value || null,
    };

    try {
        const submitBtn = e.target.querySelector('button[type="submit"]');
        const originalText = submitBtn.textContent;
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner"></span> Сохранение...';

        if (id) {
            await groupsAPI.update(parseInt(id), groupData);
            showToast('Группа обновлена', 'success');
        } else {
            await groupsAPI.create(groupData);
            showToast('Группа создана', 'success');
        }

        closeGroupModal();
        await loadData();

        submitBtn.disabled = false;
        submitBtn.textContent = originalText;
    } catch (error) {
        showToast('Ошибка сохранения группы: ' + error.message, 'error');
        const submitBtn = e.target.querySelector('button[type="submit"]');
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Сохранить';
    }
}

export function editGroup(id) {
    openGroupModal(id);
}

export async function deleteGroup(id) {
    if (!confirm('Удалить группу? Все задачи в группе останутся, но будут без группы.')) return;
    try {
        await groupsAPI.delete(id);
        showToast('Группа удалена', 'success');
        await loadData();
    } catch (error) {
        showToast('Ошибка удаления группы: ' + error.message, 'error');
    }
}