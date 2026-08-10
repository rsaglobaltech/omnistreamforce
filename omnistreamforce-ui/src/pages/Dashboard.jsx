import React from 'react';
import HeroMetricsBar from '../components/dashboard/HeroMetricsBar';
import RealtimeChart from '../components/dashboard/RealtimeChart';
import DomainStreamCard from '../components/dashboard/DomainStreamCard';
import EventFeed from '../components/dashboard/EventFeed';
import { useWebSocket } from '../hooks/useWebSocket';
import { Play, Square } from 'lucide-react';

const Dashboard = () => {
  // Use relative WebSocket URL so it works via Vite proxy or when served from Javalin directly
  const wsUrl = window.location.protocol === 'https:' 
    ? `wss://${window.location.host}/ws/dashboard` 
    : `ws://${window.location.host}/ws/dashboard`;
  
  const { data, isConnected } = useWebSocket(wsUrl);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-lg)', height: '100%' }}>
      
      {/* HEADER SECTION */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
        <div>
          <h1 className="text-hero" style={{ marginBottom: 'var(--space-sm)' }}>Command Center</h1>
          <p className="text-body">Live monitoring of the event generation engine.</p>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
          <button className="btn btn-secondary" style={{ borderColor: 'var(--accent-rose)', color: 'var(--accent-rose)' }}>
            <Square size={16} /> Stop Engine
          </button>
          <button className="btn btn-primary">
            <Play size={16} fill="currentColor" /> Resume All
          </button>
        </div>
      </div>

      {/* METRICS ROW */}
      <HeroMetricsBar data={data} isConnected={isConnected} />

      {/* MAIN GRID */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 'var(--space-lg)', flex: 1, minHeight: 0 }}>
        
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-lg)', overflowY: 'auto', paddingRight: 'var(--space-sm)' }}>
          {/* CHARTS */}
          <div className="glass-panel" style={{ padding: 'var(--space-lg)', height: '300px' }}>
            <h3 className="text-h3" style={{ marginBottom: 'var(--space-md)' }}>Global Throughput (EPS)</h3>
            <RealtimeChart data={data} metric="eps" />
          </div>

          {/* DOMAINS */}
          <h3 className="text-h3">Active Domains</h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 'var(--space-md)' }}>
            <DomainStreamCard domain="Healthcare" eps={1240} errorRate={5} status="STEADY" color="var(--accent-emerald)" />
            <DomainStreamCard domain="Ecommerce" eps={8500} errorRate={12} status="BURST" color="var(--accent-blue)" />
            <DomainStreamCard domain="FastFood" eps={430} errorRate={0} status="STEADY" color="var(--accent-orange)" />
          </div>
        </div>

        {/* EVENT FEED */}
        <div className="glass-panel" style={{ padding: '0', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          <div style={{ padding: 'var(--space-lg) var(--space-lg) var(--space-md)', borderBottom: '1px solid var(--border)' }}>
            <h3 className="text-h3">Live Event Feed</h3>
          </div>
          <EventFeed />
        </div>
        
      </div>
    </div>
  );
};

export default Dashboard;
