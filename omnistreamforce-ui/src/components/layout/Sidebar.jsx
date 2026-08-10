import React from 'react';
import { NavLink } from 'react-router';
import { LayoutDashboard, Cable, Network, BrainCircuit, Activity, Settings, Zap } from 'lucide-react';

const Sidebar = ({ isExpanded }) => {
  const navItems = [
    { name: 'Dashboard', path: '/', icon: <LayoutDashboard size={20} /> },
    { name: 'Streams', path: '/streams', icon: <Zap size={20} /> },
    { name: 'Connections', path: '/connections', icon: <Cable size={20} /> },
    { name: 'AI Studio', path: '/ai-studio', icon: <BrainCircuit size={20} /> },
    { name: 'Outbox & Relay', path: '/outbox', icon: <Network size={20} /> },
    { name: 'Metrics', path: '/metrics', icon: <Activity size={20} /> },
    { name: 'Settings', path: '/settings', icon: <Settings size={20} /> },
  ];

  return (
    <div style={{
      width: isExpanded ? '240px' : '70px',
      background: 'var(--surface-1)',
      borderRight: '1px solid var(--border)',
      display: 'flex',
      flexDirection: 'column',
      padding: 'var(--space-md) 0',
      zIndex: 10,
      transition: 'width 0.2s ease'
    }}>
      <div style={{ padding: '0 var(--space-lg)', marginBottom: 'var(--space-xl)', overflow: 'hidden', whiteSpace: 'nowrap' }}>
        <h2 className="text-h3" style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--accent-cyan)' }}>
          <Zap size={24} style={{ flexShrink: 0 }} />
          <span style={{ opacity: isExpanded ? 1 : 0, transition: 'opacity 0.2s' }}>OmniStreamForce</span>
        </h2>
      </div>

      <nav style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {navItems.map(item => (
          <NavLink
            key={item.path}
            to={item.path}
            title={!isExpanded ? item.name : undefined}
            style={({ isActive }) => ({
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              padding: '10px var(--space-lg)',
              color: isActive ? 'var(--text-primary)' : 'var(--text-secondary)',
              background: isActive ? 'var(--surface-3)' : 'transparent',
              borderLeft: `3px solid ${isActive ? 'var(--accent-cyan)' : 'transparent'}`,
              textDecoration: 'none',
              fontWeight: 500,
              transition: 'all 0.2s',
              overflow: 'hidden',
              whiteSpace: 'nowrap'
            })}
          >
            <span style={{ color: 'inherit', flexShrink: 0 }}>{item.icon}</span>
            <span style={{ opacity: isExpanded ? 1 : 0, transition: 'opacity 0.2s' }}>{item.name}</span>
          </NavLink>
        ))}
      </nav>

      <div style={{ padding: 'var(--space-md) var(--space-lg)', borderTop: '1px solid var(--border)', overflow: 'hidden', whiteSpace: 'nowrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
          <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-emerald)', flexShrink: 0 }}></div>
          <span className="text-body" style={{ fontSize: '12px', opacity: isExpanded ? 1 : 0, transition: 'opacity 0.2s' }}>Kafka: Local</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-cyan)', flexShrink: 0 }}></div>
          <span className="text-body" style={{ fontSize: '12px', opacity: isExpanded ? 1 : 0, transition: 'opacity 0.2s' }}>Engine: 3 Domains</span>
        </div>
      </div>
    </div>
  );
};

export default Sidebar;
