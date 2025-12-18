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
                        status: 1,
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
            byStatus: {}
        };

        latestCheckpoints.forEach(checkpoint => {
            const status = checkpoint.status.toLowerCase();

            if (status.includes('open')) {
                stats.open++;
            } else if (status.includes('closed')) {
                stats.closed++;
            } else if (status.includes('busy')) {
                stats.busy++;
            }

            stats.byStatus[checkpoint.status] = (stats.byStatus[checkpoint.status] || 0) + 1;
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

    // Filter checkpoints by status
    static async filterByStatus(statusFilter) {
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
                        status: { $regex: statusFilter, $options: 'i' }
                    }
                }
            ])
            .toArray();

        return results;
    }
}

module.exports = CheckpointModel;