import React, { useState } from 'react';
import { Database, Network, Server, ArrowRight } from 'lucide-react';

const Outbox = () => {
  const [breakerStatus, setBreakerStatus] = useState('CLOSED');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 'var(--space-lg)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
        <div>
          <h1 className="text-hero" style={{ marginBottom: 'var(--space-sm)' }}>Outbox & Relay Pipeline</h1>
          <p className="text-body">Visual topology of the persistence layer and Kafka relay.</p>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-md)', alignItems: 'center' }}>
          <span className="text-body" style={{ textTransform: 'uppercase', fontSize: '12px' }}>Circuit Breaker:</span>
          <button 
            className="btn" 
            onClick={() => setBreakerStatus(s => s === 'CLOSED' ? 'OPEN' : 'CLOSED')}
            style={{ 
              background: breakerStatus === 'CLOSED' ? 'var(--accent-emerald)' : 'var(--accent-rose)', 
              color: 'white',
              borderRadius: 'var(--radius-full)'
            }}>
            {breakerStatus}
          </button>
        </div>
      </div>

      {/* TOPOLOGY VIEW */}
      <div className="glass-panel" style={{ height: '300px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 'var(--space-2xl)', padding: 'var(--space-xl)' }}>
        
        {/* Engine Node */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }}>
          <div style={{ width: '64px', height: '64px', borderRadius: '50%', background: 'var(--surface-3)', border: '2px solid var(--accent-cyan)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Server size={32} color="var(--accent-cyan)" />
          </div>
          <span className="text-h3">OSF Engine</span>
        </div>

        {/* Animated Arrow 1 */}
        <div style={{ flex: 1, height: '4px', background: 'var(--surface-3)', position: 'relative', overflow: 'hidden' }}>
          <div style={{ 
            position: 'absolute', top: 0, left: 0, bottom: 0, width: '30%', 
            background: 'var(--accent-cyan)', 
            animation: 'slideRight 1s linear infinite'
          }}></div>
        </div>

        {/* PostgreSQL Node */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }}>
          <div style={{ width: '64px', height: '64px', borderRadius: '50%', background: 'var(--surface-3)', border: '2px solid var(--accent-orange)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Database size={32} color="var(--accent-orange)" />
          </div>
          <span className="text-h3">PostgreSQL</span>
          <span className="text-mono" style={{ fontSize: '11px', color: 'var(--accent-orange)' }}>+ Outbox Table</span>
        </div>

        {/* Animated Arrow 2 (Relay) */}
        <div style={{ flex: 1, height: '4px', background: 'var(--surface-3)', position: 'relative', overflow: 'hidden' }}>
          {breakerStatus === 'CLOSED' && (
            <div style={{ 
              position: 'absolute', top: 0, left: 0, bottom: 0, width: '30%', 
              background: 'var(--accent-orange)', 
              animation: 'slideRight 1.5s linear infinite'
            }}></div>
          )}
          {breakerStatus === 'OPEN' && (
             <div style={{ position: 'absolute', top: 0, left: '50%', transform: 'translateX(-50%)', color: 'var(--accent-rose)', fontSize: '18px', fontWeight: 'bold' }}>X</div>
          )}
        </div>

        {/* Kafka Node */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }}>
          <div style={{ width: '64px', height: '64px', borderRadius: '50%', background: 'var(--surface-3)', border: '2px solid var(--accent-emerald)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Network size={32} color="var(--accent-emerald)" />
          </div>
          <span className="text-h3">Kafka Cluster</span>
        </div>

      </div>

      {/* METRICS */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-lg)' }}>
        <div className="glass-panel" style={{ padding: 'var(--space-lg)', borderLeft: '4px solid var(--accent-orange)' }}>
          <h3 className="text-h3" style={{ marginBottom: 'var(--space-md)' }}>Persistence Metrics</h3>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <div>
              <div className="text-body" style={{ fontSize: '11px', textTransform: 'uppercase' }}>DB Inserts/Sec</div>
              <div className="text-metric">1,204</div>
            </div>
            <div>
              <div className="text-body" style={{ fontSize: '11px', textTransform: 'uppercase' }}>Pending Outbox</div>
              <div className="text-metric">42</div>
            </div>
            <div>
              <div className="text-body" style={{ fontSize: '11px', textTransform: 'uppercase' }}>Avg Commit Time</div>
              <div className="text-metric">8.4ms</div>
            </div>
          </div>
        </div>

        <div className="glass-panel" style={{ padding: 'var(--space-lg)', borderLeft: '4px solid var(--accent-emerald)' }}>
          <h3 className="text-h3" style={{ marginBottom: 'var(--space-md)' }}>Relay Stats</h3>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <div>
              <div className="text-body" style={{ fontSize: '11px', textTransform: 'uppercase' }}>Lag (End-to-End)</div>
              <div className="text-metric" style={{ color: 'var(--accent-emerald)' }}>0.8s</div>
            </div>
            <div>
              <div className="text-body" style={{ fontSize: '11px', textTransform: 'uppercase' }}>Events Relayed</div>
              <div className="text-metric">4,592,110</div>
            </div>
          </div>
        </div>
      </div>
      
      {/* CSS Animation injected */}
      <style>{`
        @keyframes slideRight {
          0% { left: -30%; }
          100% { left: 100%; }
        }
      `}</style>
    </div>
  );
};

export default Outbox;
