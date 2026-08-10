import React from 'react';
import { Search, Bell, HelpCircle, User, Menu } from 'lucide-react';

const TopHeader = ({ onToggleSidebar }) => {
  return (
    <header style={{
      height: '56px',
      background: 'rgba(18, 18, 26, 0.8)',
      backdropFilter: 'blur(12px)',
      borderBottom: '1px solid var(--border)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 var(--space-lg)',
      zIndex: 10
    }}>
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 'var(--space-md)' }}>
        <button 
          onClick={onToggleSidebar}
          style={{ background: 'none', border: 'none', color: 'var(--text-primary)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
        >
          <Menu size={24} />
        </button>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          background: 'var(--surface-0)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-sm)',
          padding: '6px 12px',
          width: '300px',
          color: 'var(--text-secondary)'
        }}>
          <Search size={16} style={{ marginRight: '8px' }} />
          <span style={{ fontSize: '14px', flex: 1 }}>Search or jump to...</span>
          <span style={{ 
            fontSize: '12px', 
            background: 'var(--surface-3)', 
            padding: '2px 6px', 
            borderRadius: '4px',
            fontFamily: 'var(--font-mono)'
          }}>Ctrl K</span>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-md)' }}>
        <button style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
          <Bell size={20} />
        </button>
        <button style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
          <HelpCircle size={20} />
        </button>
        <div style={{
          width: '32px',
          height: '32px',
          borderRadius: '50%',
          background: 'var(--surface-3)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          border: '1px solid var(--border)'
        }}>
          <User size={18} color="var(--text-primary)" />
        </div>
      </div>
    </header>
  );
};

export default TopHeader;
