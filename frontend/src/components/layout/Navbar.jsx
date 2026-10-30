import React from 'react';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';
import { Moon, Sun, Shield, LogOut, User as UserIcon } from 'lucide-react';

export const Navbar = () => {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();

  return (
    <header className="sticky top-0 z-30 h-16 border-b border-slate-200 dark:border-slate-800 bg-white/80 dark:bg-slate-900/80 backdrop-blur-md px-6 flex items-center justify-between">
      {/* Brand logo & title */}
      <div className="flex items-center space-x-3">
        <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center text-white shadow-md shadow-brand-500/20">
          <Shield className="w-5 h-5" />
        </div>
        <div>
          <h1 className="text-base font-bold text-slate-900 dark:text-white leading-tight">
            API Rate Limiter
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">
            Distributed Platform
          </p>
        </div>
        {/* Distributed Gateway Status badge */}
        <span className="hidden sm:inline-flex items-center space-x-1.5 ml-4 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
          <span>Gateway Active</span>
        </span>
      </div>

      {/* Right actions: Theme toggle and User profile */}
      <div className="flex items-center space-x-3">
        <button
          onClick={toggleTheme}
          aria-label="Toggle theme"
          className="p-2 rounded-xl text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
        >
          {theme === 'dark' ? <Sun className="w-5 h-5 text-amber-400" /> : <Moon className="w-5 h-5 text-slate-600" />}
        </button>

        {user && (
          <div className="flex items-center space-x-3 pl-3 border-l border-slate-200 dark:border-slate-800">
            <div className="text-right hidden md:block">
              <div className="text-sm font-semibold text-slate-900 dark:text-white">
                {user.username}
              </div>
              <div className="text-xs text-brand-600 dark:text-brand-400 font-medium">
                {user.role === 'ROLE_ADMIN' ? 'Administrator' : 'Developer'}
              </div>
            </div>
            <button
              onClick={logout}
              title="Logout"
              className="p-2 rounded-xl text-rose-500 hover:bg-rose-500/10 transition-colors"
            >
              <LogOut className="w-5 h-5" />
            </button>
          </div>
        )}
      </div>
    </header>
  );
};
