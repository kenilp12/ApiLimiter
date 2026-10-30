import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  KeyRound,
  SlidersHorizontal,
  PlayCircle,
  BarChart3,
  ShieldAlert,
  Settings
} from 'lucide-react';

export const Sidebar = () => {
  const navItems = [
    { to: '/', label: 'Overview', icon: LayoutDashboard },
    { to: '/keys', label: 'API Keys', icon: KeyRound },
    { to: '/policies', label: 'Rate Limit Policies', icon: SlidersHorizontal },
    { to: '/playground', label: 'API Playground', icon: PlayCircle },
    { to: '/analytics', label: 'Request Analytics', icon: BarChart3 },
    { to: '/blocked', label: 'Blocked Requests', icon: ShieldAlert },
    { to: '/settings', label: 'Settings', icon: Settings },
  ];

  return (
    <aside className="w-64 border-r border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-md flex flex-col justify-between shrink-0 min-h-[calc(100vh-4rem)]">
      <div className="p-4 space-y-1.5">
        <div className="px-3 py-2 text-xs font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">
          Platform Menu
        </div>
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all ${
                  isActive
                    ? 'bg-brand-500/10 text-brand-600 dark:text-brand-400 font-semibold shadow-sm'
                    : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800/60'
                }`
              }
            >
              <Icon className="w-4 h-4 shrink-0" />
              <span>{item.label}</span>
            </NavLink>
          );
        })}
      </div>

      {/* Redis Shared State Info card at bottom of sidebar */}
      <div className="p-4 m-3 rounded-xl bg-slate-100 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700/60 text-xs">
        <div className="font-semibold text-slate-800 dark:text-slate-200 flex items-center justify-between">
          <span>Distributed Redis</span>
          <span className="w-2 h-2 rounded-full bg-emerald-500" />
        </div>
        <p className="mt-1 text-slate-500 dark:text-slate-400">
          Atomic Lua operations with multi-instance shared state
        </p>
      </div>
    </aside>
  );
};
