import React, { useEffect, useState } from 'react';
import { policyService } from '../services/policyService';
import { AlgorithmBadge, StatusBadge } from '../components/common/Badge';
import { Modal } from '../components/common/Modal';
import { ConfirmDialog } from '../components/common/ConfirmDialog';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import {
  SlidersHorizontal,
  Plus,
  Edit2,
  Trash2,
  Power,
  Info,
  CheckCircle2,
  Sliders
} from 'lucide-react';

export const PoliciesPage = () => {
  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState(null);

  // Form inputs
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [algorithm, setAlgorithm] = useState('TOKEN_BUCKET');
  const [limitValue, setLimitValue] = useState(60);
  const [timeWindowSeconds, setTimeWindowSeconds] = useState(60);
  const [burstCapacity, setBurstCapacity] = useState(100);
  const [refillRate, setRefillRate] = useState(5.0);
  const [saving, setSaving] = useState(false);

  // Delete confirm
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const { isAdmin } = useAuth();
  const { toast } = useToast();

  const ALGORITHM_DESCRIPTIONS = {
    FIXED_WINDOW: 'Divides time into fixed intervals (e.g., 60s). Counters increment with each request and reset on boundary transitions. Low memory overhead, simple semantics.',
    SLIDING_WINDOW: 'Uses millisecond-level Redis Sorted Sets (ZSET) to record exact timestamps. Removes log entries older than the window, preventing burst boundary spikes.',
    TOKEN_BUCKET: 'Tokens accumulate in a bucket at a constant refill rate up to maximum burst capacity. Each request consumes tokens. Ideal for handling bursts with steady baseline flow.',
    LEAKY_BUCKET: 'Requests enter a virtual queue and leak at a constant processing rate. Overflows are immediately rejected. Ideal for strictly smoothing erratic traffic bursts.',
  };

  const loadPolicies = async () => {
    try {
      const data = await policyService.getPolicies();
      setPolicies(data);
    } catch (err) {
      toast.error('Failed to load rate limit policies');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPolicies();
  }, []);

  const openCreateModal = () => {
    setEditingPolicy(null);
    setName('');
    setDescription('');
    setAlgorithm('TOKEN_BUCKET');
    setLimitValue(60);
    setTimeWindowSeconds(60);
    setBurstCapacity(100);
    setRefillRate(5.0);
    setModalOpen(true);
  };

  const openEditModal = (p) => {
    setEditingPolicy(p);
    setName(p.name);
    setDescription(p.description || '');
    setAlgorithm(p.algorithm);
    setLimitValue(p.limitValue);
    setTimeWindowSeconds(p.timeWindowSeconds);
    setBurstCapacity(p.burstCapacity || p.limitValue);
    setRefillRate(p.refillRate || 1.0);
    setModalOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name.trim()) {
      toast.warning('Please enter a policy name');
      return;
    }

    const payload = {
      name: name.trim(),
      description: description.trim(),
      algorithm,
      limitValue: Number(limitValue),
      timeWindowSeconds: Number(timeWindowSeconds),
      burstCapacity: Number(burstCapacity),
      refillRate: Number(refillRate),
      isActive: editingPolicy ? editingPolicy.isActive : true,
    };

    try {
      setSaving(true);
      if (editingPolicy) {
        await policyService.updatePolicy(editingPolicy.id, payload);
        toast.success(`Policy "${name}" updated successfully.`);
      } else {
        await policyService.createPolicy(payload);
        toast.success(`Policy "${name}" created successfully.`);
      }
      setModalOpen(false);
      loadPolicies();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save policy');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleStatus = async (policy) => {
    try {
      const newStatus = !policy.isActive;
      await policyService.toggleStatus(policy.id, newStatus);
      toast.success(`Policy "${policy.name}" is now ${newStatus ? 'active' : 'inactive'}.`);
      loadPolicies();
    } catch (err) {
      toast.error('Failed to update policy status');
    }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    try {
      setDeleting(true);
      await policyService.deletePolicy(deleteTarget.id);
      toast.success(`Policy "${deleteTarget.name}" deleted.`);
      setDeleteTarget(null);
      loadPolicies();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to delete policy');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
            Rate Limit Policies
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Configure algorithm parameters, request quotas, and burst thresholds enforced across distributed instances
          </p>
        </div>
        {isAdmin && (
          <button
            onClick={openCreateModal}
            className="inline-flex items-center space-x-2 px-4 py-2.5 rounded-xl font-semibold text-sm text-white bg-brand-600 hover:bg-brand-500 shadow-md shadow-brand-500/25 transition-all self-start sm:self-auto"
          >
            <Plus className="w-4 h-4" />
            <span>Create Policy</span>
          </button>
        )}
      </div>

      {/* Policy Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {policies.map((p) => (
          <div key={p.id} className="glass-card p-6 flex flex-col justify-between glass-card-hover">
            <div>
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="text-lg font-bold text-slate-900 dark:text-white">
                    {p.name}
                  </h3>
                  <div className="mt-1 flex items-center space-x-2">
                    <AlgorithmBadge algorithm={p.algorithm} />
                    <StatusBadge active={p.isActive} />
                  </div>
                </div>
                {isAdmin && (
                  <div className="flex items-center space-x-1.5">
                    <button
                      onClick={() => handleToggleStatus(p)}
                      title={p.isActive ? 'Deactivate' : 'Activate'}
                      className={`p-1.5 rounded-lg border transition-colors ${
                        p.isActive 
                          ? 'border-emerald-500/30 text-emerald-500 hover:bg-emerald-500/10' 
                          : 'border-slate-500/30 text-slate-400 hover:bg-slate-500/10'
                      }`}
                    >
                      <Power className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => openEditModal(p)}
                      title="Edit"
                      className="p-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-slate-500 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => setDeleteTarget(p)}
                      title="Delete"
                      className="p-1.5 rounded-lg border border-rose-500/20 text-rose-500 hover:bg-rose-500/10 transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                )}
              </div>

              <p className="mt-3 text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
                {p.description || ALGORITHM_DESCRIPTIONS[p.algorithm]}
              </p>
            </div>

            <div className="mt-6 pt-4 border-t border-slate-200 dark:border-slate-800 grid grid-cols-2 gap-4 text-xs font-mono">
              <div>
                <span className="text-slate-400 uppercase tracking-wider block text-[10px]">Limit Quota</span>
                <span className="font-bold text-slate-900 dark:text-white text-sm">
                  {p.limitValue} req / {p.timeWindowSeconds}s
                </span>
              </div>
              <div>
                <span className="text-slate-400 uppercase tracking-wider block text-[10px]">Burst / Refill</span>
                <span className="font-bold text-slate-900 dark:text-white text-sm">
                  {p.burstCapacity || p.limitValue} cap ({p.refillRate || 1}/s)
                </span>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Modal: Create or Edit Policy */}
      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingPolicy ? 'Edit Rate Limit Policy' : 'Create Rate Limit Policy'}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
              Policy Name
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Standard Tier, High Throughput API"
              required
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
              Algorithm Type
            </label>
            <select
              value={algorithm}
              onChange={(e) => setAlgorithm(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none font-medium"
            >
              <option value="TOKEN_BUCKET">Token Bucket (Bursty traffic with steady token refill)</option>
              <option value="SLIDING_WINDOW">Sliding Window (Continuous microsecond Redis log)</option>
              <option value="FIXED_WINDOW">Fixed Window (Counter reset on interval boundary)</option>
              <option value="LEAKY_BUCKET">Leaky Bucket (Queue buffer with constant outflow rate)</option>
            </select>
          </div>

          {/* Dynamic Algorithm Explanation Card */}
          <div className="p-3.5 rounded-xl bg-brand-500/5 border border-brand-500/20 text-xs text-slate-600 dark:text-slate-300 flex items-start space-x-2.5">
            <Info className="w-4 h-4 text-brand-500 shrink-0 mt-0.5" />
            <p className="leading-relaxed">{ALGORITHM_DESCRIPTIONS[algorithm]}</p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
                Limit (Requests)
              </label>
              <input
                type="number"
                min="1"
                value={limitValue}
                onChange={(e) => setLimitValue(e.target.value)}
                required
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
                Window Duration (Seconds)
              </label>
              <input
                type="number"
                min="1"
                value={timeWindowSeconds}
                onChange={(e) => setTimeWindowSeconds(e.target.value)}
                required
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
                Burst / Buffer Capacity
              </label>
              <input
                type="number"
                min="1"
                value={burstCapacity}
                onChange={(e) => setBurstCapacity(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
                Refill / Leak Rate (per sec)
              </label>
              <input
                type="number"
                step="0.1"
                min="0.1"
                value={refillRate}
                onChange={(e) => setRefillRate(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
              Description (Optional)
            </label>
            <textarea
              rows="2"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Provide context or notes about this policy..."
              className="w-full px-3.5 py-2 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none"
            />
          </div>

          <div className="pt-2 flex justify-end space-x-3">
            <button
              type="button"
              onClick={() => setModalOpen(false)}
              className="px-4 py-2 text-sm font-medium rounded-xl border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving}
              className="px-4 py-2 text-sm font-semibold rounded-xl text-white bg-brand-600 hover:bg-brand-500 shadow-md shadow-brand-500/25 transition-all disabled:opacity-50"
            >
              {saving ? 'Saving...' : editingPolicy ? 'Update Policy' : 'Create Policy'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={confirmDelete}
        loading={deleting}
        title="Delete Rate Limit Policy"
        message={`Are you sure you want to delete policy "${deleteTarget?.name}"? API keys assigned to this policy will no longer be authorized.`}
        confirmText="Delete Policy"
        isDestructive={true}
      />
    </div>
  );
};
