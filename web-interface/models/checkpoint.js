const { getDB } = require('../config/db');

const COLLECTION_NAME = 'checkpoint_status';

class CheckpointModel {
    static getCollection() {
        const db = getDB();
        return db.collection(COLLECTION_NAME);
    }

    // Get all checkpoints with latest status for each
    static async getAllLatest() {
        const collection = this.getCollection();

        const checkpoints = await collection
            .aggregate([
                {
                    $sort: { lastUpdated: -1 }
                },
                {
                    $group: {
                        _id: '$checkpointId',
                        latestRecord: { $first: '$$ROOT' }
                    }
                },
                {
                    $replaceRoot: { newRoot: '$latestRecord' }
                },
                {
                    $project: {
                        checkpointId: 1,
                        checkpointName: 1,
                        generalStatus: 1,
                        inboundStatus: 1,
                        outboundStatus: 1,
                        lastUpdated: 1,
                        messageContent: 1,
                        confidence: 1
                    }
                }
            ])
            .toArray();

        return checkpoints;
    }

    // Get checkpoint by ID with history
    static async getById(checkpointId, limit = 10) {
        const collection = this.getCollection();

        const history = await collection
            .find({ checkpointId })
            .sort({ lastUpdated: -1 })
            .limit(limit)
            .toArray();

        if (history.length === 0) {
            return null;
        }

        return {
            current: history[0],
            history: history
        };
    }

    // Get statistics
    static async getStatistics() {
        const collection = this.getCollection();

        const latestCheckpoints = await collection
            .aggregate([
                {
                    $sort: { lastUpdated: -1 }
                },
                {
                    $group: {
                        _id: '$checkpointId',
                        latestRecord: { $first: '$$ROOT' }
                    }
                },
                {
                    $replaceRoot: { newRoot: '$latestRecord' }
                }
            ])
            .toArray();

        const stats = {
            total: latestCheckpoints.length,
            open: 0,
            closed: 0,
            busy: 0,
            mixed: 0,
            partial: 0,
            byStatus: {},
            byDirection: {
                inbound: { open: 0, closed: 0, busy: 0, unknown: 0 },
                outbound: { open: 0, closed: 0, busy: 0, unknown: 0 }
            }
        };

        latestCheckpoints.forEach(checkpoint => {
            const general = checkpoint.generalStatus?.toLowerCase() || 'unknown';

            // إحصائيات عامة
            if (general.includes('open')) {
                stats.open++;
            } else if (general.includes('closed')) {
                stats.closed++;
            } else if (general.includes('busy')) {
                stats.busy++;
            } else if (general === 'mixed') {
                stats.mixed++;
            } else if (general === 'partial') {
                stats.partial++;
            }

            stats.byStatus[checkpoint.generalStatus] = (stats.byStatus[checkpoint.generalStatus] || 0) + 1;

            // إحصائيات الاتجاهات
            if (checkpoint.inboundStatus) {
                const inStatus = checkpoint.inboundStatus.status?.toLowerCase() || 'unknown';
                if (stats.byDirection.inbound[inStatus] !== undefined) {
                    stats.byDirection.inbound[inStatus]++;
                }
            }

            if (checkpoint.outboundStatus) {
                const outStatus = checkpoint.outboundStatus.status?.toLowerCase() || 'unknown';
                if (stats.byDirection.outbound[outStatus] !== undefined) {
                    stats.byDirection.outbound[outStatus]++;
                }
            }
        });

        return stats;
    }

    // Search checkpoints by name or ID
    static async search(query) {
        const collection = this.getCollection();

        const results = await collection
            .aggregate([
                {
                    $sort: { lastUpdated: -1 }
                },
                {
                    $group: {
                        _id: '$checkpointId',
                        latestRecord: { $first: '$$ROOT' }
                    }
                },
                {
                    $replaceRoot: { newRoot: '$latestRecord' }
                },
                {
                    $match: {
                        $or: [
                            { checkpointName: { $regex: query, $options: 'i' } },
                            { checkpointId: { $regex: query, $options: 'i' } }
                        ]
                    }
                }
            ])
            .toArray();

        return results;
    }

    // Filter checkpoints by status (supports directional filtering)
    static async filterByStatus(statusFilter) {
        const collection = this.getCollection();

        const filterLower = statusFilter.toLowerCase();

        const results = await collection
            .aggregate([
                {
                    $sort: { lastUpdated: -1 }
                },
                {
                    $group: {
                        _id: '$checkpointId',
                        latestRecord: { $first: '$$ROOT' }
                    }
                },
                {
                    $replaceRoot: { newRoot: '$latestRecord' }
                },
                {
                    $match: {
                        $or: [
                            { generalStatus: { $regex: statusFilter, $options: 'i' } },
                            { 'inboundStatus.status': { $regex: statusFilter, $options: 'i' } },
                            { 'outboundStatus.status': { $regex: statusFilter, $options: 'i' } }
                        ]
                    }
                }
            ])
            .toArray();

        return results;
    }
}

module.exports = CheckpointModel;