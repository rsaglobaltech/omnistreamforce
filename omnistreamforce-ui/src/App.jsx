import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router';
import Layout from './components/layout/Layout';

// Pages
import Dashboard from './pages/Dashboard';
import Streams from './pages/Streams';
import Connections from './pages/Connections';
import AIStudio from './pages/AIStudio';
import Outbox from './pages/Outbox';
import Settings from './pages/Settings';

const Metrics = () => (
  <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 'var(--space-lg)' }}>
    <h1 className="text-hero">Metrics</h1>
    <p className="text-body">Detailed historical metrics and performance analysis.</p>
  </div>
);

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Dashboard />} />
          <Route path="streams" element={<Streams />} />
          <Route path="connections" element={<Connections />} />
          <Route path="ai-studio" element={<AIStudio />} />
          <Route path="outbox" element={<Outbox />} />
          <Route path="metrics" element={<Metrics />} />
          <Route path="settings" element={<Settings />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
