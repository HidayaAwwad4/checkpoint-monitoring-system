const express = require('express');
const path = require('path');
const cors = require('cors');
const { connectDB } = require('./config/db');
const apiRoutes = require('./routes/api');
const checkpointRoutes = require('./routes/checkpoints');

const app = express();
const PORT = process.env.PORT || 3000;
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, 'public')));
app.use('/api', apiRoutes);
app.use('/checkpoints', checkpointRoutes);
app.get('/', (req, res) => {
    res.redirect('/checkpoints');
});
app.get('/health', (req, res) => {
    res.json({
        success: true,
        message: 'Server is running',
        timestamp: new Date().toISOString()
    });
});
app.use((req, res) => {
    res.status(404).json({
        success: false,
        error: 'الصفحة غير موجودة'
    });
});
app.use((err, req, res, next) => {
    console.error('Server error:', err);
    res.status(500).json({
        success: false,
        error: 'خطأ في السيرفر'
    });
});
async function startServer() {
    try {
        await connectDB();

        app.listen(PORT, () => {
            console.log('='.repeat(50));
            console.log('🚀 Checkpoint Monitoring Server');
            console.log('='.repeat(50));
            console.log(`✅ Server running on: http://localhost:${PORT}`);
            console.log(`📊 Database: checkpoint_monitoring`);
            console.log('');
            console.log('📌 API Endpoints:');
            console.log(`   GET  /api/checkpoints        - Get all checkpoints`);
            console.log(`   GET  /api/checkpoints/:id    - Get checkpoint by ID`);
            console.log(`   GET  /api/statistics         - Get statistics`);
            console.log(`   GET  /api/search?q=...       - Search checkpoints`);
            console.log(`   GET  /api/filter?status=...  - Filter by status`);
            console.log('');
            console.log('🌐 Web Interface:');
            console.log(`   GET  /checkpoints            - Main page`);
            console.log(`   GET  /checkpoints/:id        - Checkpoint details`);
            console.log('='.repeat(50));
        });
    } catch (error) {
        console.error('❌ Failed to start server:', error);
        process.exit(1);
    }
}
process.on('SIGINT', async () => {
    console.log('\n🛑 Shutting down server...');
    process.exit(0);
});

process.on('SIGTERM', async () => {
    console.log('\n🛑 Shutting down server...');
    process.exit(0);
});

startServer();