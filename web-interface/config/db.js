const { MongoClient } = require('mongodb');

const MONGO_URI = process.env.MONGO_URI || 'mongodb://localhost:27017';
const DB_NAME = 'checkpoint_monitoring';

let db = null;
let client = null;

async function connectDB() {
    try {
        if (db) {
            console.log('✅ Already connected to MongoDB');
            return db;
        }

        client = new MongoClient(MONGO_URI, {
            useUnifiedTopology: true,
        });

        await client.connect();
        console.log('✅ Connected to MongoDB successfully');

        db = client.db(DB_NAME);
        return db;
    } catch (error) {
        console.error('❌ MongoDB connection error:', error);
        throw error;
    }
}

function getDB() {
    if (!db) {
        throw new Error('Database not initialized. Call connectDB first.');
    }
    return db;
}

async function closeDB() {
    if (client) {
        await client.close();
        db = null;
        client = null;
        console.log('🔌 MongoDB connection closed');
    }
}

module.exports = {
    connectDB,
    getDB,
    closeDB
};