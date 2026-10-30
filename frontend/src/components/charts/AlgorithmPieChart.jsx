import React from 'react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from 'recharts';

export const AlgorithmPieChart = ({ data = {}, height = 260 }) => {
  const COLORS = {
    TOKEN_BUCKET: '#f59e0b',   // amber
    SLIDING_WINDOW: '#3b82f6', // blue
    FIXED_WINDOW: '#10b981',   // emerald
    LEAKY_BUCKET: '#a855f7',   // purple
  };

  const LABELS = {
    TOKEN_BUCKET: 'Token Bucket',
    SLIDING_WINDOW: 'Sliding Window',
    FIXED_WINDOW: 'Fixed Window',
    LEAKY_BUCKET: 'Leaky Bucket',
  };

  const chartData = Object.entries(data).map(([key, value]) => ({
    name: LABELS[key] || key,
    rawKey: key,
    value: Number(value),
  })).filter(item => item.value > 0);

  if (chartData.length === 0) {
    return (
      <div className="flex items-center justify-center h-60 text-sm text-slate-400">
        No algorithm data recorded yet.
      </div>
    );
  }

  return (
    <div className="w-full" style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={chartData}
            cx="50%"
            cy="50%"
            innerRadius={60}
            outerRadius={85}
            paddingAngle={4}
            dataKey="value"
          >
            {chartData.map((entry) => (
              <Cell
                key={`cell-${entry.rawKey}`}
                fill={COLORS[entry.rawKey] || '#6366f1'}
                stroke="transparent"
              />
            ))}
          </Pie>
          <Tooltip
            content={({ active, payload }) => {
              if (active && payload && payload.length) {
                const item = payload[0];
                return (
                  <div className="glass-card p-2.5 shadow-xl text-xs border border-slate-700 bg-slate-900/95 text-white">
                    <p className="font-semibold">{item.name}</p>
                    <p className="text-slate-300 mt-1">Requests: <strong>{item.value}</strong></p>
                  </div>
                );
              }
              return null;
            }}
          />
          <Legend
            verticalAlign="bottom"
            iconType="circle"
            wrapperStyle={{ fontSize: '11px', paddingTop: '10px' }}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
};
