import React, { createContext, useContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertTriangle, XCircle, Info, X } from 'lucide-react';

const ToastContext = createContext();

export const ToastProvider = ({ children }) => {
  const [toasts, setToasts] = useState([]);

  const removeToast = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const addToast = useCallback(({ type = 'info', message, duration = 4000 }) => {
    const id = Date.now() + Math.random().toString(36).substring(2, 7);
    setToasts((prev) => [...prev, { id, type, message }]);

    if (duration > 0) {
      setTimeout(() => {
        removeToast(id);
      }, duration);
    }
  }, [removeToast]);

  const toast = {
    success: (msg) => addToast({ type: 'success', message: msg }),
    error: (msg) => addToast({ type: 'error', message: msg }),
    warning: (msg) => addToast({ type: 'warning', message: msg }),
    info: (msg) => addToast({ type: 'info', message: msg }),
  };

  return (
    <ToastContext.Provider value={{ toast, addToast, removeToast }}>
      {children}
      {/* Toast Notification Container */}
      <div className="fixed bottom-4 right-4 z-50 flex flex-col space-y-2 max-w-md w-full px-4 pointer-events-none">
        {toasts.map((t) => {
          const icons = {
            success: <CheckCircle2 className="w-5 h-5 text-emerald-500 shrink-0" />,
            warning: <AlertTriangle className="w-5 h-5 text-amber-500 shrink-0" />,
            error: <XCircle className="w-5 h-5 text-rose-500 shrink-0" />,
            info: <Info className="w-5 h-5 text-brand-500 shrink-0" />
          };

          const borderColors = {
            success: 'border-emerald-500/30 bg-emerald-50/90 dark:bg-emerald-950/90',
            warning: 'border-amber-500/30 bg-amber-50/90 dark:bg-amber-950/90',
            error: 'border-rose-500/30 bg-rose-50/90 dark:bg-rose-950/90',
            info: 'border-brand-500/30 bg-brand-50/90 dark:bg-brand-950/90',
          };

          return (
            <div
              key={t.id}
              className={`pointer-events-auto flex items-center justify-between p-3.5 rounded-xl border backdrop-blur-md shadow-lg transition-all transform duration-200 animate-slide-in ${borderColors[t.type] || borderColors.info}`}
            >
              <div className="flex items-center space-x-3 text-sm font-medium text-slate-800 dark:text-slate-100">
                {icons[t.type] || icons.info}
                <span>{t.message}</span>
              </div>
              <button
                onClick={() => removeToast(t.id)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 ml-3 transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
};

export const useToast = () => useContext(ToastContext);
