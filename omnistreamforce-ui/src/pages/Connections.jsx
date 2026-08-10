import React from 'react';

const Connections = () => (
  <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 'var(--space-lg)' }}>
    <h1 className="text-hero">Connections Management</h1>
    <p className="text-body">Manage Kafka clusters and Database connections.</p>
    <div className="glass-panel" style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <p className="text-muted">Connection configurations coming soon...</p>
    </div>
  </div>
);

export default Connections;
