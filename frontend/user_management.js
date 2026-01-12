// User management functions

import { users, selectedUserId, setCookie, showToast, initializeAppIfNeeded, resetUserForm, setUsers, setSelectedUserId } from './utils.js';
import { usersAPI } from './api.js';

export async function showUserPickScreen() {
    const appLayout = document.getElementById('app-layout');
    const pickScreen = document.getElementById('user-pick-screen');
    if (appLayout) appLayout.style.display = 'none';
    if (pickScreen) pickScreen.style.display = 'flex';

    // Сначала показываем форму создания (мгновенный отклик)
    // Это нужно, чтобы пользователь не ждал таймаута подключения, если backend недоступен
    renderUserPickButtons([]);

    // Затем пытаемся загрузить пользователей в фоне
    // Если они загрузятся, интерфейс обновится автоматически
    try {
        console.log('Loading users for pick screen...');
        const startTime = Date.now();
        setUsers(await usersAPI.getAll());
        const loadTime = Date.now() - startTime;
        console.log(`Loaded users in ${loadTime}ms:`, users);

        // Если пользователи загрузились, обновляем интерфейс
        if (Array.isArray(users) && users.length > 0) {
            renderUserPickButtons(users);
        }
        // Если список пуст, форма уже показана - ничего не делаем
    } catch (e) {
        console.error('Failed to load users for pick screen', e);
        // Форма уже показана, ничего не делаем
    }
}

export function renderUserPickButtons(userList) {
    const list = document.getElementById('user-pick-list');
    if (!list) {
        console.error('user-pick-list element not found');
        return;
    }
    // Показываем форму создания, если список пуст или не является массивом
    if (!Array.isArray(userList) || userList.length === 0) {
        console.log('No users found, showing create form');
        // Меняем стиль контейнера для корректного отображения формы
        list.style.display = 'block';
        list.style.flexWrap = 'nowrap';
        list.innerHTML = `
            <div style="width:100%; text-align:left;">
                <p style="margin-bottom:12px;">В базе пока нет пользователей. Создайте первого, чтобы продолжить работу.</p>
                <form id="user-pick-create-form" style="display:flex; flex-direction:column; gap:12px;">
                    <div style="display:flex; flex-direction:column; gap:4px;">
                        <label for="user-pick-name" style="font-size:0.9em; color:#374151;">Имя</label>
                        <input type="text" id="user-pick-name" required placeholder="Например, Сергей" style="padding:10px; border:1px solid #d1d5db; border-radius:6px;">
                    </div>
                    <div style="display:flex; flex-direction:column; gap:4px;">
                        <label for="user-pick-email" style="font-size:0.9em; color:#374151;">Почта (необязательно)</label>
                        <input type="email" id="user-pick-email" placeholder="user@example.com" style="padding:10px; border:1px solid #d1d5db; border-radius:6px;">
                    </div>
                    <div style="display:flex; flex-direction:column; gap:4px;">
                        <label for="user-pick-role" style="font-size:0.9em; color:#374151;">Привилегии</label>
                        <select id="user-pick-role" style="padding:10px; border:1px solid #d1d5db; border-radius:6px;">
                            <option value="regular" selected>Обычный</option>
                            <option value="admin">Администратор</option>
                            <option value="guest">Гость</option>
                        </select>
                    </div>
                    <button type="submit" class="btn btn-primary" style="align-self:flex-start;">Создать и продолжить</button>
                </form>
            </div>
        `;
        const form = document.getElementById('user-pick-create-form');
        if (form) {
            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                const nameInput = document.getElementById('user-pick-name');
                const emailInput = document.getElementById('user-pick-email');
                const roleSelect = document.getElementById('user-pick-role');
                const name = nameInput.value.trim();
                const email = emailInput.value.trim();
                const role = roleSelect.value || 'regular';
                if (!name) {
                    nameInput.focus();
                    return;
                }
                form.querySelector('button[type="submit"]').disabled = true;
                try {
                    const created = await usersAPI.create({
                        name,
                        email: email || undefined,
                        role,
                        is_active: true,
                    });
                    users = [created];
                    setCookie('hp.selectedUserId', created.id);
                    selectedUserId = created.id;
                    showToast('Пользователь создан', 'success');
                    await initializeAppIfNeeded();
                } catch (err) {
                    console.error('Failed to create user from pick screen', err);
                    showToast('Не удалось создать пользователя. Попробуйте ещё раз.', 'error');
                    form.querySelector('button[type="submit"]').disabled = false;
                }
            });
        }
        return;
    }
    list.innerHTML = userList
        .map((u) => {
            const name = (u && (u.name || u.display_name || `#${u.id}`)) || '—';
            return `<button class="btn btn-primary" data-user-id="${String(u.id)}" style="flex:1 1 auto; min-width:180px;">${name}</button>`;
        })
        .join('');
    list.querySelectorAll('button[data-user-id]').forEach((btn) => {
        btn.addEventListener('click', async (e) => {
            const id = parseInt(e.currentTarget.getAttribute('data-user-id'), 10);
            if (!Number.isFinite(id)) return;
            setCookie('hp.selectedUserId', id);
            setSelectedUserId(id);
            await initializeAppIfNeeded();
            showToast('Пользователь выбран', 'success');
        });
    });
}

export function renderUsersList() {
    const container = document.getElementById('users-list');
    if (!container) return;
    const sortedUsers = [...users].sort((a, b) => {
        if (a.is_active !== b.is_active) {
            return a.is_active ? -1 : 1;
        }
        return a.name.localeCompare(b.name, 'ru');
    });
    if (!sortedUsers.length) {
        container.innerHTML = `
            <div class="empty-state" style="min-height: unset; padding: 12px;">
                <div class="empty-state-icon">👥</div>
                <div class="empty-state-text">Нет пользователей</div>
                <div class="empty-state-hint">Добавьте хотя бы одного пользователя, чтобы назначать задачи</div>
            </div>
        `;
        return;
    }
    container.innerHTML = sortedUsers.map(user => `
        <div class="user-row">
            <div class="user-info">
                <span class="user-name">${escapeHtml(user.name)}</span>
                ${user.email ? `<span class="user-email">${escapeHtml(user.email)}</span>` : '<span class="user-email">Без email</span>'}
                <div class="user-meta">
                    <span class="user-chip">${USER_ROLE_LABELS[user.role] || user.role}</span>
                    <span class="user-chip ${user.is_active ? 'user-chip-active' : 'user-chip-inactive'}">${USER_STATUS_LABELS[user.is_active] || ''}</span>
                </div>
            </div>
            <div class="user-actions">
                <button class="btn btn-secondary btn-sm" onclick="editUser(${user.id})" title="Редактировать">✎</button>
                <button class="btn btn-danger btn-sm" onclick="deleteUser(${user.id})" title="Удалить">✕</button>
            </div>
        </div>
    `).join('');
}

export function renderUsersView() {
    renderUsersList();
}

export function editUser(id) {
    const user = users.find(u => u.id === id);
    if (!user) return;
    const title = document.getElementById('user-form-title');
    if (title) title.textContent = 'Редактировать пользователя';
    document.getElementById('user-id').value = user.id;
    document.getElementById('user-name').value = user.name;
    document.getElementById('user-email').value = user.email || '';
    const roleSelect = document.getElementById('user-role');
    if (roleSelect) roleSelect.value = user.role || 'regular';
    const activeCheckbox = document.getElementById('user-active');
    if (activeCheckbox) activeCheckbox.checked = !!user.is_active;
    const saveBtn = document.getElementById('user-save');
    if (saveBtn) saveBtn.textContent = 'Сохранить';
    switchView('users');
}

export async function deleteUser(id) {
    if (!confirm('Удалить пользователя? Назначенные задачи потеряют связь с ним.')) return;
    try {
        await usersAPI.delete(id);
        showToast('Пользователь удалён', 'success');
        resetUserForm();
        await loadData();
    } catch (error) {
        console.error('Failed to delete user', error);
        showToast(error.message || 'Не удалось удалить пользователя', 'error');
    }
}

export async function handleUserSubmit(e) {
    e.preventDefault();
    const id = document.getElementById('user-id').value;
    const name = document.getElementById('user-name').value.trim();
    const email = document.getElementById('user-email').value.trim();
    if (!name) {
        showToast('Имя пользователя обязательно', 'warning');
        return;
    }
    const role = document.getElementById('user-role').value;
    const isActive = document.getElementById('user-active').checked;
    const payload = { name, email: email || null, role, is_active: isActive };
    try {
        if (id) {
            await usersAPI.update(parseInt(id, 10), payload);
            showToast('Пользователь обновлён', 'success');
        } else {
            await usersAPI.create(payload);
            showToast('Пользователь создан', 'success');
        }
        resetUserForm();
        await loadData();
    } catch (error) {
        console.error('Failed to save user', error);
        showToast(error.message || 'Не удалось сохранить пользователя', 'error');
    }
}

// Need to import these
import { escapeHtml, USER_ROLE_LABELS, USER_STATUS_LABELS, switchView, loadData } from './utils.js';