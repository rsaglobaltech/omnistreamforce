import React, { useState } from 'react';
import { Outlet } from 'react-router';
import Sidebar from './Sidebar';
import TopHeader from './TopHeader';

const Layout = () => {
  const [isSidebarExpanded, setSidebarExpanded] = useState(true);

  return (
    <div style={{ display: 'flex', height: '100vh', width: '100vw', overflow: 'hidden' }}>
      <Sidebar isExpanded={isSidebarExpanded} />
      <div style={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
        <TopHeader onToggleSidebar={() => setSidebarExpanded(!isSidebarExpanded)} />
        <main style={{ flex: 1, overflowY: 'auto', padding: 'var(--space-lg)', position: 'relative' }}>
          {/* Ambient glow in the background */}
          <div style={{
            position: 'absolute',
            top: 0,
            left: '50%',
            transform: 'translateX(-50%)',
            width: '80%',
            height: '500px',
            background: 'var(--gradient-glow)',
            pointerEvents: 'none',
            zIndex: 0
          }}></div>
          
          <div style={{ position: 'relative', zIndex: 1, height: '100%' }}>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};

export default Layout;
