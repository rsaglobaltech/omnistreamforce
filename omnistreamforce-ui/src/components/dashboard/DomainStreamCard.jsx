import React from 'react';
import { Activity, AlertTriangle, Settings2, PauseCircle } from 'lucide-react';

const DomainStreamCard = ({ domain, eps, errorRate, status, color }) => {
  return (
    <div className="glass-panel" style={{ borderLeft: `4px solid ${color}`, display: 'flex', flexDirection: 'column' }}>
      <div style={{ padding: 'var(--space-md) var(--space-lg)', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h4 className="text-h3">{domain}</h4>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
            <PauseCircle size={16} />
          </button>
          <button style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
            <Settings2 size={16} />
          </button>
        </div>
      </div>
      
      <div style={{ padding: 'var(--space-md) var(--space-lg)', display: 'flex', justifyContent: 'space-between' }}>
        <div>
          <div className="text-body" style={{ fontSize: '11px', textTransform: 'uppercase', marginBottom: '4px' }}>EPS (Target)</div>
          <div className="text-metric" style={{ fontSize: '20px' }}>{eps.toLocaleString()}</div>
        </div>
        
        <div>
          <div className="text-body" style={{ fontSize: '11px', textTransform: 'uppercase', marginBottom: '4px' }}>Error Rate</div>
          <div className="text-metric" style={{ fontSize: '20px', color: errorRate > 0 ? 'var(--accent-amber)' : 'inherit' }}>
            {errorRate}%
          </div>
        </div>

        <div>
          <div className="text-body" style={{ fontSize: '11px', textTransform: 'uppercase', marginBottom: '4px' }}>Status</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', height: '24px' }}>
            <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: status === 'STEADY' ? 'var(--accent-emerald)' : 'var(--accent-blue)' }}></div>
            <span className="text-mono" style={{ fontSize: '12px' }}>{status}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DomainStreamCard;
