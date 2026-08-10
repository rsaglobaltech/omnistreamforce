import React from 'react';

const HeroMetricsBar = ({ data, isConnected }) => {
  // Fake data for visual if no connection
  const totalEvents = data ? data.totalEvents : 14205842;
  const currentEps = data ? data.eps : 10170;
  const errorCount = data ? data.totalErrors : 1240;

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 'var(--space-md)' }}>
      <MetricCard title="Total Events Generated" value={totalEvents.toLocaleString()} highlight="var(--accent-cyan)" />
      <MetricCard title="Current Global EPS" value={currentEps.toLocaleString()} highlight="var(--accent-emerald)" />
      <MetricCard title="Total Errors Injected" value={errorCount.toLocaleString()} highlight="var(--accent-rose)" />
      
      <div className="glass-panel" style={{ padding: 'var(--space-md) var(--space-lg)', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
        <span className="text-body" style={{ textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '8px' }}>Engine Status</span>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ position: 'relative' }}>
            {/* Pulse effect */}
            <div style={{ 
              position: 'absolute', 
              top: '-4px', left: '-4px', right: '-4px', bottom: '-4px', 
              borderRadius: '50%', 
              background: isConnected ? 'var(--accent-emerald)' : 'var(--accent-amber)',
              opacity: 0.2,
              animation: 'pulse 2s infinite'
            }}></div>
            <div style={{ 
              width: '12px', height: '12px', 
              borderRadius: '50%', 
              background: isConnected ? 'var(--accent-emerald)' : 'var(--accent-amber)',
              position: 'relative', zIndex: 1
            }}></div>
          </div>
          <span className="text-h2" style={{ color: isConnected ? 'var(--accent-emerald)' : 'var(--accent-amber)' }}>
            {isConnected ? 'ONLINE' : 'CONNECTING...'}
          </span>
        </div>
      </div>
    </div>
  );
};

const MetricCard = ({ title, value, highlight }) => (
  <div className="glass-panel" style={{ padding: 'var(--space-md) var(--space-lg)', borderLeft: `3px solid ${highlight}` }}>
    <div className="text-body" style={{ textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '8px' }}>{title}</div>
    <div className="text-metric" style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
      {value}
    </div>
  </div>
);

export default HeroMetricsBar;
