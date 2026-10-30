import React, { useEffect, useState, useCallback } from 'react';
import { analyticsService } from '../services/analyticsService';
import { AlgorithmBadge } from '../components/common/Badge';
import { ShieldAlert, RefreshCw, AlertTriangle, Search, Filter, XCircle } from 'lucide-react';

export const BlockedRequestsPage = () => {
  const [blockedLogs, setBlockedLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [refreshing, setRefreshing] = useState(false);

  // Filters
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedAlgorithm, setSelectedAlgorithm] = useState('ALL');

  const loadData = useCallback(async (targetPage = 0, isManual = false) => {
    if (isManual) setRefreshing(true);
    setError(null);
    try {
      const filters = {};
      if (searchTerm.trim()) filters.search = searchTerm.trim();
      if (selectedAlgorithm && selectedAlgorithm !== 'ALL') filters.algorithm = selectedAlgorithm;

      const data = await analyticsService.getBlockedRequests(targetPage, 15, filters);
      setBlockedLogs(data.content || []);
      setTotalPages(data.totalPages || 1);
      setPage(data.number || 0);
    } catch (err) {
      console.error('Failed to load blocked requests:', err);
      setError('Unable to fetch blocked request history. Ensure backend service is reachable.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [searchTerm, selectedAlgorithm]);

  useEffect(() => {
    loadData(0);
  }, [loadData]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    loadData(0);
  };

  const handleClearFilters = () => {
    setSearchTerm('');
    setSelectedAlgorithm('ALL');
  };

  const hasActiveFilters = searchTerm.trim() !== '' || selectedAlgorithm !== 'ALL';

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white flex items-center space-x-2">
            <ShieldAlert className="w-6 h-6 text-rose-500" />
            <span>Blocked Requests (HTTP 429)</span>
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Real-time query log of requests throttled and rejected by the distributed rate limiter
          </p>
        </div>
        <button
          onClick={() => loadData(page, true)}
          disabled={refreshing}
          className="inline-flex items-center space-x-2 px-3.5 py-2 rounded-xl text-xs font-semibold border border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors self-start sm:self-auto"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin text-brand-500' : ''}`} />
          <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="glass-card p-4">
        <form onSubmit={handleSearchSubmit} className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
          <div className="flex-1 flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Search route (e.g. /protected-api/data) or API prefix..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-9 pr-3.5 py-2 rounded-xl text-xs border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-brand-500"
              />
            </div>

            <div className="flex items-center space-x-2">
              <Filter className="w-3.5 h-3.5 text-slate-400 shrink-0" />
              <select
                value={selectedAlgorithm}
                onChange={(e) => setSelectedAlgorithm(e.target.value)}
                className="px-3 py-2 rounded-xl text-xs border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-brand-500"
              >
                <option value="ALL">All Algorithms</option>
                <option value="TOKEN_BUCKET">Token Bucket</option>
                <option value="SLIDING_WINDOW">Sliding Window</option>
                <option value="FIXED_WINDOW">Fixed Window</option>
                <option value="LEAKY_BUCKET">Leaky Bucket</option>
              </select>
            </div>
          </div>

          <div className="flex items-center space-x-2 justify-end">
            {hasActiveFilters && (
              <button
                type="button"
                onClick={handleClearFilters}
                className="px-3 py-2 rounded-xl text-xs font-medium text-slate-500 hover:text-slate-800 dark:hover:text-slate-200 flex items-center space-x-1"
              >
                <XCircle className="w-3.5 h-3.5" />
                <span>Clear</span>
              </button>
            )}
            <button
              type="submit"
              className="px-4 py-2 rounded-xl text-xs font-semibold text-white bg-brand-600 hover:bg-brand-500 transition-colors shadow-sm"
            >
              Apply Filter
            </button>
          </div>
        </form>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-600 dark:text-rose-400 text-xs flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
          <button
            onClick={() => loadData(page, true)}
            className="underline font-semibold ml-4 hover:opacity-80"
          >
            Retry
          </button>
        </div>
      )}

      {/* Blocked Requests Table */}
      <div className="glass-card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50/50 dark:bg-slate-900/50 text-xs font-semibold uppercase text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-800">
              <tr>
                <th className="px-5 py-3">Timestamp</th>
                <th className="px-5 py-3">Client IP</th>
                <th className="px-5 py-3">Endpoint Route</th>
                <th className="px-5 py-3">API Key Prefix</th>
                <th className="px-5 py-3">Policy & Algorithm</th>
                <th className="px-5 py-3">Status</th>
                <th className="px-5 py-3">Policy Quota</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:border-slate-800">
              {loading ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-400">
                    <RefreshCw className="w-6 h-6 text-brand-500 animate-spin mx-auto mb-2" />
                    <span>Loading blocked request logs...</span>
                  </td>
                </tr>
              ) : blockedLogs.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-400">
                    <AlertTriangle className="w-8 h-8 text-slate-400 mx-auto mb-2 opacity-50" />
                    <span>
                      {hasActiveFilters
                        ? 'No blocked requests match the current search filters.'
                        : 'No blocked requests recorded yet. Incoming HTTP 429 events will appear here in real time.'}
                    </span>
                  </td>
                </tr>
              ) : (
                blockedLogs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors">
                    <td className="px-5 py-3.5 text-xs text-slate-500 font-mono whitespace-nowrap">
                      {new Date(log.timestamp).toLocaleString()}
                    </td>
                    <td className="px-5 py-3.5 font-mono text-xs text-slate-600 dark:text-slate-300">
                      {log.clientIp || '127.0.0.1'}
                    </td>
                    <td className="px-5 py-3.5 font-mono text-xs text-slate-800 dark:text-slate-200">
                      <span className="font-bold text-rose-500 mr-1.5">{log.method}</span>
                      {log.endpoint}
                    </td>
                    <td className="px-5 py-3.5 font-mono text-xs text-slate-500">
                      {log.apiKeyPrefix || 'arl_live_***'}
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex flex-col gap-1 items-start">
                        {log.policyName && (
                          <span className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                            {log.policyName}
                          </span>
                        )}
                        <AlgorithmBadge algorithm={log.algorithm} />
                      </div>
                    </td>
                    <td className="px-5 py-3.5">
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-bold bg-rose-500/10 text-rose-500 border border-rose-500/20">
                        429 Blocked
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-xs font-mono text-slate-500">
                      Limit: {log.rateLimitLimit || '-'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Controls */}
        {totalPages > 1 && (
          <div className="p-4 border-t border-slate-200 dark:border-slate-800 flex items-center justify-between text-xs text-slate-500">
            <span>
              Page {page + 1} of {totalPages}
            </span>
            <div className="flex space-x-2">
              <button
                disabled={page === 0}
                onClick={() => loadData(page - 1)}
                className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 disabled:opacity-40 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
              >
                Previous
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => loadData(page + 1)}
                className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 disabled:opacity-40 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
