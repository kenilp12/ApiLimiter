import React from 'react';
import { Link } from 'react-router-dom';
import { ShieldAlert, ArrowLeft } from 'lucide-react';

export const NotFoundPage = () => {
  return (
    <div className="min-h-[70vh] flex flex-col items-center justify-center text-center p-6">
      <div className="p-4 rounded-3xl bg-brand-500/10 text-brand-500 border border-brand-500/20 mb-4">
        <ShieldAlert className="w-12 h-12" />
      </div>
      <h2 className="text-3xl font-extrabold text-slate-900 dark:text-white">
        404 — Page Not Found
      </h2>
      <p className="mt-2 text-sm text-slate-500 max-w-sm">
        The route you are looking for does not exist on this platform.
      </p>
      <Link
        to="/"
        className="mt-6 inline-flex items-center space-x-2 px-5 py-2.5 rounded-xl font-semibold text-white bg-brand-600 hover:bg-brand-500 transition-colors shadow-md shadow-brand-500/25 text-sm"
      >
        <ArrowLeft className="w-4 h-4" />
        <span>Return to Dashboard</span>
      </Link>
    </div>
  );
};
