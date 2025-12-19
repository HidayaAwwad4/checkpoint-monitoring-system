const express = require('express');
const CheckpointController = require('../controllers/checkpointController');
const router = express.Router();
router.get('/checkpoints', CheckpointController.getAllCheckpoints);
router.get('/checkpoints/:id', CheckpointController.getCheckpointById);
router.get('/statistics', CheckpointController.getStatistics);
router.get('/search', CheckpointController.searchCheckpoints);
router.get('/filter', CheckpointController.filterCheckpoints);
module.exports = router;