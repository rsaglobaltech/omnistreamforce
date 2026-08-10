import React from 'react';

const AIStudio = () => (
  <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 'var(--space-lg)' }}>
    <h1 className="text-hero">AI Schema Studio</h1>
    <p className="text-body">Interact with the OmniStreamForce AI to generate custom schemas.</p>
    <div className="glass-panel" style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <p className="text-muted">Chat interface coming soon...</p>
    </div>
  </div>
);

export default AIStudio;
