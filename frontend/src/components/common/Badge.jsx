import React from 'react';

export const AlgorithmBadge = ({ algorithm }) => {
  const algo = (algorithm || '').toUpperCase();
  
  const styles = {
    TOKEN_BUCKET: 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/30',
    SLIDING_WINDOW: 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/30',
    FIXED_WINDOW: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/30',
    LEAKY_BUCKET: 'bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/30',
  };

  const labels = {
    TOKEN_BUCKET: 'Token Bucket',
    SLIDING_WINDOW: 'Sliding Window',
    FIXED_WINDOW: 'Fixed Window',
    LEAKY_BUCKET: 'Leaky Bucket',
  };

  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${styles[algo] || 'bg-slate-500/10 text-slate-400 border-slate-500/30'}`}>
      {labels[algo] || algorithm}
    </span>
  );
};

export const StatusBadge = ({ status, active = null }) => {
  if (active !== null) {
    return active ? (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/30">
        Active
      </span>
    ) : (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border bg-slate-500/10 text-slate-400 border-slate-500/30">
        Inactive
      </span>
    );
  }

  const s = (status || '').toUpperCase();
  if (s === 'ACTIVE') {
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/30">
        Active
      </span>
    );
  }
  if (s === 'REVOKED') {
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/30">
        Revoked
      </span>
    );
  }
  return (
    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/30">
      Expired
    </span>
  );
};
