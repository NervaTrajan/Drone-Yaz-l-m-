const authBox = document.getElementById('authBox');
const appBox = document.getElementById('appBox');
const message = document.getElementById('message');

const emailInput = document.getElementById('email');
const passwordInput = document.getElementById('password');
const registerBtn = document.getElementById('registerBtn');
const loginBtn = document.getElementById('loginBtn');
const logoutBtn = document.getElementById('logoutBtn');

const titleInput = document.getElementById('title');
const contentInput = document.getElementById('content');
const saveBtn = document.getElementById('saveBtn');
const cancelEditBtn = document.getElementById('cancelEditBtn');
const notesList = document.getElementById('notesList');

let token = localStorage.getItem('token') || '';
let editingId = null;

function setMessage(text, isError = false) {
  message.textContent = text;
  message.style.color = isError ? '#b00020' : '#206a2f';
}

function setAuthUI(isLoggedIn) {
  authBox.classList.toggle('hidden', isLoggedIn);
  appBox.classList.toggle('hidden', !isLoggedIn);
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(path, { ...options, headers });
  const data = await res.json().catch(() => ({}));

  if (!res.ok) {
    throw new Error(data.error || 'Bir hata oluştu');
  }

  return data;
}

async function register() {
  try {
    await api('/api/register', {
      method: 'POST',
      body: JSON.stringify({ email: emailInput.value.trim(), password: passwordInput.value })
    });
    setMessage('Kayıt başarılı. Şimdi giriş yap.');
  } catch (error) {
    setMessage(error.message, true);
  }
}

async function login() {
  try {
    const data = await api('/api/login', {
      method: 'POST',
      body: JSON.stringify({ email: emailInput.value.trim(), password: passwordInput.value })
    });
    token = data.token;
    localStorage.setItem('token', token);
    setAuthUI(true);
    setMessage('Giriş başarılı.');
    await loadNotes();
  } catch (error) {
    setMessage(error.message, true);
  }
}

function logout() {
  token = '';
  localStorage.removeItem('token');
  editingId = null;
  setAuthUI(false);
  notesList.innerHTML = '';
  setMessage('Çıkış yapıldı.');
}

async function loadNotes() {
  try {
    const notes = await api('/api/notes');
    notesList.innerHTML = '';

    notes.forEach((note) => {
      const li = document.createElement('li');
      li.innerHTML = `
        <div class="noteHeader">
          <strong>${note.title}</strong>
          <small>${new Date(note.updatedAt).toLocaleString('tr-TR')}</small>
        </div>
        <p>${(note.content || '').replace(/</g, '&lt;')}</p>
        <div class="noteActions">
          <button data-edit="${note.id}">Düzenle</button>
          <button data-delete="${note.id}">Sil</button>
        </div>
      `;
      notesList.appendChild(li);
    });
  } catch (error) {
    setMessage(error.message, true);
  }
}

async function saveNote() {
  try {
    const payload = { title: titleInput.value.trim(), content: contentInput.value.trim() };

    if (editingId) {
      await api(`/api/notes/${editingId}`, { method: 'PUT', body: JSON.stringify(payload) });
      setMessage('Not güncellendi.');
    } else {
      await api('/api/notes', { method: 'POST', body: JSON.stringify(payload) });
      setMessage('Not kaydedildi.');
    }

    titleInput.value = '';
    contentInput.value = '';
    editingId = null;
    cancelEditBtn.classList.add('hidden');
    await loadNotes();
  } catch (error) {
    setMessage(error.message, true);
  }
}

function startEdit(noteId) {
  const item = [...notesList.querySelectorAll('button[data-edit]')].find((b) => b.dataset.edit === noteId)?.closest('li');
  if (!item) return;

  const title = item.querySelector('strong').textContent;
  const content = item.querySelector('p').textContent;
  titleInput.value = title;
  contentInput.value = content;
  editingId = noteId;
  cancelEditBtn.classList.remove('hidden');
}

async function deleteNote(noteId) {
  try {
    await api(`/api/notes/${noteId}`, { method: 'DELETE' });
    setMessage('Not silindi.');
    await loadNotes();
  } catch (error) {
    setMessage(error.message, true);
  }
}

notesList.addEventListener('click', async (event) => {
  const editId = event.target.dataset.edit;
  const deleteId = event.target.dataset.delete;

  if (editId) startEdit(editId);
  if (deleteId) await deleteNote(deleteId);
});

cancelEditBtn.addEventListener('click', () => {
  editingId = null;
  titleInput.value = '';
  contentInput.value = '';
  cancelEditBtn.classList.add('hidden');
});

registerBtn.addEventListener('click', register);
loginBtn.addEventListener('click', login);
logoutBtn.addEventListener('click', logout);
saveBtn.addEventListener('click', saveNote);

if (token) {
  setAuthUI(true);
  loadNotes();
} else {
  setAuthUI(false);
}
