import React from 'react';

export const StatCard = ({ title, value, change, changeType = 'positive', icon: Icon, color = 'brand' }) => {
  const colorMap = {
    brand: 'text-brand-500 bg-brand-500/10 border-brand-500/20',
    emerald: 'text-emerald-500 bg-emerald-500/10 border-emerald-500/20',
    rose: 'text-rose-500 bg-rose-500/10 border-rose-500/20',
    amber: 'text-amber-500 bg-amber-500/10 border-amber-500/20',
    blue: 'text-blue-500 bg-blue-500/10 border-blue-500/20',
  };

  return (
    <div className="glass-card p-5 glass-card-hover">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-slate-500 dark:text-slate-400">{title}</span>
        {Icon && (
          <div className={`p-2 rounded-xl border ${colorMap[color] || colorMap.brand}`}>
            <Icon className="w-5 h-5" />
          </div>
        )}
      </div>
      <div className="mt-3 flex items-baseline justify-between">
        <span className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
          {value}
        </span>
        {change && (
          <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
            changeType === 'positive' 
              ? 'text-emerald-600 dark:text-emerald-400 bg-emerald-500/10' 
              : changeType === 'negative'
              ? 'text-rose-600 dark:text-rose-400 bg-rose-500/10'
              : 'text-slate-500 bg-slate-500/10'
          }`}>
            {change}
          </span>
        )}
      </div>
    </div>
  );
};
