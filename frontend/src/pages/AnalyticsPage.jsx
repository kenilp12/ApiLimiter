import React, { useEffect, useState } from 'react';
import { analyticsService } from '../services/analyticsService';
import { TrafficChart } from '../components/charts/TrafficChart';
import { AlgorithmPieChart } from '../components/charts/AlgorithmPieChart';
import { StatCard } from '../components/common/StatCard';
import {
  BarChart2,
  TrendingUp,
  Clock,
  ShieldCheck,
  ShieldAlert,
  Calendar
} from 'lucide-react';

export const AnalyticsPage = () => {
  const [timeRange, setTimeRange] = useState(60);
  const [timeseries, setTimeseries] = useState([]);
  const [overview, setOverview] = useState(null);
  const [loading, setLoading] = useState(true);

  const loadData = async () => {
    try {
      const [ts, ov] = await Promise.all([
        analyticsService.getTimeseries(timeRange),
        analyticsService.getOverview(),
      ]);
      setTimeseries(ts);
      setOverview(ov);
    } catch (err) {
      console.error('Failed to load analytics:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [timeRange]);

  return (
    <div className="space-y-6">
      {/* Header with Time Range Filter */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
            Traffic Analytics & Telemetry
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Historical request rates, throttling decisions, and algorithm effectiveness
          </p>
        </div>

        {/* Time Window Selector */}
        <div className="inline-flex rounded-xl p-1 bg-slate-200/60 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 self-start sm:self-auto">
          {[
            { label: '15m', value: 15 },
            { label: '1h', value: 60 },
            { label: '6h', value: 360 },
            { label: '24h', value: 1440 },
          ].map((item) => (
            <button
              key={item.value}
              onClick={() => setTimeRange(item.value)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                timeRange === item.value
                  ? 'bg-white dark:bg-slate-900 text-brand-600 dark:text-brand-400 shadow-sm'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      {/* Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Evaluated Traffic"
          value={overview?.totalRequests?.toLocaleString() || 0}
          icon={TrendingUp}
          color="brand"
        />
        <StatCard
          title="Permitted Requests"
          value={overview?.allowedRequests?.toLocaleString() || 0}
          icon={ShieldCheck}
          color="emerald"
        />
        <StatCard
          title="Throttled Requests (429)"
          value={overview?.blockedRequests?.toLocaleString() || 0}
          icon={ShieldAlert}
          color="rose"
        />
        <StatCard
          title="Average Latency"
          value={`${overview?.averageResponseTimeMs || 0} ms`}
          change="P99: < 25ms"
          changeType="positive"
          icon={Clock}
          color="blue"
        />
      </div>

      {/* Main Traffic Timeseries Area Chart */}
      <div className="glass-card p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-semibold text-slate-900 dark:text-white">
              Request Throughput & Throttling History
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Aggregated traffic across all active API keys in {timeRange >= 60 ? `${timeRange / 60}h` : `${timeRange}m`} window
            </p>
          </div>
        </div>
        <TrafficChart data={timeseries} height={340} />
      </div>

      {/* Secondary Row: Algorithm Breakdown & Traffic Comparison */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="glass-card p-6">
          <div className="mb-4">
            <h3 className="text-base font-semibold text-slate-900 dark:text-white">
              Algorithm Distribution
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Breakdown by active rate limiting algorithm
            </p>
          </div>
          <AlgorithmPieChart data={overview?.algorithmBreakdown || {}} height={260} />
        </div>

        <div className="lg:col-span-2 glass-card p-6 flex flex-col justify-between">
          <div>
            <h3 className="text-base font-semibold text-slate-900 dark:text-white mb-2">
              Rate Limiting Efficiency & Health
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 mb-6">
              Evaluation summary across the platform's distributed nodes
            </p>

            <div className="space-y-4">
              <div>
                <div className="flex justify-between text-xs font-semibold mb-1">
                  <span className="text-slate-600 dark:text-slate-300">Traffic Health Ratio</span>
                  <span className="text-emerald-500">
                    {100 - (overview?.rejectionRatePercentage || 0)}% Accepted
                  </span>
                </div>
                <div className="w-full h-2.5 rounded-full bg-slate-200 dark:bg-slate-800 overflow-hidden">
                  <div
                    className="h-full bg-emerald-500 rounded-full transition-all duration-500"
                    style={{ width: `${100 - (overview?.rejectionRatePercentage || 0)}%` }}
                  />
                </div>
              </div>

              <div>
                <div className="flex justify-between text-xs font-semibold mb-1">
                  <span className="text-slate-600 dark:text-slate-300">Rejection Throttling Ratio</span>
                  <span className="text-rose-500">
                    {overview?.rejectionRatePercentage || 0}% Throttled
                  </span>
                </div>
                <div className="w-full h-2.5 rounded-full bg-slate-200 dark:bg-slate-800 overflow-hidden">
                  <div
                    className="h-full bg-rose-500 rounded-full transition-all duration-500"
                    style={{ width: `${overview?.rejectionRatePercentage || 0}%` }}
                  />
                </div>
              </div>
            </div>
          </div>

          <div className="mt-6 pt-4 border-t border-slate-200 dark:border-slate-800 grid grid-cols-2 gap-4 text-xs font-mono">
            <div>
              <span className="text-slate-400 block text-[10px] uppercase">Active Monitored Keys</span>
              <span className="text-lg font-bold text-slate-900 dark:text-white">
                {overview?.activeKeysCount || 0}
              </span>
            </div>
            <div>
              <span className="text-slate-400 block text-[10px] uppercase">Enforced Policies</span>
              <span className="text-lg font-bold text-slate-900 dark:text-white">
                {overview?.activePoliciesCount || 0}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
