import React from 'react';
import { useAuth } from '../context/AuthContext';
import {
  User,
  Shield,
  Server,
  Database,
  ExternalLink,
  Cpu,
  Layers,
  Lock
} from 'lucide-react';

export const SettingsPage = () => {
  const { user } = useAuth();

  const services = [
    { name: 'Backend Management API', url: 'http://localhost:8080', port: '8080', status: 'Online' },
    { name: 'Spring Cloud Gateway', url: 'http://localhost:8081', port: '8081', status: 'Online' },
    { name: 'Redis Distributed State Store', url: 'localhost:6379', port: '6379', status: 'Active' },
    { name: 'PostgreSQL Relational DB', url: 'localhost:5432', port: '5432', status: 'Active' },
    { name: 'Prometheus Telemetry Scraper', url: 'http://localhost:9090', port: '9090', status: 'Scraping' },
    { name: 'Grafana Metric Dashboards', url: 'http://localhost:3001', port: '3001', status: 'Provisioned' },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
          Platform Configuration & Account
        </h2>
        <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
          Cluster deployment topology, fail-mode strategy, and active user profile
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* User Profile Card */}
        <div className="glass-card p-6 space-y-4">
          <div className="flex items-center space-x-3">
            <div className="w-12 h-12 rounded-2xl bg-brand-500/10 text-brand-500 flex items-center justify-center font-bold text-lg border border-brand-500/20">
              <User className="w-6 h-6" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900 dark:text-white">
                {user?.username || 'Current User'}
              </h3>
              <p className="text-xs text-slate-400">{user?.email || 'N/A'}</p>
            </div>
          </div>

          <div className="pt-3 border-t border-slate-200 dark:border-slate-800 space-y-2 text-xs">
            <div className="flex justify-between py-1">
              <span className="text-slate-400">Assigned Role</span>
              <span className="font-semibold text-brand-500">
                {user?.role === 'ROLE_ADMIN' ? 'Platform Administrator (Full Access)' : 'Standard Developer'}
              </span>
            </div>
            <div className="flex justify-between py-1">
              <span className="text-slate-400">Authentication</span>
              <span className="font-semibold text-slate-700 dark:text-slate-300">
                JWT (HMAC-SHA256)
              </span>
            </div>
            <div className="flex justify-between py-1">
              <span className="text-slate-400">Password Hashing</span>
              <span className="font-semibold text-slate-700 dark:text-slate-300">
                BCrypt (Cost Factor 10)
              </span>
            </div>
          </div>
        </div>

        {/* Rate Limiting Strategy & Fail-Mode */}
        <div className="lg:col-span-2 glass-card p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center space-x-2">
              <Cpu className="w-5 h-5 text-brand-500" />
              <span>Distributed Architecture & Fail-Safe Mode</span>
            </h3>
            <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-500 border border-emerald-500/20">
              Fail-Closed (Default)
            </span>
          </div>

          <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
            Rate limiting state is synchronized across gateway nodes using atomic Redis Lua scripts. Each algorithm operation (counter increments, log pruning, bucket refills) executes atomically inside Redis in <strong>O(1)</strong> or <strong>O(log N)</strong> time without race conditions.
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs pt-2">
            <div className="p-3 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/50">
              <strong className="block text-slate-900 dark:text-white font-semibold mb-1">
                Fail-Closed (Security Mode)
              </strong>
              <p className="text-slate-500 dark:text-slate-400 leading-relaxed text-[11px]">
                If Redis becomes unreachable or times out, requests are safely rejected to protect upstream infrastructure from cascading failure.
              </p>
            </div>
            <div className="p-3 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/50">
              <strong className="block text-slate-900 dark:text-white font-semibold mb-1">
                Fail-Open (Availability Mode)
              </strong>
              <p className="text-slate-500 dark:text-slate-400 leading-relaxed text-[11px]">
                Configurable via <code>RATE_LIMIT_FAIL_OPEN=true</code> to prioritize service availability over strict quota enforcement.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Services Topology */}
      <div className="glass-card overflow-hidden">
        <div className="p-5 border-b border-slate-200 dark:border-slate-800">
          <h3 className="text-base font-semibold text-slate-900 dark:text-white flex items-center space-x-2">
            <Server className="w-4 h-4 text-brand-500" />
            <span>Platform Topology & Local Service Ports</span>
          </h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50/50 dark:bg-slate-900/50 text-xs font-semibold uppercase text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-800">
              <tr>
                <th className="px-5 py-3">Service Name</th>
                <th className="px-5 py-3">Internal/External URL</th>
                <th className="px-5 py-3">Port</th>
                <th className="px-5 py-3">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-xs">
              {services.map((s, idx) => (
                <tr key={idx} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors">
                  <td className="px-5 py-3.5 font-medium text-slate-900 dark:text-white">
                    {s.name}
                  </td>
                  <td className="px-5 py-3.5 font-mono text-slate-500">
                    <a
                      href={s.url.startsWith('http') ? s.url : undefined}
                      target="_blank"
                      rel="noreferrer"
                      className="hover:text-brand-500 hover:underline flex items-center space-x-1"
                    >
                      <span>{s.url}</span>
                      {s.url.startsWith('http') && <ExternalLink className="w-3 h-3" />}
                    </a>
                  </td>
                  <td className="px-5 py-3.5 font-mono font-bold text-slate-700 dark:text-slate-300">
                    {s.port}
                  </td>
                  <td className="px-5 py-3.5">
                    <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-500/10 text-emerald-500 border border-emerald-500/20">
                      {s.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
