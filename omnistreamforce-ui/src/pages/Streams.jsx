import React, { useState, useEffect } from 'react';
import { Database, Zap, Plus, Settings2, Trash2 } from 'lucide-react';

const Streams = () => {
  const [activeTab, setActiveTab] = useState('wizard');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 'var(--space-lg)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
        <div>
          <h1 className="text-hero" style={{ marginBottom: 'var(--space-sm)' }}>Stream Constructor</h1>
          <p className="text-body">Configure and deploy new event streams to Kafka or Database.</p>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-sm)' }}>
          <button 
            className={`btn ${activeTab === 'wizard' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setActiveTab('wizard')}
            style={{ borderRadius: 'var(--radius-full)' }}
          >
            Setup Wizard
          </button>
          <button 
            className={`btn ${activeTab === 'manage' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setActiveTab('manage')}
            style={{ borderRadius: 'var(--radius-full)' }}
          >
            Manage Active
          </button>
        </div>
      </div>

      {activeTab === 'wizard' && <WizardView />}
      {activeTab === 'manage' && <ManageView />}
    </div>
  );
};

const WizardView = () => {
  const [domains, setDomains] = useState([]);
  const [selectedDomain, setSelectedDomain] = useState('Healthcare');
  const [eps, setEps] = useState(100);
  const [errorRate, setErrorRate] = useState(5);
  const [targetTopic, setTargetTopic] = useState('');
  const [errorTopic, setErrorTopic] = useState('');
  const [format, setFormat] = useState('JSON');
  const [mode, setMode] = useState('STEADY');
  const [keyStrategy, setKeyStrategy] = useState('entityId');
  
  const [deploying, setDeploying] = useState(false);
  const [message, setMessage] = useState('');
  
  useEffect(() => {
    fetch('/api/domains')
      .then(res => res.json())
      .then(data => {
        setDomains(data);
        if (data.length > 0) {
          setSelectedDomain(data[0]);
          setTargetTopic(`${data[0].toLowerCase()}-events`);
          setErrorTopic(`${data[0].toLowerCase()}-errors`);
        }
      })
      .catch(err => console.error("Failed to fetch domains:", err));
  }, []);

  useEffect(() => {
    setTargetTopic(`${selectedDomain.toLowerCase()}-events`);
    setErrorTopic(`${selectedDomain.toLowerCase()}-errors`);
  }, [selectedDomain]);

  const handleDeploy = () => {
    setDeploying(true);
    setMessage('');
    fetch(`/api/streams?domain=${selectedDomain}&eps=${eps}&errorRate=${errorRate}&targetTopic=${encodeURIComponent(targetTopic)}&errorTopic=${encodeURIComponent(errorTopic)}&format=${format}&mode=${mode}&keyStrategy=${keyStrategy}`, { method: 'POST' })
      .then(res => res.json())
      .then(data => {
        setMessage(data.message || 'Stream started successfully!');
        setDeploying(false);
      })
      .catch(err => {
        setMessage('Error starting stream.');
        setDeploying(false);
      });
  };

  return (
  <div className="glass-panel" style={{ flex: 1, padding: 'var(--space-xl)', display: 'flex', flexDirection: 'column', gap: 'var(--space-xl)' }}>
    
    {/* STEP INDICATOR */}
    <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--border)', paddingBottom: 'var(--space-lg)' }}>
      {['Connection', 'Domain', 'Schema', 'Deploy'].map((step, idx) => (
        <div key={step} style={{ display: 'flex', alignItems: 'center', gap: '12px', opacity: idx === 1 ? 1 : 0.4 }}>
          <div style={{ width: '32px', height: '32px', borderRadius: '50%', background: idx === 1 ? 'var(--accent-cyan)' : 'var(--surface-3)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: idx === 1 ? 'var(--surface-0)' : 'inherit', fontWeight: 'bold' }}>
            {idx + 1}
          </div>
          <span className="text-h3">{step}</span>
        </div>
      ))}
    </div>

    {/* DOMAIN CONFIG */}
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-xl)' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }}>
        <h3 className="text-h2">Select Domain</h3>
        <p className="text-body">Choose a pre-configured domain or create a custom one with AI.</p>
        
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-md)' }}>
          {domains.map((dom) => (
            <div key={dom} onClick={() => setSelectedDomain(dom)}>
              <DomainOption title={dom} icon={<Database size={20} />} color="var(--accent-blue)" active={selectedDomain === dom} />
            </div>
          ))}
          <DomainOption title="Custom AI" icon={<Plus size={20} />} color="var(--accent-violet)" />
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }}>
        <h3 className="text-h2">Stream Configuration</h3>
        
        <div className="glass-panel" style={{ padding: 'var(--space-lg)', display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-md)' }}>
            <div>
              <label className="text-body" style={{ textTransform: 'uppercase', fontSize: '11px', display: 'block', marginBottom: '8px' }}>Target Topic (Kafka)</label>
              <input type="text" value={targetTopic} onChange={e => setTargetTopic(e.target.value)} style={{ width: '100%', background: 'var(--surface-2)', border: '1px solid var(--border)', padding: '10px 12px', color: 'var(--text-primary)', borderRadius: 'var(--radius-sm)', outline: 'none' }} />
            </div>
            <div>
              <label className="text-body" style={{ textTransform: 'uppercase', fontSize: '11px', display: 'block', marginBottom: '8px' }}>Error Topic (Kafka)</label>
              <input type="text" value={errorTopic} onChange={e => setErrorTopic(e.target.value)} style={{ width: '100%', background: 'var(--surface-2)', border: '1px solid var(--border)', padding: '10px 12px', color: 'var(--text-primary)', borderRadius: 'var(--radius-sm)', outline: 'none' }} />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-md)' }}>
            <div>
              <label className="text-body" style={{ textTransform: 'uppercase', fontSize: '11px', display: 'block', marginBottom: '8px' }}>Events Per Second (EPS)</label>
              <div style={{ display: 'flex', gap: 'var(--space-md)', alignItems: 'center' }}>
                <input type="range" min="1" max="10000" value={eps} onChange={e => setEps(Number(e.target.value))} style={{ flex: 1, accentColor: 'var(--accent-cyan)' }} />
                <span className="text-mono" style={{ width: '50px', textAlign: 'right' }}>{eps.toLocaleString()}</span>
              </div>
            </div>
            <div>
              <label className="text-body" style={{ textTransform: 'uppercase', fontSize: '11px', display: 'block', marginBottom: '8px' }}>Error Rate (%)</label>
              <div style={{ display: 'flex', gap: 'var(--space-md)', alignItems: 'center' }}>
                <input type="range" min="0" max="100" value={errorRate} onChange={e => setErrorRate(Number(e.target.value))} style={{ flex: 1, accentColor: 'var(--accent-rose)' }} />
                <span className="text-mono" style={{ width: '40px', textAlign: 'right', color: 'var(--accent-rose)' }}>{errorRate}%</span>
              </div>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-md)' }}>
            <div>
              <label className="text-body" style={{ textTransform: 'uppercase', fontSize: '11px', display: 'block', marginBottom: '8px' }}>Traffic Pattern</label>
              <select value={mode} onChange={e => setMode(e.target.value)} style={{ width: '100%', background: 'var(--surface-2)', border: '1px solid var(--border)', padding: '10px 12px', color: 'var(--text-primary)', borderRadius: 'var(--radius-sm)', outline: 'none' }}>
                <option value="STEADY">STEADY</option>
                <option value="BURST">BURST</option>
                <option value="SPIKE">SPIKE</option>
                <option value="RAMP">RAMP</option>
              </select>
            </div>
            <div>
              <label className="text-body" style={{ textTransform: 'uppercase', fontSize: '11px', display: 'block', marginBottom: '8px' }}>Format</label>
              <select value={format} onChange={e => setFormat(e.target.value)} style={{ width: '100%', background: 'var(--surface-2)', border: '1px solid var(--border)', padding: '10px 12px', color: 'var(--text-primary)', borderRadius: 'var(--radius-sm)', outline: 'none' }}>
                <option value="JSON">JSON</option>
                <option value="Avro">Avro</option>
                <option value="Protobuf">Protobuf</option>
              </select>
            </div>
            <div>
              <label className="text-body" style={{ textTransform: 'uppercase', fontSize: '11px', display: 'block', marginBottom: '8px' }}>Key Strategy</label>
              <select value={keyStrategy} onChange={e => setKeyStrategy(e.target.value)} style={{ width: '100%', background: 'var(--surface-2)', border: '1px solid var(--border)', padding: '10px 12px', color: 'var(--text-primary)', borderRadius: 'var(--radius-sm)', outline: 'none' }}>
                <option value="entityId">entityId</option>
                <option value="random">random</option>
                <option value="roundRobin">roundRobin</option>
              </select>
            </div>
          </div>
          
          <div style={{ marginTop: 'var(--space-md)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span className="text-body" style={{ color: 'var(--accent-emerald)' }}>{message}</span>
            <button className="btn btn-primary" onClick={handleDeploy} disabled={deploying}>
              {deploying ? 'Deploying...' : 'Deploy to Engine'} <Zap size={16}/>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
  );
};

const ManageView = () => {
  const [activeStreams, setActiveStreams] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedStream, setSelectedStream] = useState(null);

  const fetchActive = () => {
    fetch('/api/streams/active')
      .then(res => res.json())
      .then(data => {
        setActiveStreams(data);
        setLoading(false);
        if (selectedStream && !data.includes(selectedStream)) {
          setSelectedStream(null); // Stream died
        }
      })
      .catch(err => {
        console.error(err);
        setLoading(false);
      });
  };

  useEffect(() => {
    fetchActive();
    const interval = setInterval(fetchActive, 5000);
    return () => clearInterval(interval);
  }, [selectedStream]);

  if (selectedStream) {
    return <StreamDetailView domain={selectedStream} onBack={() => setSelectedStream(null)} onStopped={() => { setSelectedStream(null); fetchActive(); }} />;
  }

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 'var(--space-lg)' }}>
      {loading ? (
        <div style={{ color: 'var(--text-secondary)' }}>Loading active streams...</div>
      ) : activeStreams.length === 0 ? (
        <div className="glass-panel" style={{ padding: 'var(--space-xl)', textAlign: 'center', color: 'var(--text-secondary)' }}>
          No active streams found. Use the Setup Wizard to create one.
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 'var(--space-md)' }}>
          {activeStreams.map(domain => (
            <div key={domain} className="glass-panel" style={{ padding: 'var(--space-lg)', display: 'flex', flexDirection: 'column', gap: 'var(--space-md)', cursor: 'pointer', transition: 'all 0.2s' }} onClick={() => setSelectedStream(domain)}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <div style={{ color: 'var(--accent-emerald)' }}>
                  <Zap size={24} />
                </div>
                <div>
                  <h3 className="text-h3">{domain}</h3>
                  <span className="text-body" style={{ fontSize: '12px', color: 'var(--accent-emerald)' }}>RUNNING</span>
                </div>
              </div>
              <div style={{ marginTop: 'var(--space-sm)' }}>
                <span className="text-body" style={{ fontSize: '13px' }}>Click to view live data and controls &rarr;</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

const StreamDetailView = ({ domain, onBack, onStopped }) => {
  const [logs, setLogs] = useState([]);
  const [paused, setPaused] = useState(false);
  const [eps, setEps] = useState(100); // Default, we don't have get-eps yet

  useEffect(() => {
    const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProto}//${window.location.host}/ws/streams/${domain}`;
    const socket = new WebSocket(wsUrl);

    socket.onmessage = (event) => {
      setLogs(prev => {
        const newLogs = [...prev, event.data];
        return newLogs.slice(-20); // Keep last 20
      });
    };

    return () => socket.close();
  }, [domain]);

  const handleStop = (e) => {
    e.stopPropagation();
    fetch(`/api/streams?domain=${domain}`, { method: 'DELETE' })
      .then(() => onStopped())
      .catch(err => console.error(err));
  };

  const togglePause = () => {
    const action = paused ? 'resume' : 'pause';
    fetch(`/api/streams/${domain}/${action}`, { method: 'POST' })
      .then(() => setPaused(!paused));
  };

  const updateEps = (val) => {
    setEps(val);
    fetch(`/api/streams/${domain}/eps?val=${val}`, { method: 'PUT' });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-md)', height: '100%' }}>
      <div style={{ display: 'flex', gap: 'var(--space-md)', alignItems: 'center' }}>
        <button className="btn btn-secondary" onClick={onBack}>&larr; Back</button>
        <h2 className="text-h2" style={{ margin: 0, flex: 1 }}>{domain} <span style={{ color: paused ? 'var(--accent-amber)' : 'var(--accent-emerald)', fontSize: '14px', marginLeft: '8px' }}>{paused ? 'PAUSED' : 'LIVE'}</span></h2>
        
        <button className="btn btn-secondary" onClick={togglePause}>
          {paused ? 'Resume Engine' : 'Pause Engine'}
        </button>
        <button className="btn btn-secondary" style={{ color: 'var(--accent-rose)' }} onClick={handleStop}>
          <Trash2 size={16} style={{ marginRight: '8px' }} /> Stop Stream
        </button>
      </div>

      <div className="glass-panel" style={{ padding: 'var(--space-md)', display: 'flex', gap: 'var(--space-lg)', alignItems: 'center' }}>
        <label className="text-body" style={{ textTransform: 'uppercase', fontSize: '11px', flexShrink: 0 }}>Adjust Live EPS:</label>
        <input type="range" min="1" max="10000" value={eps} onChange={e => updateEps(Number(e.target.value))} style={{ flex: 1, accentColor: 'var(--accent-cyan)' }} />
        <span className="text-mono" style={{ width: '60px', textAlign: 'right' }}>{eps.toLocaleString()}</span>
      </div>

      <div className="glass-panel" style={{ flex: 1, padding: 'var(--space-md)', background: 'var(--surface-0)', overflowY: 'auto', fontFamily: 'monospace', fontSize: '12px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {logs.length === 0 ? <span style={{ color: 'var(--text-secondary)' }}>Waiting for live events...</span> : logs.map((log, i) => (
          <div key={i} style={{ color: 'var(--accent-emerald)', borderBottom: '1px solid var(--surface-2)', paddingBottom: '4px' }}>{log}</div>
        ))}
      </div>
    </div>
  );
};

const DomainOption = ({ title, icon, color, active }) => (
  <div className="glass-panel" style={{ 
    padding: 'var(--space-md)', 
    cursor: 'pointer', 
    border: active ? `1px solid ${color}` : '1px solid var(--border)',
    background: active ? `${color}15` : 'var(--surface-2)',
    display: 'flex', alignItems: 'center', gap: '12px',
    transition: 'all 0.2s'
  }}>
    <div style={{ color: color }}>{icon}</div>
    <span className="text-h3" style={{ fontSize: '15px' }}>{title}</span>
  </div>
);

// Dummy icons
const ActivityIcon = () => <Zap size={20} />;
const ShoppingCartIcon = () => <Database size={20} />;
const BurgerIcon = () => <Settings2 size={20} />;
const SparklesIcon = () => <Plus size={20} />;

export default Streams;
