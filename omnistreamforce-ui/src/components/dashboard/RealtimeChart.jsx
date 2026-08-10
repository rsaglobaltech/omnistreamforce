import React, { useState, useEffect } from 'react';
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';

const RealtimeChart = ({ data, metric }) => {
  const [chartData, setChartData] = useState([]);

  useEffect(() => {
    // Generate initial dummy data for the preview
    const initial = [];
    let now = new Date().getTime();
    for (let i = 60; i >= 0; i--) {
      initial.push({
        time: new Date(now - i * 1000).toLocaleTimeString(),
        eps: Math.floor(Math.random() * 2000) + 8000,
        latency: Math.random() * 5 + 10
      });
    }
    setChartData(initial);
  }, []);

  useEffect(() => {
    if (data) {
      setChartData(prev => {
        const newData = [...prev, {
          time: new Date().toLocaleTimeString(),
          eps: data.eps,
          latency: data.latency || 0
        }];
        if (newData.length > 60) newData.shift();
        return newData;
      });
    }
  }, [data]);

  return (
    <div style={{ width: '100%', height: 'calc(100% - 40px)' }}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={chartData} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
          <defs>
            <linearGradient id="colorEps" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="var(--accent-cyan)" stopOpacity={0.3}/>
              <stop offset="95%" stopColor="var(--accent-cyan)" stopOpacity={0}/>
            </linearGradient>
          </defs>
          <XAxis dataKey="time" hide />
          <YAxis stroke="var(--text-muted)" fontSize={12} tickFormatter={(val) => val >= 1000 ? `${(val/1000).toFixed(1)}k` : val} />
          <Tooltip 
            contentStyle={{ backgroundColor: 'var(--surface-2)', borderColor: 'var(--border)', borderRadius: 'var(--radius-sm)' }}
            itemStyle={{ color: 'var(--accent-cyan)' }}
          />
          <Area type="monotone" dataKey="eps" stroke="var(--accent-cyan)" fillOpacity={1} fill="url(#colorEps)" isAnimationActive={false} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
};

export default RealtimeChart;
