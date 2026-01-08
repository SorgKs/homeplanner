// User pick screen logic

import { setCookie, showToast } from './utils.js';

// Global variables (imported from utils.js)
let users = [];
let selectedUserId = null;

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
        users = await usersAPI.getAll();
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
            selectedUserId = id;
            await initializeAppIfNeeded();
            showToast('Пользователь выбран', 'success');
        });
    });
}

// This function needs to be imported or defined elsewhere
// For now, we'll assume it's available
// import { initializeAppIfNeeded } from './utils.js';