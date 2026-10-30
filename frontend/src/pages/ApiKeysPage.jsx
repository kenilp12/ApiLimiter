import React, { useEffect, useState } from 'react';
import { apiKeyService } from '../services/apiKeyService';
import { policyService } from '../services/policyService';
import { StatusBadge, AlgorithmBadge } from '../components/common/Badge';
import { Modal } from '../components/common/Modal';
import { ConfirmDialog } from '../components/common/ConfirmDialog';
import { useToast } from '../context/ToastContext';
import {
  KeyRound,
  Plus,
  Copy,
  Check,
  Trash2,
  AlertTriangle,
  Calendar,
  Layers,
  RefreshCw
} from 'lucide-react';

export const ApiKeysPage = () => {
  const [keys, setKeys] = useState([]);
  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [newKeyData, setNewKeyData] = useState(null);
  const [copied, setCopied] = useState(false);

  // Form state
  const [keyName, setKeyName] = useState('');
  const [selectedPolicyId, setSelectedPolicyId] = useState('');
  const [creating, setCreating] = useState(false);

  // Revoke confirm state
  const [revokeTarget, setRevokeTarget] = useState(null);
  const [revoking, setRevoking] = useState(false);

  const { toast } = useToast();

  const loadData = async () => {
    try {
      const [keysList, policiesList] = await Promise.all([
        apiKeyService.getKeys(),
        policyService.getPolicies(),
      ]);
      const activePolicies = policiesList.filter((p) => p.isActive);
      setKeys(keysList);
      setPolicies(activePolicies);
      if (activePolicies.length > 0 && (!selectedPolicyId || !activePolicies.some(p => p.id.toString() === selectedPolicyId))) {
        setSelectedPolicyId(activePolicies[0].id.toString());
      }
    } catch (err) {
      toast.error('Failed to load API keys or policies');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenCreateModal = () => {
    if (policies.length > 0 && (!selectedPolicyId || !policies.some(p => p.id.toString() === selectedPolicyId))) {
      setSelectedPolicyId(policies[0].id.toString());
    }
    setCreateModalOpen(true);
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleCreateKey = async (e) => {
    e.preventDefault();
    if (!keyName.trim()) {
      toast.warning('Please enter a name for your API key.');
      return;
    }
    if (!selectedPolicyId) {
      toast.warning('Please select a rate limit policy.');
      return;
    }

    try {
      setCreating(true);
      const res = await apiKeyService.createKey(keyName, Number(selectedPolicyId), null);
      setNewKeyData(res);
      setCreateModalOpen(false);
      setKeyName('');
      loadData();
      toast.success('API key generated successfully!');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to generate API key');
    } finally {
      setCreating(false);
    }
  };

  const handleCopyKey = (keyString) => {
    navigator.clipboard.writeText(keyString);
    setCopied(true);
    toast.info('API key copied to clipboard.');
    setTimeout(() => setCopied(false), 2500);
  };

  const confirmRevoke = async () => {
    if (!revokeTarget) return;
    try {
      setRevoking(true);
      await apiKeyService.revokeKey(revokeTarget.id);
      toast.success(`API key "${revokeTarget.name}" has been revoked.`);
      setRevokeTarget(null);
      loadData();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to revoke API key');
    } finally {
      setRevoking(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
            API Keys Management
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Create, authenticate, and monitor rate-limited API access tokens
          </p>
        </div>
        <button
          onClick={handleOpenCreateModal}
          className="inline-flex items-center space-x-2 px-4 py-2.5 rounded-xl font-semibold text-sm text-white bg-brand-600 hover:bg-brand-500 shadow-md shadow-brand-500/25 transition-all self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          <span>Generate New Key</span>
        </button>
      </div>

      {/* Keys Table */}
      <div className="glass-card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50/50 dark:bg-slate-900/50 text-xs font-semibold uppercase text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-800">
              <tr>
                <th className="px-5 py-3">Key Name</th>
                <th className="px-5 py-3">Key Prefix</th>
                <th className="px-5 py-3">Assigned Policy</th>
                <th className="px-5 py-3">Algorithm</th>
                <th className="px-5 py-3">Status</th>
                <th className="px-5 py-3">Created</th>
                <th className="px-5 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
              {loading ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-400">
                    <RefreshCw className="w-5 h-5 text-brand-500 animate-spin mx-auto mb-2" />
                    <span>Loading API keys...</span>
                  </td>
                </tr>
              ) : keys.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-10 text-center text-slate-400">
                    No API keys created yet. Click "Generate New Key" to create your first key!
                  </td>
                </tr>
              ) : (
                keys.map((k) => (
                  <tr key={k.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors">
                    <td className="px-5 py-3.5 font-medium text-slate-900 dark:text-white">
                      {k.name}
                    </td>
                    <td className="px-5 py-3.5 font-mono text-xs text-slate-500 dark:text-slate-400">
                      {k.keyPrefix}
                    </td>
                    <td className="px-5 py-3.5 text-xs text-slate-700 dark:text-slate-300">
                      {k.policyName}
                    </td>
                    <td className="px-5 py-3.5">
                      <AlgorithmBadge algorithm={k.policyAlgorithm} />
                    </td>
                    <td className="px-5 py-3.5">
                      <StatusBadge status={k.status} />
                    </td>
                    <td className="px-5 py-3.5 text-xs text-slate-500">
                      {new Date(k.createdAt).toLocaleDateString()}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      {k.status === 'ACTIVE' && (
                        <button
                          onClick={() => setRevokeTarget(k)}
                          className="px-2.5 py-1 rounded-lg text-xs font-medium text-rose-500 hover:bg-rose-500/10 transition-colors"
                        >
                          Revoke
                        </button>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal: Create API Key Form */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Generate New API Key"
      >
        <form onSubmit={handleCreateKey} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
              Key Name / Description
            </label>
            <input
              type="text"
              value={keyName}
              onChange={(e) => setKeyName(e.target.value)}
              placeholder="e.g. Mobile App Production, Backend Microservice"
              required
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300 mb-1.5">
              Attach Rate Limit Policy
            </label>
            <select
              value={selectedPolicyId}
              onChange={(e) => setSelectedPolicyId(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none"
            >
              {policies.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.algorithm} - {p.limitValue} req/{p.timeWindowSeconds}s)
                </option>
              ))}
            </select>
          </div>

          <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-xs text-amber-600 dark:text-amber-400 flex items-start space-x-2">
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>
              For maximum security, your complete API key is hashed with SHA-256 before storage. The raw key is only shown once upon creation.
            </span>
          </div>

          <div className="pt-2 flex justify-end space-x-3">
            <button
              type="button"
              onClick={() => setCreateModalOpen(false)}
              className="px-4 py-2 text-sm font-medium rounded-xl border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={creating}
              className="px-4 py-2 text-sm font-semibold rounded-xl text-white bg-brand-600 hover:bg-brand-500 shadow-md shadow-brand-500/25 transition-all disabled:opacity-50"
            >
              {creating ? 'Generating...' : 'Create Key'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Modal: Show Newly Created Secret Key Once */}
      {newKeyData && (
        <Modal
          isOpen={!!newKeyData}
          onClose={() => setNewKeyData(null)}
          title="Save Your Secret API Key"
        >
          <div className="space-y-4">
            <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-xs text-emerald-600 dark:text-emerald-400">
              <strong className="block mb-1">Key successfully generated!</strong>
              {newKeyData.warning}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 mb-1">
                Raw API Secret Key:
              </label>
              <div className="flex items-center space-x-2">
                <input
                  type="text"
                  readOnly
                  value={newKeyData.rawApiKey}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-100 dark:bg-slate-950 font-mono text-xs text-slate-900 dark:text-emerald-400 select-all focus:outline-none"
                />
                <button
                  type="button"
                  onClick={() => handleCopyKey(newKeyData.rawApiKey)}
                  className="px-3.5 py-2.5 rounded-xl bg-brand-600 hover:bg-brand-500 text-white font-medium text-xs flex items-center space-x-1.5 transition-colors shrink-0"
                >
                  {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                  <span>{copied ? 'Copied' : 'Copy'}</span>
                </button>
              </div>
            </div>

            <div className="pt-2 flex justify-end">
              <button
                type="button"
                onClick={() => setNewKeyData(null)}
                className="px-5 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-sm font-medium transition-colors"
              >
                I have safely stored this key
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* Confirm Revocation Dialog */}
      <ConfirmDialog
        isOpen={!!revokeTarget}
        onClose={() => setRevokeTarget(null)}
        onConfirm={confirmRevoke}
        loading={revoking}
        title="Revoke API Key"
        message={`Are you sure you want to revoke API key "${revokeTarget?.name}"? Any applications using this key will immediately receive HTTP 403 Forbidden.`}
        confirmText="Revoke Key"
        isDestructive={true}
      />
    </div>
  );
};
