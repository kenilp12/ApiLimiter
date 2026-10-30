import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { Shield, Lock, User, ArrowRight } from 'lucide-react';

export const LoginPage = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const { toast } = useToast();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!username.trim() || !password) {
      toast.warning('Please enter your username and password.');
      return;
    }

    try {
      setLoading(true);
      await login(username, password);
      toast.success(`Welcome back, ${username}!`);
      navigate('/');
    } catch (err) {
      if (err.code === 'ERR_NETWORK' || !err.response || err.response.status >= 502) {
        toast.error('Unable to connect to the backend server. Please verify the backend is running on port 8080.');
      } else {
        toast.error(err.response?.data?.message || 'Invalid username or password');
      }
    } finally {
      setLoading(false);
    }
  };

  const fillCredentials = (user, pass) => {
    setUsername(user);
    setPassword(pass);
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-slate-100 dark:bg-slate-950">
      <div className="w-full max-w-md">
        {/* Header Branding */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-to-tr from-brand-600 to-indigo-500 text-white shadow-xl shadow-brand-500/20 mb-4">
            <Shield className="w-7 h-7" />
          </div>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white">
            API Rate Limiter
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Distributed Platform & Developer Dashboard
          </p>
        </div>

        {/* Login Card */}
        <div className="glass-card p-8 bg-white dark:bg-slate-900/90 shadow-2xl">
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-2">
                Username or Email
              </label>
              <div className="relative">
                <User className="w-5 h-5 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="admin or developer"
                  required
                  className="w-full pl-11 pr-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500 text-sm"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-2">
                Password
              </label>
              <div className="relative">
                <Lock className="w-5 h-5 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  required
                  className="w-full pl-11 pr-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500 text-sm"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 px-4 rounded-xl font-semibold text-white bg-brand-600 hover:bg-brand-500 shadow-lg shadow-brand-500/25 transition-all flex items-center justify-center space-x-2 text-sm disabled:opacity-50"
            >
              <span>{loading ? 'Authenticating...' : 'Sign In'}</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </form>

          {/* Quick Demo Credentials Autofill */}
          <div className="mt-6 pt-5 border-t border-slate-200 dark:border-slate-800 text-xs">
            <span className="text-slate-400 font-medium block mb-2 text-center">
              Quick Demo Accounts:
            </span>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => fillCredentials('admin', 'admin123')}
                className="py-1.5 px-3 rounded-lg border border-slate-200 dark:border-slate-800 hover:border-brand-500/50 text-slate-700 dark:text-slate-300 font-medium transition-colors"
              >
                Admin (admin123)
              </button>
              <button
                type="button"
                onClick={() => fillCredentials('developer', 'dev123')}
                className="py-1.5 px-3 rounded-lg border border-slate-200 dark:border-slate-800 hover:border-brand-500/50 text-slate-700 dark:text-slate-300 font-medium transition-colors"
              >
                Dev (dev123)
              </button>
            </div>
          </div>

          <div className="mt-6 text-center text-xs text-slate-500 dark:text-slate-400">
            Need an account?{' '}
            <Link to="/register" className="text-brand-500 hover:underline font-semibold">
              Create one now
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
