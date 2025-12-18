const CheckpointModel = require('../models/checkpoint');

class CheckpointController {
    // Get all checkpoints
    static async getAllCheckpoints(req, res) {
        try {
            const checkpoints = await CheckpointModel.getAllLatest();

            res.json({
                success: true,
                count: checkpoints.length,
                data: checkpoints
            });
        } catch (error) {
            console.error('Error in getAllCheckpoints:', error);
            res.status(500).json({
                success: false,
                error: 'فشل في جلب بيانات الحواجز'
            });
        }
    }

    // Get checkpoint by ID
    static async getCheckpointById(req, res) {
        try {
            const { id } = req.params;
            const limit = parseInt(req.query.limit) || 10;

            const checkpoint = await CheckpointModel.getById(id, limit);

            if (!checkpoint) {
                return res.status(404).json({
                    success: false,
                    error: 'الحاجز غير موجود'
                });
            }

            res.json({
                success: true,
                data: checkpoint
            });
        } catch (error) {
            console.error('Error in getCheckpointById:', error);
            res.status(500).json({
                success: false,
                error: 'فشل في جلب بيانات الحاجز'
            });
        }
    }

    // Get statistics
    static async getStatistics(req, res) {
        try {
            const stats = await CheckpointModel.getStatistics();

            res.json({
                success: true,
                data: stats
            });
        } catch (error) {
            console.error('Error in getStatistics:', error);
            res.status(500).json({
                success: false,
                error: 'فشل في جلب الإحصائيات'
            });
        }
    }

    // Search checkpoints
    static async searchCheckpoints(req, res) {
        try {
            const { q } = req.query;

            if (!q || q.trim() === '') {
                return res.status(400).json({
                    success: false,
                    error: 'يرجى إدخال نص للبحث'
                });
            }

            const results = await CheckpointModel.search(q);

            res.json({
                success: true,
                count: results.length,
                data: results
            });
        } catch (error) {
            console.error('Error in searchCheckpoints:', error);
            res.status(500).json({
                success: false,
                error: 'فشل في البحث'
            });
        }
    }

    // Filter checkpoints by status
    static async filterCheckpoints(req, res) {
        try {
            const { status } = req.query;

            if (!status) {
                return res.status(400).json({
                    success: false,
                    error: 'يرجى تحديد الحالة للفلترة'
                });
            }

            const results = await CheckpointModel.filterByStatus(status);

            res.json({
                success: true,
                count: results.length,
                data: results
            });
        } catch (error) {
            console.error('Error in filterCheckpoints:', error);
            res.status(500).json({
                success: false,
                error: 'فشل في الفلترة'
            });
        }
    }
}

module.exports = CheckpointController;