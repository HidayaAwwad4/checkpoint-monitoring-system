const API_BASE_URL = 'http://localhost:3000/api';

let allCheckpoints = [];
let filteredCheckpoints = [];
let currentFilter = 'all';


const checkpointsGrid = document.getElementById('checkpointsGrid');
const loading = document.getElementById('loading');
const errorMessage = document.getElementById('errorMessage');
const noResults = document.getElementById('noResults');
const openCount = document.getElementById('openCount');
const closedCount = document.getElementById('closedCount');
const searchInput = document.getElementById('searchInput');
const filterButtons = document.querySelectorAll('.filter-btn');
const refreshBtn = document.getElementById('refreshBtn');
const modal = document.getElementById('checkpointModal');
const modalClose = document.getElementById('modalClose');
const modalBody = document.getElementById('modalBody');

document.addEventListener('DOMContentLoaded', () => {
    fetchCheckpoints();
    fetchStatistics();
    setupEventListeners();

    setInterval(() => {
        fetchCheckpoints();
        fetchStatistics();
    }, 30000);
});

function setupEventListeners() {
    searchInput.addEventListener('input', (e) => {
        const query = e.target.value.trim();
        if (query) {
            searchCheckpoints(query);
        } else {
            filteredCheckpoints = allCheckpoints;
            applyFilter(currentFilter);
        }
    });

    filterButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            filterButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentFilter = btn.dataset.filter;
            applyFilter(currentFilter);
        });
    });

    refreshBtn.addEventListener('click', () => {
        refreshBtn.style.animation = 'none';
        setTimeout(() => {
            refreshBtn.style.animation = '';
        }, 10);
        fetchCheckpoints();
        fetchStatistics();
    });

    modalClose.addEventListener('click', () => {
        modal.classList.remove('show');
    });

    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.classList.remove('show');
        }
    });
}

async function fetchCheckpoints() {
    try {
        showLoading(true);
        hideError();

        const response = await fetch(`${API_BASE_URL}/checkpoints`);
        const data = await response.json();

        if (data.success) {
            allCheckpoints = data.data;
            filteredCheckpoints = allCheckpoints;
            applyFilter(currentFilter);
        } else {
            showError();
        }
    } catch (error) {
        console.error('Error fetching checkpoints:', error);
        showError();
    } finally {
        showLoading(false);
    }
}

async function fetchStatistics() {
    try {
        const response = await fetch(`${API_BASE_URL}/statistics`);
        const data = await response.json();

        if (data.success) {
            updateStatistics(data.data);
        }
    } catch (error) {
        console.error('Error fetching statistics:', error);
    }
}

async function searchCheckpoints(query) {
    try {
        const response = await fetch(`${API_BASE_URL}/search?q=${encodeURIComponent(query)}`);
        const data = await response.json();

        if (data.success) {
            filteredCheckpoints = data.data;
            applyFilter(currentFilter);
        }
    } catch (error) {
        console.error('Error searching checkpoints:', error);
    }
}

function applyFilter(filter) {
    let filtered = [...filteredCheckpoints];

    if (filter !== 'all') {
        filtered = filtered.filter(checkpoint => {
            const status = checkpoint.status.toLowerCase();
            return status.includes(filter);
        });
    }

    renderCheckpoints(filtered);
}

function renderCheckpoints(checkpoints) {
    if (checkpoints.length === 0) {
        checkpointsGrid.innerHTML = '';
        noResults.style.display = 'block';
        return;
    }

    noResults.style.display = 'none';

    checkpointsGrid.innerHTML = checkpoints.map(checkpoint => {
        const statusClass = getStatusClass(checkpoint.status);
        const statusText = getStatusText(checkpoint.status);
        const timeAgo = formatTimeAgo(checkpoint.lastUpdated);

        return `
            <div class="checkpoint-card ${statusClass}" onclick="showCheckpointDetails('${checkpoint.checkpointId}')">
                <div class="checkpoint-header">
                    <h3 class="checkpoint-name">${checkpoint.checkpointName}</h3>
                    <span class="status-badge ${statusClass}">
                        <span class="status-indicator"></span>
                        ${statusText}
                    </span>
                </div>
                <p class="checkpoint-message">${checkpoint.messageContent || 'لا توجد معلومات إضافية'}</p>
                <div class="checkpoint-footer">
                    <span class="checkpoint-time">⏰ ${timeAgo}</span>
                    <span class="checkpoint-confidence">🎯 ${checkpoint.confidence}</span>
                </div>
            </div>
        `;
    }).join('');
}

function updateStatistics(stats) {
    openCount.textContent = stats.open;
    closedCount.textContent = stats.closed;
}

async function showCheckpointDetails(checkpointId) {
    try {
        const response = await fetch(`${API_BASE_URL}/checkpoints/${checkpointId}?limit=5`);
        const data = await response.json();

        if (data.success) {
            const { current, history } = data.data;

            modalBody.innerHTML = `
                <h2 style="margin-bottom: 1rem; color: var(--text-primary);">${current.checkpointName}</h2>
                
                <div style="background-color: var(--bg-color); padding: 1rem; border-radius: 8px; margin-bottom: 1.5rem;">
                    <h3 style="margin-bottom: 0.5rem; color: var(--text-secondary); font-size: 0.9rem;">الحالة الحالية</h3>
                    <p style="font-size: 1.1rem; margin-bottom: 0.5rem;">
                        <span class="status-badge ${getStatusClass(current.status)}">
                            <span class="status-indicator"></span>
                            ${getStatusText(current.status)}
                        </span>
                    </p>
                    <p style="color: var(--text-secondary); margin-bottom: 0.5rem;">${current.messageContent}</p>
                    <p style="color: var(--text-secondary); font-size: 0.9rem;">⏰ ${formatDateTime(current.lastUpdated)}</p>
                </div>

                <h3 style="margin-bottom: 1rem; color: var(--text-secondary); font-size: 1rem;">آخر التحديثات</h3>
                <div style="display: flex; flex-direction: column; gap: 0.75rem;">
                    ${history.map(item => `
                        <div style="padding: 0.75rem; border-right: 3px solid ${getStatusColor(item.status)}; background-color: var(--bg-color); border-radius: 4px;">
                            <div style="display: flex; justify-content: space-between; margin-bottom: 0.25rem;">
                                <span style="font-weight: 600;">${getStatusText(item.status)}</span>
                                <span style="font-size: 0.85rem; color: var(--text-secondary);">${formatDateTime(item.lastUpdated)}</span>
                            </div>
                            <p style="font-size: 0.9rem; color: var(--text-secondary);">${item.messageContent}</p>
                        </div>
                    `).join('')}
                </div>
            `;

            modal.classList.add('show');
        }
    } catch (error) {
        console.error('Error fetching checkpoint details:', error);
    }
}

function getStatusClass(status) {
    const s = status.toLowerCase();
    if (s.includes('open')) return 'status-open';
    if (s.includes('closed')) return 'status-closed';
    if (s.includes('busy')) return 'status-busy';
    return 'status-open';
}

function getStatusText(status) {
    const s = status.toLowerCase();
    if (s.includes('open')) return 'مفتوح';
    if (s.includes('closed')) return 'مغلق';
    if (s.includes('busy')) return 'مزدحم';
    return status;
}

function getStatusColor(status) {
    const s = status.toLowerCase();
    if (s.includes('open')) return '#10b981';
    if (s.includes('closed')) return '#ef4444';
    if (s.includes('busy')) return '#f59e0b';
    return '#10b981';
}

function formatTimeAgo(dateString) {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'الآن';
    if (diffMins < 60) return `منذ ${diffMins} دقيقة`;
    if (diffHours < 24) return `منذ ${diffHours} ساعة`;
    return `منذ ${diffDays} يوم`;
}

function formatDateTime(dateString) {
    const date = new Date(dateString);
    return date.toLocaleString('ar-PS', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function showLoading(show) {
    loading.style.display = show ? 'block' : 'none';
}

function showError() {
    errorMessage.style.display = 'block';
}

function hideError() {
    errorMessage.style.display = 'none';
}