// API Base URL
const API_BASE_URL = 'http://localhost:3000/api';

// State
let allCheckpoints = [];
let filteredCheckpoints = [];
let currentFilter = 'all';

// DOM Elements
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

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    fetchCheckpoints();
    fetchStatistics();
    setupEventListeners();

    // Auto refresh every 30 seconds
    setInterval(() => {
        fetchCheckpoints();
        fetchStatistics();
    }, 30000);
});

// Setup Event Listeners
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

// Fetch all checkpoints
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

// Fetch statistics
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

// Search checkpoints
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

// Apply filter
function applyFilter(filter) {
    let filtered = [...filteredCheckpoints];

    if (filter !== 'all') {
        filtered = filtered.filter(checkpoint => {
            const general = checkpoint.generalStatus?.toLowerCase() || '';
            const inbound = checkpoint.inboundStatus?.status?.toLowerCase() || '';
            const outbound = checkpoint.outboundStatus?.status?.toLowerCase() || '';

            return general.includes(filter) ||
                inbound.includes(filter) ||
                outbound.includes(filter);
        });
    }

    renderCheckpoints(filtered);
}

// Render checkpoints
function renderCheckpoints(checkpoints) {
    if (checkpoints.length === 0) {
        checkpointsGrid.innerHTML = '';
        noResults.style.display = 'block';
        return;
    }

    noResults.style.display = 'none';

    checkpointsGrid.innerHTML = checkpoints.map(checkpoint => {
        const displayInfo = getDisplayInfo(checkpoint);
        const timeAgo = formatTimeAgo(checkpoint.lastUpdated);

        return `
            <div class="checkpoint-card" onclick="showCheckpointDetails('${checkpoint.checkpointId}')">
                <div class="checkpoint-header">
                    <h3 class="checkpoint-name">${checkpoint.checkpointName}</h3>
                </div>
                
                ${displayInfo.html}
                
                <p class="checkpoint-message">${checkpoint.messageContent || 'لا توجد معلومات إضافية'}</p>
                <div class="checkpoint-footer" style="display: flex; justify-content: space-between; margin-top: 0.75rem; font-size: 0.85rem; color: var(--text-secondary);">
                    <span>⏰ ${timeAgo}</span>
                    <span>🎯 ${Math.round(checkpoint.confidence * 100)}%</span>
                </div>
            </div>
        `;
    }).join('');
}

// Get display info based on checkpoint status
function getDisplayInfo(checkpoint) {
    const general = checkpoint.generalStatus?.toLowerCase() || 'unknown';
    const inbound = checkpoint.inboundStatus || {};
    const outbound = checkpoint.outboundStatus || {};

    // حالة واحدة للاتجاهين
    if (general !== 'mixed' && general !== 'partial') {
        return {
            html: `
                <div style="margin-bottom: 0.75rem;">
                    <span class="status-badge ${getStatusClass(general)}">
                        <span class="status-indicator"></span>
                        ${getStatusText(general)}
                    </span>
                </div>
            `
        };
    }

    // حالة مختلطة أو جزئية
    return {
        html: `
            <div style="margin-bottom: 0.75rem; display: flex; flex-direction: column; gap: 0.5rem;">
                <div style="display: flex; align-items: center; gap: 0.5rem;">
                    <span style="font-size: 0.85rem; color: var(--text-secondary); min-width: 60px;">↓ للداخل:</span>
                    <span class="status-badge ${getStatusClass(inbound.status)}">
                        <span class="status-indicator"></span>
                        ${getStatusText(inbound.status)}
                    </span>
                    ${!inbound.isRecent ? '<span style="font-size: 0.75rem; color: var(--warning-color);">⚠️ قديم</span>' : ''}
                </div>
                <div style="display: flex; align-items: center; gap: 0.5rem;">
                    <span style="font-size: 0.85rem; color: var(--text-secondary); min-width: 60px;">↑ للخارج:</span>
                    <span class="status-badge ${getStatusClass(outbound.status)}">
                        <span class="status-indicator"></span>
                        ${getStatusText(outbound.status)}
                    </span>
                    ${!outbound.isRecent ? '<span style="font-size: 0.75rem; color: var(--warning-color);">⚠️ قديم</span>' : ''}
                </div>
            </div>
        `
    };
}

// Update statistics
function updateStatistics(stats) {
    openCount.textContent = stats.open || 0;
    closedCount.textContent = stats.closed || 0;
}

// Show checkpoint details
async function showCheckpointDetails(checkpointId) {
    try {
        const response = await fetch(`${API_BASE_URL}/checkpoints/${checkpointId}?limit=5`);
        const data = await response.json();

        if (data.success) {
            const { current, history } = data.data;
            const general = current.generalStatus?.toLowerCase() || 'unknown';

            let statusHtml = '';
            if (general === 'mixed' || general === 'partial') {
                statusHtml = `
                    <div style="display: flex; flex-direction: column; gap: 0.75rem;">
                        <div style="display: flex; align-items: center; gap: 0.75rem;">
                            <span style="font-weight: 500; min-width: 70px;">↓ للداخل:</span>
                            <span class="status-badge ${getStatusClass(current.inboundStatus?.status)}">
                                <span class="status-indicator"></span>
                                ${getStatusText(current.inboundStatus?.status)}
                            </span>
                            ${!current.inboundStatus?.isRecent ? '<span style="font-size: 0.8rem; color: var(--warning-color);">⚠️ المعلومة قد لا تكون دقيقة</span>' : ''}
                        </div>
                        <div style="display: flex; align-items: center; gap: 0.75rem;">
                            <span style="font-weight: 500; min-width: 70px;">↑ للخارج:</span>
                            <span class="status-badge ${getStatusClass(current.outboundStatus?.status)}">
                                <span class="status-indicator"></span>
                                ${getStatusText(current.outboundStatus?.status)}
                            </span>
                            ${!current.outboundStatus?.isRecent ? '<span style="font-size: 0.8rem; color: var(--warning-color);">⚠️ المعلومة قد لا تكون دقيقة</span>' : ''}
                        </div>
                    </div>
                `;
            } else {
                statusHtml = `
                    <span class="status-badge ${getStatusClass(general)}">
                        <span class="status-indicator"></span>
                        ${getStatusText(general)}
                    </span>
                `;
            }

            modalBody.innerHTML = `
                <h2 style="margin-bottom: 1rem; color: var(--text-primary);">${current.checkpointName}</h2>
                
                <div style="background-color: var(--bg-color); padding: 1.25rem; border-radius: 8px; margin-bottom: 1.5rem;">
                    <h3 style="margin-bottom: 0.75rem; color: var(--text-secondary); font-size: 0.9rem;">الحالة الحالية</h3>
                    ${statusHtml}
                    <p style="color: var(--text-secondary); margin: 0.75rem 0;">${current.messageContent}</p>
                    <p style="color: var(--text-secondary); font-size: 0.9rem;">⏰ ${formatDateTime(current.lastUpdated)}</p>
                </div>

                <h3 style="margin-bottom: 1rem; color: var(--text-secondary); font-size: 1rem;">آخر التحديثات</h3>
                <div style="display: flex; flex-direction: column; gap: 0.75rem;">
                    ${history.map(item => {
                const itemGeneral = item.generalStatus?.toLowerCase() || 'unknown';
                let itemStatusHtml = '';

                if (itemGeneral === 'mixed' || itemGeneral === 'partial') {
                    itemStatusHtml = `
                                <div style="font-size: 0.9rem;">
                                    <div>↓ ${getStatusText(item.inboundStatus?.status)}</div>
                                    <div>↑ ${getStatusText(item.outboundStatus?.status)}</div>
                                </div>
                            `;
                } else {
                    itemStatusHtml = `<span style="font-weight: 600;">${getStatusText(itemGeneral)}</span>`;
                }

                return `
                            <div style="padding: 0.75rem; border-right: 3px solid ${getStatusColor(itemGeneral)}; background-color: var(--bg-color); border-radius: 4px;">
                                <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem; align-items: start;">
                                    ${itemStatusHtml}
                                    <span style="font-size: 0.85rem; color: var(--text-secondary);">${formatDateTime(item.lastUpdated)}</span>
                                </div>
                                <p style="font-size: 0.9rem; color: var(--text-secondary);">${item.messageContent}</p>
                            </div>
                        `;
            }).join('')}
                </div>
            `;

            modal.classList.add('show');
        }
    } catch (error) {
        console.error('Error fetching checkpoint details:', error);
    }
}

// Helper functions
function getStatusClass(status) {
    if (!status) return 'status-open';
    const s = status.toLowerCase();
    if (s.includes('open') || s === 'open') return 'status-open';
    if (s.includes('closed') || s === 'closed') return 'status-closed';
    if (s.includes('busy') || s === 'busy') return 'status-busy';
    if (s === 'unknown') return 'status-unknown';
    return 'status-open';
}

function getStatusText(status) {
    if (!status) return 'غير معروف';
    const s = status.toLowerCase();
    if (s.includes('open') || s === 'open') return 'مفتوح';
    if (s.includes('closed') || s === 'closed') return 'مغلق';
    if (s.includes('busy') || s === 'busy') return 'مزدحم';
    if (s === 'unknown') return 'غير معروف';
    if (s === 'mixed') return 'مختلط';
    if (s === 'partial') return 'جزئي';
    return status;
}

function getStatusColor(status) {
    if (!status) return '#10b981';
    const s = status.toLowerCase();
    if (s.includes('open') || s === 'open') return '#10b981';
    if (s.includes('closed') || s === 'closed') return '#ef4444';
    if (s.includes('busy') || s === 'busy') return '#f59e0b';
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