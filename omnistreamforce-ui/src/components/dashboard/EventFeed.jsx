import React, { useState, useEffect, useRef } from 'react';

const EventFeed = () => {
  const [events, setEvents] = useState([]);
  const feedRef = useRef(null);

  useEffect(() => {
    // Simulate incoming events
    const domains = ['Healthcare', 'Ecommerce', 'FastFood'];
    const types = ['SUCCESS', 'SUCCESS', 'SUCCESS', 'WARNING', 'ERROR'];
    
    const interval = setInterval(() => {
      setEvents(prev => {
        const newEvent = {
          id: Math.random().toString(36).substr(2, 9),
          timestamp: new Date().toISOString().split('T')[1].substr(0, 12),
          domain: domains[Math.floor(Math.random() * domains.length)],
          type: types[Math.floor(Math.random() * types.length)],
          size: Math.floor(Math.random() * 2000) + 100
        };
        const next = [newEvent, ...prev].slice(0, 50); // Keep last 50
        return next;
      });
    }, 400);

    return () => clearInterval(interval);
  }, []);

  const getDomainColor = (domain) => {
    switch(domain) {
      case 'Healthcare': return 'var(--accent-emerald)';
      case 'Ecommerce': return 'var(--accent-blue)';
      case 'FastFood': return 'var(--accent-orange)';
      default: return 'var(--text-secondary)';
    }
  };

  const getTypeColor = (type) => {
    switch(type) {
      case 'ERROR': return 'var(--accent-rose)';
      case 'WARNING': return 'var(--accent-amber)';
      default: return 'var(--text-muted)';
    }
  };

  return (
    <div ref={feedRef} style={{ flex: 1, overflowY: 'auto', padding: '0 var(--space-md)' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
        <tbody>
          {events.map((evt) => (
            <tr key={evt.id} style={{ borderBottom: '1px solid var(--border)', fontFamily: 'var(--font-mono)', fontSize: '12px' }}>
              <td style={{ padding: '8px 4px', color: 'var(--text-muted)' }}>{evt.timestamp}</td>
              <td style={{ padding: '8px 4px' }}>
                <span style={{ color: getDomainColor(evt.domain) }}>{evt.domain}</span>
              </td>
              <td style={{ padding: '8px 4px', color: getTypeColor(evt.type) }}>
                {evt.type === 'SUCCESS' ? 'PUBLISHED' : evt.type}
              </td>
              <td style={{ padding: '8px 4px', color: 'var(--text-secondary)', textAlign: 'right' }}>
                {evt.size}b
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default EventFeed;
