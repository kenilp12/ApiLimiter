import React, { useEffect, useState } from 'react';
import { apiKeyService, getKnownRawKey } from '../services/apiKeyService';
import { gatewayApi } from '../services/gatewayApi';
import { useToast } from '../context/ToastContext';
import { AlgorithmBadge } from '../components/common/Badge';
import {
  Play,
  Zap,
  CheckCircle2,
  AlertOctagon,
  KeyRound,
  AlertTriangle,
  Send,
  Timer
} from 'lucide-react';

const DEMO_KEY = 'arl_live_demo1234567890abcdef1234';

export const PlaygroundPage = () => {
  const [keys, setKeys] = useState([]);
  const [selectedKeyId, setSelectedKeyId] = useState('demo');
  const [selectedRawKey, setSelectedRawKey] = useState(DEMO_KEY);
  const [customKey, setCustomKey] = useState(DEMO_KEY);
  const [useCustomKey, setUseCustomKey] = useState(false);
  const [keyNotice, setKeyNotice] = useState({ type: 'success', text: 'Pre-seeded Demo Key loaded.' });
  const [endpoint, setEndpoint] = useState('/protected-api/data');
  const [method, setMethod] = useState('GET');
  const [postPayload, setPostPayload] = useState('{\n  "query": "telemetry",\n  "sampleRate": 100\n}');

  // Execution state
  const [executing, setExecuting] = useState(false);
  const [result, setResult] = useState(null);
  const [burstCount, setBurstCount] = useState(1);
  const [burstResults, setBurstResults] = useState([]);

  // Retry-after live countdown
  const [countdown, setCountdown] = useState(null);

  const { toast } = useToast();

  useEffect(() => {
    const loadKeys = async () => {
      try {
        const data = await apiKeyService.getKeys();
        const active = data.filter((k) => k.status === 'ACTIVE');
        setKeys(active);
      } catch (err) {
        console.warn('Failed to load keys in playground:', err);
      }
    };
    loadKeys();
  }, []);

  useEffect(() => {
    let timer;
    if (countdown !== null && countdown > 0) {
      timer = setInterval(() => {
        setCountdown((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [countdown]);

  const handleKeySelectChange = (keyId) => {
    setSelectedKeyId(keyId);

    if (keyId === 'demo') {
      setSelectedRawKey(DEMO_KEY);
      setCustomKey(DEMO_KEY);
      setKeyNotice({ type: 'success', text: 'Pre-seeded Demo Key loaded.' });
      return;
    }

    const keyObj = keys.find((k) => k.id.toString() === keyId.toString());
    if (!keyObj) return;

    const known = getKnownRawKey(keyObj.id, keyObj.keyPrefix);
    if (known) {
      setSelectedRawKey(known);
      setCustomKey(known);
      setKeyNotice({ type: 'success', text: `Full key loaded from session for "${keyObj.name}".` });
    } else {
      setSelectedRawKey('');
      setKeyNotice({
        type: 'warning',
        text: `Full secret for "${keyObj.name}" is not in browser memory (only SHA-256 hash exists in DB). Please paste the raw API key below.`,
      });
      setUseCustomKey(true);
    }
  };

  const handleSend = async () => {
    const rawCandidate = useCustomKey ? customKey : (selectedRawKey || customKey);
    const activeKey = (rawCandidate || '').trim();

    if (!activeKey) {
      toast.warning('Please select or input an API key.');
      return;
    }

    if (activeKey.includes('...') || (activeKey.startsWith('arl_live_') && activeKey.length <= 16)) {
      toast.error(`Cannot authenticate using masked prefix "${activeKey}". Please enter the full raw API key.`);
      setUseCustomKey(true);
      return;
    }

    setExecuting(true);
    setResult(null); // Clear previous result to eliminate contradictory stale banners
    setBurstResults([]);

    try {
      let parsedPayload = null;
      if (method === 'POST') {
        try {
          parsedPayload = JSON.parse(postPayload);
        } catch (e) {
          toast.error('Invalid JSON in request payload body.');
          setExecuting(false);
          return;
        }
      }

      if (burstCount === 1) {
        const res = await gatewayApi.sendRequest({
          endpoint,
          method,
          apiKey: activeKey,
          payload: parsedPayload,
        });
        setResult(res);

        if (res.status === 429) {
          const retrySec = Number(res.rateLimit?.retryAfter) || 5;
          setCountdown(retrySec);
          toast.error('HTTP 429 Too Many Requests: Rate limit exceeded!');
        } else if (res.status === 401) {
          setCountdown(null);
          toast.error(`HTTP 401 Unauthorized: ${res.data?.message || 'Invalid or missing API key'}`);
        } else if (res.status === 403) {
          setCountdown(null);
          toast.error(`HTTP 403 Forbidden: ${res.data?.message || 'API key is revoked or access denied'}`);
        } else if (res.status >= 500) {
          setCountdown(null);
          toast.error(`HTTP ${res.status}: Server error occurred`);
        } else if (res.status >= 200 && res.status < 300) {
          setCountdown(null);
          toast.success(`HTTP ${res.status} OK: Request permitted by gateway.`);
        } else {
          setCountdown(null);
          toast.warning(`HTTP ${res.status}: ${res.statusText || 'Unexpected response'}`);
        }
      } else {
        // Burst Mode: send N requests concurrently
        toast.info(`Sending burst of ${burstCount} requests through gateway...`);
        const requests = Array.from({ length: burstCount }, () =>
          gatewayApi.sendRequest({
            endpoint,
            method,
            apiKey: activeKey,
            payload: parsedPayload,
          })
        );

        const responses = await Promise.all(requests);
        setBurstResults(responses);
        setResult(responses[responses.length - 1]);

        const blockedCount = responses.filter((r) => r.status === 429).length;
        const authErrorCount = responses.filter((r) => r.status === 401 || r.status === 403).length;
        const allowedCount = responses.filter((r) => r.status >= 200 && r.status < 300).length;

        if (blockedCount > 0) {
          const lastBlocked = responses.find((r) => r.status === 429);
          setCountdown(Number(lastBlocked?.rateLimit?.retryAfter) || 5);
          toast.warning(`Burst complete: ${allowedCount} allowed, ${blockedCount} throttled (HTTP 429).`);
        } else if (authErrorCount > 0) {
          toast.error(`Burst failed: ${authErrorCount} requests rejected due to invalid/revoked API key.`);
        } else {
          toast.success(`Burst complete: All ${allowedCount} requests allowed.`);
        }
      }
    } catch (err) {
      toast.error('Request dispatch failed: ' + err.message);
      setResult(null);
    } finally {
      setExecuting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
          Interactive API Playground
        </h2>
        <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
          Test rate limiting rules, observe real-time HTTP 429 responses, inspect quota headers, and run burst stress tests
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Request Builder */}
        <div className="lg:col-span-5 space-y-5">
          <div className="glass-card p-6 space-y-4">
            <h3 className="text-base font-semibold text-slate-900 dark:text-white flex items-center space-x-2">
              <Send className="w-4 h-4 text-brand-500" />
              <span>Request Configuration</span>
            </h3>

            {/* API Key selector */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300">
                  Client API Key
                </label>
                <button
                  type="button"
                  onClick={() => setUseCustomKey(!useCustomKey)}
                  className="text-xs text-brand-500 hover:underline"
                >
                  {useCustomKey ? 'Select from active keys' : 'Enter custom key'}
                </button>
              </div>

              {useCustomKey ? (
                <div>
                  <input
                    type="text"
                    value={customKey}
                    onChange={(e) => setCustomKey(e.target.value)}
                    placeholder="arl_live_..."
                    className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white font-mono text-xs focus:ring-2 focus:ring-brand-500 focus:outline-none"
                  />
                  <p className="mt-1 text-[11px] text-slate-400">
                    Paste the full raw API key returned when the key was created.
                  </p>
                </div>
              ) : (
                <div className="space-y-1.5">
                  <select
                    value={selectedKeyId}
                    onChange={(e) => handleKeySelectChange(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-xs font-mono focus:ring-2 focus:ring-brand-500 focus:outline-none"
                  >
                    <option value="demo">Demo Key ({DEMO_KEY.substring(0, 16)}...)</option>
                    {keys.map((k) => {
                      const hasSecret = !!getKnownRawKey(k.id, k.keyPrefix);
                      return (
                        <option key={k.id} value={k.id}>
                          {k.name} ({k.keyPrefix}) — {k.policyAlgorithm} {hasSecret ? '✓' : '(paste secret)'}
                        </option>
                      );
                    })}
                  </select>
                  {keyNotice && (
                    <p className={`text-[11px] ${
                      keyNotice.type === 'warning' ? 'text-amber-500' : 'text-emerald-500'
                    }`}>
                      {keyNotice.text}
                    </p>
                  )}
                </div>
              )}
            </div>

            {/* Method & Endpoint */}
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
                Target Route & Method
              </label>
              <div className="flex space-x-2">
                <select
                  value={method}
                  onChange={(e) => setMethod(e.target.value)}
                  className="w-24 px-3 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-xs font-bold focus:ring-2 focus:ring-brand-500 focus:outline-none"
                >
                  <option value="GET">GET</option>
                  <option value="POST">POST</option>
                </select>

                <select
                  value={endpoint}
                  onChange={(e) => setEndpoint(e.target.value)}
                  className="flex-1 px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-xs font-mono focus:ring-2 focus:ring-brand-500 focus:outline-none"
                >
                  <option value="/protected-api/data">/protected-api/data (Analytics feed)</option>
                  <option value="/protected-api/users">/protected-api/users (Directory)</option>
                  <option value="/protected-api/orders">/protected-api/orders (Orders list)</option>
                  <option value="/protected-api/echo">/protected-api/echo (Echo payload)</option>
                </select>
              </div>
            </div>

            {/* POST Payload Body if method === POST */}
            {method === 'POST' && (
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
                  Request Body (JSON)
                </label>
                <textarea
                  rows="3"
                  value={postPayload}
                  onChange={(e) => setPostPayload(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white font-mono text-xs focus:ring-2 focus:ring-brand-500 focus:outline-none"
                />
              </div>
            )}

            {/* Burst Mode Switcher */}
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
                Traffic Stress Burst
              </label>
              <div className="grid grid-cols-4 gap-2">
                {[1, 5, 10, 20].map((count) => (
                  <button
                    key={count}
                    type="button"
                    onClick={() => setBurstCount(count)}
                    className={`py-2 rounded-xl text-xs font-semibold border transition-all ${
                      burstCount === count
                        ? 'bg-brand-600 text-white border-brand-600 shadow-md shadow-brand-500/20'
                        : 'border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                    }`}
                  >
                    {count === 1 ? 'Single (1)' : `${count} Burst`}
                  </button>
                ))}
              </div>
              <p className="mt-1 text-[11px] text-slate-400">
                {burstCount > 1
                  ? `Simulates ${burstCount} concurrent client requests to stress test distributed Redis rate limiting.`
                  : 'Sends an individual isolated request.'}
              </p>
            </div>

            {/* Send Button */}
            <button
              type="button"
              onClick={handleSend}
              disabled={executing}
              className="w-full py-3 px-4 rounded-xl font-bold text-white bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 shadow-lg shadow-brand-500/25 transition-all flex items-center justify-center space-x-2 text-sm disabled:opacity-50"
            >
              {executing ? (
                <>
                  <Zap className="w-4 h-4 animate-spin" />
                  <span>Transmitting...</span>
                </>
              ) : (
                <>
                  <Play className="w-4 h-4 fill-white" />
                  <span>Send Request{burstCount > 1 ? ` (${burstCount}x)` : ''}</span>
                </>
              )}
            </button>
          </div>

          {/* Burst Summary Pill List */}
          {burstResults.length > 0 && (
            <div className="glass-card p-4 space-y-2">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Burst Sequence Telemetry ({burstResults.length} requests)
              </h4>
              <div className="flex flex-wrap gap-1.5 max-h-36 overflow-y-auto p-1">
                {burstResults.map((r, i) => {
                  const isSuccess = r.status >= 200 && r.status < 300;
                  const is429 = r.status === 429;
                  const isAuthError = r.status === 401 || r.status === 403;
                  return (
                    <span
                      key={i}
                      className={`px-2 py-1 rounded text-[11px] font-mono font-bold border ${
                        is429
                          ? 'bg-rose-500/10 text-rose-500 border-rose-500/30'
                          : isAuthError
                          ? 'bg-amber-500/10 text-amber-500 border-amber-500/30'
                          : isSuccess
                          ? 'bg-emerald-500/10 text-emerald-500 border-emerald-500/30'
                          : 'bg-red-500/10 text-red-500 border-red-500/30'
                      }`}
                    >
                      #{i + 1}: {r.status} {isSuccess ? 'OK' : is429 ? '429' : 'ERR'}
                    </span>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* Right Column: Gateway Response Inspector */}
        <div className="lg:col-span-7 space-y-5">
          {/* Rate-Limit / Auth Decision Explanation Card */}
          {result && (
            <div className={`p-4 rounded-2xl border transition-all ${
              result.status === 429
                ? 'bg-rose-500/10 border-rose-500/30 text-rose-900 dark:text-rose-200'
                : (result.status === 401 || result.status === 403)
                ? 'bg-amber-500/10 border-amber-500/30 text-amber-900 dark:text-amber-200'
                : (result.status >= 200 && result.status < 300)
                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-900 dark:text-emerald-200'
                : 'bg-red-500/10 border-red-500/30 text-red-900 dark:text-red-200'
            }`}>
              <div className="flex items-start space-x-3">
                {result.status === 429 ? (
                  <AlertOctagon className="w-5 h-5 text-rose-500 shrink-0 mt-0.5" />
                ) : (result.status === 401 || result.status === 403) ? (
                  <KeyRound className="w-5 h-5 text-amber-500 shrink-0 mt-0.5" />
                ) : (result.status >= 200 && result.status < 300) ? (
                  <CheckCircle2 className="w-5 h-5 text-emerald-500 shrink-0 mt-0.5" />
                ) : (
                  <AlertTriangle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
                )}
                <div className="flex-1">
                  <div className="flex items-center justify-between">
                    <h4 className="text-sm font-bold">
                      {result.status === 429
                        ? 'HTTP 429: Rate Limit Exceeded'
                        : result.status === 401
                        ? 'HTTP 401: Unauthorized (Invalid API Key)'
                        : result.status === 403
                        ? 'HTTP 403: Forbidden (API Key Revoked or Inactive)'
                        : (result.status >= 200 && result.status < 300)
                        ? `HTTP ${result.status}: Request Permitted`
                        : `HTTP ${result.status}: Server Error`}
                    </h4>
                    {result.rateLimit?.algorithm && (
                      <AlgorithmBadge algorithm={result.rateLimit.algorithm} />
                    )}
                  </div>
                  <p className="text-xs mt-1 leading-relaxed opacity-90">
                    {result.status === 429
                      ? result.data?.message || 'Your request exceeded the policy threshold. The gateway rejected the request before reaching downstream microservices.'
                      : (result.status === 401 || result.status === 403)
                      ? result.data?.message || 'The gateway rejected this request because the API key could not be verified or is revoked.'
                      : (result.status >= 200 && result.status < 300)
                      ? 'The gateway verified your API key and policy quota, allowing the request through.'
                      : result.data?.message || 'An error occurred while contacting the gateway service.'}
                  </p>

                  {/* Countdown Timer if 429 */}
                  {result.status === 429 && countdown !== null && (
                    <div className="mt-3 inline-flex items-center space-x-2 px-3 py-1.5 rounded-lg bg-rose-500/20 text-rose-700 dark:text-rose-300 font-mono text-xs font-semibold">
                      <Timer className="w-4 h-4 animate-pulse" />
                      <span>Retry permitted in: {countdown}s</span>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Response Inspector Card */}
          <div className="glass-card overflow-hidden">
            <div className="p-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between bg-slate-50/50 dark:bg-slate-900/50">
              <div className="flex items-center space-x-3">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Response Inspector
                </span>
                {result && (
                  <span className={`px-2 py-0.5 rounded text-xs font-bold font-mono ${
                    result.status >= 200 && result.status < 300
                      ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400'
                      : result.status === 429
                      ? 'bg-rose-500/15 text-rose-600 dark:text-rose-400'
                      : 'bg-amber-500/15 text-amber-600 dark:text-amber-400'
                  }`}>
                    {result.status} {result.statusText}
                  </span>
                )}
              </div>
              {result && (
                <span className="text-xs font-mono text-slate-400">
                  Latency: {result.latencyMs} ms
                </span>
              )}
            </div>

            {/* Rate Limit Response Headers Card */}
            {result && result.rateLimit && Object.keys(result.rateLimit).length > 0 && (
              <div className="p-4 bg-slate-100/50 dark:bg-slate-950/50 border-b border-slate-200 dark:border-slate-800 grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs font-mono">
                <div>
                  <span className="text-slate-400 block text-[10px]">X-RateLimit-Limit</span>
                  <span className="font-bold text-slate-800 dark:text-slate-100">
                    {result.rateLimit.limit || 'N/A'}
                  </span>
                </div>
                <div>
                  <span className="text-slate-400 block text-[10px]">X-RateLimit-Remaining</span>
                  <span className={`font-bold ${
                    result.rateLimit.remaining === '0' || result.status === 429 ? 'text-rose-500' : 'text-emerald-500'
                  }`}>
                    {result.rateLimit.remaining || '0'}
                  </span>
                </div>
                <div>
                  <span className="text-slate-400 block text-[10px]">X-RateLimit-Reset</span>
                  <span className="font-bold text-slate-800 dark:text-slate-100">
                    {result.rateLimit.reset ? `${result.rateLimit.reset}s` : 'N/A'}
                  </span>
                </div>
                <div>
                  <span className="text-slate-400 block text-[10px]">Retry-After</span>
                  <span className="font-bold text-amber-500">
                    {result.rateLimit.retryAfter ? `${result.rateLimit.retryAfter}s` : '0s'}
                  </span>
                </div>
              </div>
            )}

            {/* Formatted JSON Response Body */}
            <div className="p-5">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400 block mb-2">
                Response Payload Body
              </span>
              <pre className="p-4 rounded-xl bg-slate-950 text-slate-100 font-mono text-xs overflow-x-auto max-h-80 border border-slate-800 leading-relaxed">
                {result
                  ? JSON.stringify(result.data, null, 2)
                  : '// Click "Send Request" to observe real-time Gateway responses and rate limit headers.'}
              </pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
