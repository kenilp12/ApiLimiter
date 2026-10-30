import React, { useEffect, useState } from 'react';
import { analyticsService } from '../services/analyticsService';
import { StatCard } from '../components/common/StatCard';
import { TrafficChart } from '../components/charts/TrafficChart';
import { AlgorithmPieChart } from '../components/charts/AlgorithmPieChart';
import { AlgorithmBadge } from '../components/common/Badge';
import {
  Activity,
  CheckCircle2,
  AlertOctagon,
  Percent,
  Clock,
  KeyRound,
  Sliders,
  RefreshCw,
  AlertTriangle
} from 'lucide-react';

export const DashboardPage = () => {
  const [overview, setOverview] = useState(null);
  const [timeseries, setTimeseries] = useState([]);
  const [recentRequests, setRecentRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const fetchData = async (isManual = false) => {
    if (isManual) setRefreshing(true);
    setError(null);
    try {
      const [overviewData, timeseriesData, requestsData] = await Promise.all([
        analyticsService.getOverview(),
        analyticsService.getTimeseries(60),
        analyticsService.getRecentRequests(0, 10),
      ]);
      setOverview(overviewData);
      setTimeseries(timeseriesData);
      setRecentRequests(requestsData.content || []);
    } catch (err) {
      console.error('Failed to load dashboard data:', err);
      setError('Failed to connect to the analytics backend. Please verify the backend service is running.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchData();
    // Auto-refresh metrics every 10 seconds for real-time traffic view
    const interval = setInterval(() => {
      fetchData();
    }, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
            System Overview
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Real-time telemetry, rate-limit enforcement, and distributed Redis traffic metrics
          </p>
        </div>
        <button
          onClick={() => fetchData(true)}
          disabled={refreshing}
          className="inline-flex items-center space-x-2 px-3.5 py-2 rounded-xl text-xs font-semibold border border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors self-start sm:self-auto"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin text-brand-500' : ''}`} />
          <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
        </button>
      </div>

      {/* Error Alert */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-600 dark:text-rose-400 text-xs flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
          <button
            onClick={() => fetchData(true)}
            className="underline font-semibold ml-4 hover:opacity-80"
          >
            Retry
          </button>
        </div>
      )}

      {/* Metric Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Requests"
          value={overview?.totalRequests?.toLocaleString() || 0}
          icon={Activity}
          color="brand"
        />
        <StatCard
          title="Allowed Traffic"
          value={overview?.allowedRequests?.toLocaleString() || 0}
          icon={CheckCircle2}
          color="emerald"
        />
        <StatCard
          title="Rate Limit Blocked (429)"
          value={overview?.blockedRequests?.toLocaleString() || 0}
          icon={AlertOctagon}
          color="rose"
        />
        <StatCard
          title="Rejection Rate"
          value={`${overview?.rejectionRatePercentage || 0}%`}
          change={`${overview?.averageResponseTimeMs || 0} ms avg latency`}
          changeType={overview?.rejectionRatePercentage > 20 ? 'negative' : 'neutral'}
          icon={Percent}
          color="amber"
        />
      </div>

      {/* Charts Section: Traffic Throughput & Algorithm Breakdown */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 glass-card p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-base font-semibold text-slate-900 dark:text-white">
                Traffic & Throttling (Last 60 Minutes)
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Allowed requests vs HTTP 429 rate-limited rejections
              </p>
            </div>
            <div className="flex items-center space-x-2 text-xs text-slate-400">
              <span className="w-2 h-2 rounded-full bg-emerald-500" />
              <span>Allowed</span>
              <span className="w-2 h-2 rounded-full bg-rose-500 ml-2" />
              <span>Blocked</span>
            </div>
          </div>
          <TrafficChart data={timeseries} height={280} />
        </div>

        <div className="glass-card p-6">
          <div className="mb-4">
            <h3 className="text-base font-semibold text-slate-900 dark:text-white">
              Algorithm Distribution
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Evaluations by active rate limiting algorithm
            </p>
          </div>
          <AlgorithmPieChart data={overview?.algorithmBreakdown || {}} height={280} />
        </div>
      </div>

      {/* Recent Activity Table */}
      <div className="glass-card overflow-hidden">
        <div className="p-5 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
          <div>
            <h3 className="text-base font-semibold text-slate-900 dark:text-white">
              Recent Gateway Requests
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Real-time feed of traffic traversing the Spring Cloud Gateway
            </p>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50/50 dark:bg-slate-900/50 text-xs font-semibold uppercase text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-800">
              <tr>
                <th className="px-5 py-3">Timestamp</th>
                <th className="px-5 py-3">Method & Endpoint</th>
                <th className="px-5 py-3">API Key Prefix</th>
                <th className="px-5 py-3">Algorithm</th>
                <th className="px-5 py-3">Status</th>
                <th className="px-5 py-3">Remaining Quota</th>
                <th className="px-5 py-3">Latency</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
              {recentRequests.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-8 text-center text-slate-400">
                    No requests recorded yet. Try the <a href="/playground" className="text-brand-500 underline">API Playground</a> to test requests!
                  </td>
                </tr>
              ) : (
                recentRequests.map((req) => (
                  <tr key={req.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors">
                    <td className="px-5 py-3 text-xs text-slate-500 font-mono whitespace-nowrap">
                      {new Date(req.timestamp).toLocaleTimeString()}
                    </td>
                    <td className="px-5 py-3">
                      <div className="flex items-center space-x-2">
                        <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                          req.method === 'GET' ? 'bg-blue-500/10 text-blue-500' : 'bg-emerald-500/10 text-emerald-500'
                        }`}>
                          {req.method}
                        </span>
                        <span className="font-mono text-xs text-slate-800 dark:text-slate-200">
                          {req.endpoint}
                        </span>
                      </div>
                    </td>
                    <td className="px-5 py-3 font-mono text-xs text-slate-500">
                      {req.apiKeyPrefix || 'arl_live_***'}
                    </td>
                    <td className="px-5 py-3">
                      <AlgorithmBadge algorithm={req.algorithm} />
                    </td>
                    <td className="px-5 py-3">
                      {req.isAllowed ? (
                        <span className="inline-flex items-center space-x-1 text-emerald-600 dark:text-emerald-400 font-semibold text-xs">
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          <span>200 OK</span>
                        </span>
                      ) : (
                        <span className="inline-flex items-center space-x-1 text-rose-600 dark:text-rose-400 font-semibold text-xs">
                          <AlertOctagon className="w-3.5 h-3.5" />
                          <span>429 Blocked</span>
                        </span>
                      )}
                    </td>
                    <td className="px-5 py-3 text-xs font-mono">
                      {req.rateLimitRemaining !== null ? req.rateLimitRemaining : '-'}
                    </td>
                    <td className="px-5 py-3 text-xs text-slate-500">
                      {req.responseTimeMs ? `${req.responseTimeMs} ms` : '< 5 ms'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
