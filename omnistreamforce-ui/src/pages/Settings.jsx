import React from 'react';

const Settings = () => (
  <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 'var(--space-lg)' }}>
    <h1 className="text-hero">Settings & Profiles</h1>
    <p className="text-body">Manage global settings and YAML generator profiles.</p>
    <div className="glass-panel" style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <p className="text-muted">YAML Editor coming soon...</p>
    </div>
  </div>
);

export default Settings;
