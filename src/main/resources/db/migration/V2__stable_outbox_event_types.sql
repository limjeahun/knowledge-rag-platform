UPDATE outbox_events
SET event_type = 'knowledge-document.indexing-requested.v1'
WHERE event_type = 'DocumentIndexingRequested';

UPDATE outbox_events
SET event_type = 'knowledge-document.deleted.v1'
WHERE event_type = 'DocumentIndexRemovalRequested';
